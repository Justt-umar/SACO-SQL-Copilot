package com.example.aisqlcopilot;

import java.awt.Robot;
import java.awt.event.KeyEvent;

/**
 * Cross-platform keyboard automation utility.
 * Detects the host OS at startup and uses the correct modifier keys and
 * application-switching strategy for each platform.
 *
 * - Windows / Linux: Ctrl-based shortcuts, Alt+Tab for window switching
 * - macOS:           Cmd-based shortcuts, AppleScript for targeted app activation
 */
public class OsAutomation {

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
    }

    /** The primary modifier key: Cmd (⌘) on macOS, Ctrl on Windows/Linux. */
    public static final int MODIFIER_KEY =
            (CURRENT_OS == OsFamily.MAC) ? KeyEvent.VK_META : KeyEvent.VK_CONTROL;

    /** Returns true when running on macOS. */
    public static boolean isMac() { return CURRENT_OS == OsFamily.MAC; }

    /** Returns true when running on Windows. */
    public static boolean isWindows() { return CURRENT_OS == OsFamily.WINDOWS; }

    /**
     * The preferred monospace font for the current platform.
     * SF Mono / Menlo on macOS, Consolas on Windows, monospace fallback on Linux.
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
     */
    public static String targetIdeName() {
        return switch (CURRENT_OS) {
            case MAC     -> "MySQL Workbench";
            case WINDOWS -> "MySQL Workbench";
            case LINUX   -> "MySQL Workbench";
        };
    }

    // ──────────────────────────────────────────────────────────────
    //  Keyboard Helpers
    // ──────────────────────────────────────────────────────────────

    /**
     * Sends Modifier + keyCode (e.g. Cmd+A on Mac, Ctrl+A on Windows).
     */
    public static void modifierCombo(Robot robot, int keyCode) {
        robot.keyPress(MODIFIER_KEY);
        robot.keyPress(keyCode);
        robot.keyRelease(keyCode);
        robot.keyRelease(MODIFIER_KEY);
    }

    /**
     * Switch the OS focus to the target SQL IDE.
     *
     * On macOS this uses AppleScript to directly activate "MySQLWorkbench"
     * by its process name — far more reliable than Cmd+Tab cycling.
     *
     * On Windows/Linux this uses Alt+Tab to return to the last-focused window
     * (the user should have the IDE as the previously active window).
     */
    public static void switchToIde(Robot robot) throws Exception {
        if (isMac()) {
            // AppleScript: activate MySQL Workbench by name
            ProcessBuilder pb = new ProcessBuilder(
                    "osascript", "-e",
                    "tell application \"MySQLWorkbench\" to activate"
            );
            pb.inheritIO();
            Process p = pb.start();
            p.waitFor();
        } else {
            // Alt+Tab on Windows / Linux
            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_ALT);
        }
    }

    /**
     * Switch focus back to this SACO application.
     *
     * On macOS: activates via AppleScript using the Java process name.
     * On Windows: Alt+Tab back (assumes SACO was the previous window).
     */
    public static void switchBackToSaco(Robot robot) throws Exception {
        if (isMac()) {
            ProcessBuilder pb = new ProcessBuilder(
                    "osascript", "-e",
                    "tell application \"System Events\" to set frontmost of " +
                    "first process whose name contains \"java\" to true"
            );
            pb.inheritIO();
            Process p = pb.start();
            p.waitFor();
        }
        // On Windows: SACO's setAlwaysOnTop(true) brings it back automatically
    }
}
