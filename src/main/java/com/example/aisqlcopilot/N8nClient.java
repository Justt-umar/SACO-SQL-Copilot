package com.example.aisqlcopilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Async HTTP client for communicating with the n8n AI Agent webhook.
 *
 * Improvements over the v1 inline approach:
 * <ul>
 *   <li>Uses Jackson for safe JSON serialization (no manual escaping)</li>
 *   <li>Sends a per-session {@code sessionId} for memory isolation</li>
 *   <li>Supports an optional {@code X-SACO-Token} auth header</li>
 *   <li>Provides a lightweight health-check endpoint probe</li>
 * </ul>
 */
public class N8nClient {

    private static final Logger log = LoggerFactory.getLogger(N8nClient.class);

    private final HttpClient   httpClient;
    private final ObjectMapper mapper;
    private final SacoConfig   config;

    public N8nClient(SacoConfig config) {
        this.config = config;
        this.mapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ── Response record ───────────────────────────────────────

    /** Immutable result from an n8n webhook call. */
    public record Response(int statusCode, String body) {
        public boolean isSuccess() { return statusCode == 200; }
    }

    // ── Send message to AI Agent ──────────────────────────────

    /**
     * POSTs a user prompt + current editor code to the n8n webhook.
     *
     * @param prompt      the user's message or {@code SYSTEM_COMMAND_EXECUTE:\n...}
     * @param currentCode the SQL currently in the code editor
     * @return a future that completes with the webhook response
     */
    public CompletableFuture<Response> sendMessage(String prompt, String currentCode) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("userPrompt",  prompt);
            payload.put("currentCode", currentCode);
            payload.put("sessionId",   config.getSessionId());

            String json = mapper.writeValueAsString(payload);
            log.debug("Sending payload to n8n ({} bytes)", json.length());

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(config.getWebhookUrl()))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json");

            // Attach auth token if configured
            String token = config.getAuthToken();
            if (token != null && !token.isBlank()) {
                reqBuilder.header("X-SACO-Token", token);
            }

            HttpRequest request = reqBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(resp -> {
                        log.info("n8n responded with status {}", resp.statusCode());
                        return new Response(resp.statusCode(), resp.body());
                    });

        } catch (Exception e) {
            log.error("Failed to build request: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    // ── Health check ──────────────────────────────────────────

    /**
     * Probes the n8n base URL to verify the server is reachable.
     *
     * @return a future that resolves to {@code true} if n8n responds, {@code false} otherwise
     */
    public CompletableFuture<Boolean> healthCheck() {
        try {
            // Derive base URL from webhook URL (strip path after host:port)
            URI webhookUri = URI.create(config.getWebhookUrl());
            URI baseUri = new URI(webhookUri.getScheme(), null,
                    webhookUri.getHost(), webhookUri.getPort(), "/", null, null);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(baseUri)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenApply(resp -> resp.statusCode() < 500)
                    .exceptionally(ex -> {
                        log.warn("n8n health check failed: {}", ex.getMessage());
                        return false;
                    });

        } catch (Exception e) {
            log.warn("Invalid webhook URL for health check: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }
}
