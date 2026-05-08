package com.example.aisqlcopilot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;
import java.util.UUID;

/**
 * Externalized configuration for S.A.C.O.
 *
 * Settings are loaded from {@code ~/.saco/config.properties} on startup
 * and persisted back when the user changes them via the Settings dialog.
 * Values that are not found in the file fall back to sensible defaults
 * or to environment variables (e.g. {@code SACO_WEBHOOK_URL}).
 *
 * A unique {@code sessionId} is generated on every launch so that the
 * n8n memory buffer keeps conversations isolated between app instances.
 */
public class SacoConfig {

    private static final Logger log = LoggerFactory.getLogger(SacoConfig.class);

    private static final String CONFIG_DIR  = System.getProperty("user.home") + "/.saco";
    private static final String CONFIG_FILE = CONFIG_DIR + "/config.properties";

    // ── Defaults ──────────────────────────────────────────────
    private static final String DEFAULT_WEBHOOK_URL     = "http://localhost:5678/webhook/saco-chat";
    private static final String DEFAULT_CONTAINER_NAME  = "zealous_nightingale";
    private static final int    DEFAULT_FONT_SIZE       = 14;
    private static final double DEFAULT_OPACITY         = 0.95;
    private static final int    DEFAULT_WINDOW_W        = 1100;
    private static final int    DEFAULT_WINDOW_H        = 700;

    // ── Fields ────────────────────────────────────────────────
    private String webhookUrl;
    private String authToken;
    private String n8nContainerName;
    private final String sessionId;
    private int    fontSize;
    private double opacity;
    private int    windowWidth;
    private int    windowHeight;

    public SacoConfig() {
        this.sessionId = UUID.randomUUID().toString();
        applyDefaults();
        load();
    }

    // ── Persistence ───────────────────────────────────────────

    private void applyDefaults() {
        String envUrl = System.getenv("SACO_WEBHOOK_URL");
        webhookUrl   = (envUrl != null && !envUrl.isBlank()) ? envUrl : DEFAULT_WEBHOOK_URL;

        String envToken = System.getenv("SACO_AUTH_TOKEN");
        authToken    = (envToken != null) ? envToken : "";

        String envContainer = System.getenv("SACO_N8N_CONTAINER");
        n8nContainerName = (envContainer != null && !envContainer.isBlank()) ? envContainer : DEFAULT_CONTAINER_NAME;

        fontSize     = DEFAULT_FONT_SIZE;
        opacity      = DEFAULT_OPACITY;
        windowWidth  = DEFAULT_WINDOW_W;
        windowHeight = DEFAULT_WINDOW_H;
    }

    public void load() {
        Path path = Path.of(CONFIG_FILE);
        if (!Files.exists(path)) {
            log.info("No config file at {}; using defaults.", CONFIG_FILE);
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
            webhookUrl       = props.getProperty("webhook.url",        webhookUrl);
            authToken        = props.getProperty("auth.token",          authToken);
            n8nContainerName = props.getProperty("n8n.container.name",  n8nContainerName);
            fontSize     = Integer.parseInt(props.getProperty("ui.font.size",   String.valueOf(fontSize)));
            opacity      = Double.parseDouble(props.getProperty("ui.opacity",   String.valueOf(opacity)));
            windowWidth  = Integer.parseInt(props.getProperty("ui.window.width",  String.valueOf(windowWidth)));
            windowHeight = Integer.parseInt(props.getProperty("ui.window.height", String.valueOf(windowHeight)));
            log.info("Configuration loaded from {}", CONFIG_FILE);
        } catch (Exception e) {
            log.warn("Failed to read config file; using defaults: {}", e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(Path.of(CONFIG_DIR));
            Properties props = new Properties();
            props.setProperty("webhook.url",         webhookUrl);
            props.setProperty("auth.token",          authToken);
            props.setProperty("n8n.container.name",  n8nContainerName);
            props.setProperty("ui.font.size",        String.valueOf(fontSize));
            props.setProperty("ui.opacity",          String.valueOf(opacity));
            props.setProperty("ui.window.width",     String.valueOf(windowWidth));
            props.setProperty("ui.window.height",    String.valueOf(windowHeight));

            try (OutputStream out = Files.newOutputStream(Path.of(CONFIG_FILE))) {
                props.store(out, "S.A.C.O. Configuration — auto-generated");
            }
            log.info("Configuration saved to {}", CONFIG_FILE);
        } catch (IOException e) {
            log.error("Failed to save config: {}", e.getMessage(), e);
        }
    }

    // ── Getters / Setters ─────────────────────────────────────

    public String getWebhookUrl()       { return webhookUrl; }
    public void   setWebhookUrl(String v) { this.webhookUrl = v; }

    public String getAuthToken()        { return authToken; }
    public void   setAuthToken(String v)  { this.authToken = v; }

    public String getSessionId()        { return sessionId; }

    public int    getFontSize()         { return fontSize; }
    public void   setFontSize(int v)      { this.fontSize = v; }

    public double getOpacity()          { return opacity; }
    public void   setOpacity(double v)    { this.opacity = v; }

    public int    getWindowWidth()      { return windowWidth; }
    public void   setWindowWidth(int v)   { this.windowWidth = v; }

    public int    getWindowHeight()     { return windowHeight; }
    public void   setWindowHeight(int v)  { this.windowHeight = v; }

    public String getN8nContainerName()       { return n8nContainerName; }
    public void   setN8nContainerName(String v) { this.n8nContainerName = v; }
}
