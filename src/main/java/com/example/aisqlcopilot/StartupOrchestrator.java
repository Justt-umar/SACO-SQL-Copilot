package com.example.aisqlcopilot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Automates the full startup sequence so the user only needs to run
 * {@code mvn clean javafx:run} — everything else is handled:
 *
 * <ol>
 *   <li><b>Docker Desktop</b> — opens it if not running, waits for daemon</li>
 *   <li><b>n8n Container</b> — starts the container (or creates one if missing)</li>
 *   <li><b>n8n Readiness</b> — polls the webhook endpoint until responsive</li>
 *   <li><b>MySQL Workbench</b> — launches it if not already open</li>
 *   <li><b>Window Arrangement</b> — maximizes MySQL WB, positions SACO bottom-right</li>
 * </ol>
 *
 * All steps are idempotent — if a service is already running, it's skipped.
 * Progress is reported back to the UI via the {@code statusCallback}.
 */
public class StartupOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(StartupOrchestrator.class);

    private final SacoConfig config;
    private final Consumer<String> statusCallback;

    public StartupOrchestrator(SacoConfig config, Consumer<String> statusCallback) {
        this.config = config;
        this.statusCallback = statusCallback;
    }

    /**
     * Runs the complete startup sequence. Call this from a background thread.
     *
     * @return true if all services are ready
     */
    public boolean orchestrate() {
        try {
            ensureDockerRunning();
            ensureN8nContainer();
            waitForN8nReady();
            ensureMySQLWorkbench();
            maximizeMySQLWorkbench();
            status("✅ All services ready! Environment is set up.");
            return true;
        } catch (Exception e) {
            log.error("Startup orchestration failed: {}", e.getMessage(), e);
            status("❌ Startup error: " + e.getMessage());
            return false;
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  STEP 1: Docker Desktop
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void ensureDockerRunning() throws Exception {
        status("🐳 Checking Docker Desktop…");

        if (isDockerDaemonReady()) {
            status("🐳 Docker Desktop is already running.");
            return;
        }

        status("🐳 Starting Docker Desktop…");

        if (OsAutomation.isMac()) {
            exec("open", "-a", "Docker");
        } else if (OsAutomation.isWindows()) {
            exec("cmd", "/c", "start", "", "Docker Desktop");
        } else {
            exec("systemctl", "--user", "start", "docker-desktop");
        }

        // Poll for daemon readiness (Docker Desktop can take 30-90s on cold start)
        int maxWaitSec = 90;
        for (int elapsed = 0; elapsed < maxWaitSec; elapsed += 3) {
            Thread.sleep(3000);
            if (isDockerDaemonReady()) {
                status("🐳 Docker Desktop is ready.");
                return;
            }
            status("🐳 Waiting for Docker daemon… (" + (elapsed + 3) + "s)");
        }

        throw new RuntimeException("Docker Desktop did not start within " + maxWaitSec + "s. Please start it manually.");
    }

    private boolean isDockerDaemonReady() {
        try {
            Process p = new ProcessBuilder("docker", "info")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = p.waitFor(10, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  STEP 2: n8n Container
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void ensureN8nContainer() throws Exception {
        String containerName = config.getN8nContainerName();
        status("📦 Checking n8n container (" + containerName + ")…");

        String containerStatus = getContainerStatus(containerName);

        if (containerStatus.startsWith("Up")) {
            status("📦 n8n container is already running.");
            return;
        }

        if (!containerStatus.isEmpty()) {
            // Container exists but is stopped
            status("📦 Starting stopped n8n container…");
            exec("docker", "start", containerName);
        } else {
            // Container doesn't exist — create and run it
            status("📦 Creating new n8n container…");
            exec("docker", "run", "-d",
                    "--name", containerName,
                    "-p", "5678:5678",
                    "-v", "n8n_data:/home/node/.n8n",
                    "docker.n8n.io/n8nio/n8n");
        }

        // Verify it started
        Thread.sleep(2000);
        String newStatus = getContainerStatus(containerName);
        if (newStatus.startsWith("Up")) {
            status("📦 n8n container is running.");
        } else {
            throw new RuntimeException("n8n container failed to start. Status: " + newStatus);
        }
    }

    /**
     * Returns the Docker container status string (e.g., "Up 2 hours", "Exited (0) 3 days ago"),
     * or empty string if the container doesn't exist.
     */
    private String getContainerStatus(String containerName) {
        try {
            Process p = new ProcessBuilder("docker", "ps", "-a",
                    "--filter", "name=^" + containerName + "$",
                    "--format", "{{.Status}}")
                    .redirectErrorStream(true)
                    .start();

            String output = readOutput(p).trim();
            p.waitFor(5, TimeUnit.SECONDS);
            return output;
        } catch (Exception e) {
            log.warn("Failed to check container status: {}", e.getMessage());
            return "";
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  STEP 3: Wait for n8n webhook to be responsive
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void waitForN8nReady() throws Exception {
        status("🔗 Waiting for n8n webhook to become ready…");

        int maxWaitSec = 45;
        for (int elapsed = 0; elapsed < maxWaitSec; elapsed += 3) {
            try {
                // Try a lightweight probe of n8n's health
                Process p = new ProcessBuilder("curl", "-s", "-o", "/dev/null",
                        "-w", "%{http_code}", "--connect-timeout", "2",
                        "http://localhost:5678/")
                        .redirectErrorStream(true)
                        .start();

                String httpCode = readOutput(p).trim();
                p.waitFor(5, TimeUnit.SECONDS);

                if (!httpCode.isEmpty() && !httpCode.equals("000")) {
                    status("🔗 n8n is responding (HTTP " + httpCode + ").");
                    return;
                }
            } catch (Exception e) {
                // Ignore and retry
            }
            Thread.sleep(3000);
            status("🔗 Waiting for n8n… (" + (elapsed + 3) + "s)");
        }

        // Don't throw — n8n might still be initializing but the container is running
        status("⚠️ n8n may still be starting up. Will retry on first message.");
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  STEP 4: MySQL Workbench
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void ensureMySQLWorkbench() throws Exception {
        status("🗄️ Checking MySQL Workbench…");

        if (isMacAppRunning("MySQLWorkbench")) {
            status("🗄️ MySQL Workbench is already open.");
            return;
        }

        status("🗄️ Launching MySQL Workbench…");

        if (OsAutomation.isMac()) {
            exec("open", "-a", "MySQLWorkbench");
        } else if (OsAutomation.isWindows()) {
            exec("cmd", "/c", "start", "", "MySQLWorkbench");
        } else {
            exec("mysql-workbench");
        }

        // Wait for it to appear
        int maxWaitSec = 15;
        for (int elapsed = 0; elapsed < maxWaitSec; elapsed += 2) {
            Thread.sleep(2000);
            if (isMacAppRunning("MySQLWorkbench")) {
                status("🗄️ MySQL Workbench is ready.");
                Thread.sleep(2000); // Extra time for window to fully render
                return;
            }
            status("🗄️ Waiting for MySQL Workbench… (" + (elapsed + 2) + "s)");
        }

        status("⚠️ MySQL Workbench may still be loading.");
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  STEP 5: Window Arrangement (macOS AppleScript)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void maximizeMySQLWorkbench() throws Exception {
        if (!OsAutomation.isMac()) {
            status("📐 Window arrangement is macOS-only for now.");
            return;
        }

        if (!isMacAppRunning("MySQLWorkbench")) {
            return;
        }

        status("📐 Arranging MySQL Workbench (fullscreen)…");

        try {
            // Get screen dimensions via AppleScript
            String script = """
                    tell application "Finder"
                        set screenBounds to bounds of window of desktop
                        set screenW to item 3 of screenBounds
                        set screenH to item 4 of screenBounds
                    end tell

                    tell application "System Events"
                        tell process "MySQLWorkbench"
                            set frontmost to true
                            delay 0.3
                            try
                                set position of window 1 to {0, 25}
                                set size of window 1 to {screenW, screenH - 25}
                            end try
                        end tell
                    end tell
                    """;

            ProcessBuilder pb = new ProcessBuilder("osascript", "-e", script);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = readOutput(p);
            p.waitFor(10, TimeUnit.SECONDS);

            log.debug("MySQL Workbench maximize result: {}", output);
            status("📐 MySQL Workbench maximized.");

        } catch (Exception e) {
            log.warn("Failed to maximize MySQL Workbench: {}", e.getMessage());
            status("⚠️ Could not resize MySQL Workbench (Accessibility permission may be needed).");
        }
    }

    /**
     * Positions the SACO window to the bottom-right quadrant of the screen.
     * This is called from the JavaFX thread via the main app.
     *
     * @param screenW  total screen width
     * @param screenH  total screen height
     * @return double array: {x, y, width, height}
     */
    public static double[] calculateBottomRightBounds(double screenW, double screenH) {
        double sacoW = screenW / 2.0;
        double sacoH = screenH / 2.0;
        double sacoX = screenW - sacoW;
        double sacoY = screenH - sacoH;
        return new double[]{sacoX, sacoY, sacoW, sacoH};
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  UTILITIES
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Checks if a macOS app is running using AppleScript.
     * Avoids the pgrep false-positive where the SACO Java process
     * matches "MySQLWorkbench" because the string is in our compiled code.
     */
    private boolean isMacAppRunning(String appName) {
        if (OsAutomation.isMac()) {
            try {
                String script = "tell application \"System Events\" to " +
                        "(name of processes) contains \"" + appName + "\"";
                Process p = new ProcessBuilder("osascript", "-e", script)
                        .redirectErrorStream(true).start();
                String output = readOutput(p).trim();
                p.waitFor(5, TimeUnit.SECONDS);
                log.debug("isMacAppRunning({}): {}", appName, output);
                return "true".equals(output);
            } catch (Exception e) {
                log.warn("App check failed for {}: {}", appName, e.getMessage());
                return false;
            }
        }
        // Fallback for Windows
        try {
            Process p = new ProcessBuilder("tasklist", "/FI",
                    "IMAGENAME eq " + appName + ".exe")
                    .redirectErrorStream(true).start();
            String output = readOutput(p);
            p.waitFor(5, TimeUnit.SECONDS);
            return output.contains(appName) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void exec(String... command) throws Exception {
        log.debug("exec: {}", String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = readOutput(p);
        boolean finished = p.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            log.warn("Command timed out: {}", String.join(" ", command));
        }
        if (!output.isBlank()) {
            log.debug("Output: {}", output);
        }
    }

    private String readOutput(Process p) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void status(String message) {
        log.info("[Orchestrator] {}", message);
        statusCallback.accept(message);
    }
}
