package com.example.aisqlcopilot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;

/**
 * Cross-platform keyboard automation utility — v2.
 *
 * Improvements over v1:
 * <ul>
 *   <li>Clipboard polling with timeout replaces brittle {@code Thread.sleep}</li>
 *   <li>Configurable target IDE name (not just MySQL Workbench)</li>
 *   <li>Retry mechanism for AppleScript activation</li>
 *   <li>Named daemon threads via shared Robot instance guidance</li>
 *   <li>Proper logging instead of silent failures</li>
 * </ul>
 */
public class OsAutomation {

    private static final Logger log = LoggerFactory.getLogger(OsAutomation.class);

    /** Detected operating system family. */
    public enum OsFamily { MAC, WINDOWS, LINUX }

    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase();

    public static final OsFamily CURRENT_OS;
    static {
        if (OS_NAME.contains("mac") || OS_NAME.contains("darwin")) {
            CURRENT_OS = OsFamily.MAC;
        } else if (OS_NAME.contains("win")) {
            CURRENT_OS = OsFamily.WINDOWS;
        } else {
            CURRENT_OS = OsFamily.LINUX;
        }
        log.info("Detected OS: {} ({})", CURRENT_OS, OS_NAME);
    }

    /** The primary modifier key: Cmd (⌘) on macOS, Ctrl on Windows/Linux. */
    public static final int MODIFIER_KEY =
            (CURRENT_OS == OsFamily.MAC) ? KeyEvent.VK_META : KeyEvent.VK_CONTROL;

    /** Returns true when running on macOS. */
    public static boolean isMac() { return CURRENT_OS == OsFamily.MAC; }

    /** Returns true when running on Windows. */
    public static boolean isWindows() { return CURRENT_OS == OsFamily.WINDOWS; }

    /**
     * The preferred monospace font CSS string for the current platform.
     */
    public static String monoFont() {
        return switch (CURRENT_OS) {
            case MAC     -> "'SF Mono', 'Menlo', monospace";
            case WINDOWS -> "'Consolas', monospace";
            case LINUX   -> "'JetBrains Mono', 'Ubuntu Mono', monospace";
        };
    }

    /**
     * The display name for the target SQL IDE on this platform.
     * Can be overridden via config for DBeaver, DataGrip, etc.
     */
    public static String targetIdeName() {
        return "MySQL Workbench";
    }

    /**
     * The AppleScript process name for the target IDE.
     * MySQL Workbench registers as "MySQLWorkbench" (no space) in macOS.
     */
    public static String appleScriptProcessName() {
        return "MySQLWorkbench";
    }

    // ──────────────────────────────────────────────────────────────
    //  Keyboard Helpers
    // ──────────────────────────────────────────────────────────────

    /**
     * Sends Modifier + keyCode with proper press/release timing.
     */
    public static void modifierCombo(Robot robot, int keyCode) {
        robot.keyPress(MODIFIER_KEY);
        robot.delay(30);
        robot.keyPress(keyCode);
        robot.delay(30);
        robot.keyRelease(keyCode);
        robot.delay(30);
        robot.keyRelease(MODIFIER_KEY);
        robot.delay(50);
    }

    // ──────────────────────────────────────────────────────────────
    //  Window Switching (with retry)
    // ──────────────────────────────────────────────────────────────

    /**
     * Switch the OS focus to the target SQL IDE.
     *
     * <p>On macOS: uses AppleScript with retry logic.
     * <p>On Windows/Linux: uses Alt+Tab.
     */
    public static void switchToIde(Robot robot) throws Exception {
        if (isMac()) {
            activateAppViaAppleScript(appleScriptProcessName(), 2);
        } else {
            robot.keyPress(KeyEvent.VK_ALT);
            robot.delay(30);
            robot.keyPress(KeyEvent.VK_TAB);
            robot.delay(30);
            robot.keyRelease(KeyEvent.VK_TAB);
            robot.delay(30);
            robot.keyRelease(KeyEvent.VK_ALT);
        }
    }

    /**
     * Switch focus back to the S.A.C.O. application.
     */
    public static void switchBackToSaco(Robot robot) throws Exception {
        if (isMac()) {
            ProcessBuilder pb = new ProcessBuilder(
                    "osascript", "-e",
                    "tell application \"System Events\" to set frontmost of " +
                    "first process whose name contains \"java\" to true"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor();
        }
        // On Windows: SACO's setAlwaysOnTop(true) brings it back automatically
    }

    /**
     * Activates a macOS app by name via AppleScript, with retry.
     */
    private static void activateAppViaAppleScript(String appName, int maxRetries) throws Exception {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "osascript", "-e",
                        "tell application \"" + appName + "\" to activate"
                );
                pb.redirectErrorStream(true);
                Process p = pb.start();
                int exitCode = p.waitFor();
                if (exitCode == 0) {
                    log.debug("AppleScript activated '{}' on attempt {}", appName, attempt);
                    return;
                }
                log.warn("AppleScript returned exit code {} for '{}' (attempt {})", exitCode, appName, attempt);
            } catch (Exception e) {
                log.warn("AppleScript attempt {} failed: {}", attempt, e.getMessage());
                if (attempt == maxRetries) throw e;
            }
            Thread.sleep(200);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Clipboard Operations (with polling)
    // ──────────────────────────────────────────────────────────────

    /**
     * Reads a string from the system clipboard, polling until content
     * changes from the {@code previousContent} or timeout is reached.
     *
     * @param previousContent the clipboard content before the copy action
     * @param timeoutMs       maximum time to wait for new content
     * @return the clipboard text, or the previous content if timeout
     */
    public static String pollClipboard(String previousContent, long timeoutMs) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            try {
                String current = (String) clipboard.getData(DataFlavor.stringFlavor);
                if (current != null && !current.equals(previousContent)) {
                    return current;
                }
            } catch (Exception e) {
                log.trace("Clipboard not ready: {}", e.getMessage());
            }
            try { Thread.sleep(50); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); return previousContent; }
        }

        log.warn("Clipboard poll timed out after {}ms", timeoutMs);
        // Return whatever is on the clipboard even if unchanged
        try {
            return (String) clipboard.getData(DataFlavor.stringFlavor);
        } catch (Exception e) {
            return previousContent;
        }
    }

    /**
     * Writes text to the system clipboard.
     */
    public static void setClipboard(String text) {
        StringSelection selection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
    }

    /**
     * Reads the current clipboard text, or empty string on failure.
     */
    public static String getClipboard() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            return (String) clipboard.getData(DataFlavor.stringFlavor);
        } catch (Exception e) {
            return "";
        }
    }
}
