package com.example.aisqlcopilot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SacoConfig} — default values and environment overrides.
 */
class SacoConfigTest {

    @Test
    @DisplayName("Default webhook URL is localhost:5678")
    void defaultWebhookUrl() {
        SacoConfig config = new SacoConfig();
        assertEquals("http://localhost:5678/webhook/saco-chat", config.getWebhookUrl());
    }

    @Test
    @DisplayName("Session ID is generated and non-empty")
    void sessionIdGenerated() {
        SacoConfig config = new SacoConfig();
        assertNotNull(config.getSessionId());
        assertFalse(config.getSessionId().isBlank());
    }

    @Test
    @DisplayName("Two instances get different session IDs")
    void uniqueSessionIds() {
        SacoConfig config1 = new SacoConfig();
        SacoConfig config2 = new SacoConfig();
        assertNotEquals(config1.getSessionId(), config2.getSessionId());
    }

    @Test
    @DisplayName("Default font size is 14")
    void defaultFontSize() {
        SacoConfig config = new SacoConfig();
        assertEquals(14, config.getFontSize());
    }

    @Test
    @DisplayName("Default opacity is 0.95")
    void defaultOpacity() {
        SacoConfig config = new SacoConfig();
        assertEquals(0.95, config.getOpacity(), 0.001);
    }

    @Test
    @DisplayName("Setters update values correctly")
    void settersWork() {
        SacoConfig config = new SacoConfig();
        config.setWebhookUrl("http://example.com/webhook");
        config.setAuthToken("my-secret");
        config.setFontSize(18);
        config.setOpacity(0.8);

        assertEquals("http://example.com/webhook", config.getWebhookUrl());
        assertEquals("my-secret", config.getAuthToken());
        assertEquals(18, config.getFontSize());
        assertEquals(0.8, config.getOpacity(), 0.001);
    }
}
