package com.saco.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Calls the Google Gemini API to generate AI SQL reviews.
 * The API key is read from application.properties / environment variable.
 */
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private static final String SYSTEM_PROMPT = """
            You are S.A.C.O. (SQL Agentic Co-Pilot Orchestrator), an expert MySQL assistant built by Umar Khan.

            RULES:
            1. Help review, debug, optimize, and draft MySQL-compatible SQL code.
            2. Always return SQL inside a ```sql fenced code block.
            3. Use MySQL syntax: LIMIT not TOP, backtick quoting, AUTO_INCREMENT, IFNULL, ENGINE=InnoDB.
            4. Structure: Issues Found → Suggested Fix (sql block) → Explanation.
            5. Warn about: missing WHERE on UPDATE/DELETE, implicit cross joins, SELECT *, N+1 queries.
            6. If correct, say so and suggest improvements.
            7. Keep explanations concise but technically precise.
            8. This is a LIVE DEMO for recruiters — be impressive and showcase deep SQL knowledge.
            """;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    /**
     * Sends the user's prompt and optional SQL code to Gemini and returns the AI response text.
     */
    public String chat(String userPrompt, String currentCode) throws Exception {
        // Build the user message
        String userMessage = (currentCode != null && !currentCode.isBlank())
                ? "User Request: " + userPrompt + "\n\nCurrent SQL Code:\n```sql\n" + currentCode + "\n```"
                : "User Request: " + userPrompt;

        // Build Gemini request body using Jackson (safe JSON — no injection)
        ObjectNode body = mapper.createObjectNode();

        // System instruction
        ObjectNode sysInstruction = body.putObject("system_instruction");
        ArrayNode sysParts = sysInstruction.putArray("parts");
        sysParts.addObject().put("text", SYSTEM_PROMPT);

        // User content
        ArrayNode contents = body.putArray("contents");
        ObjectNode userContent = contents.addObject();
        userContent.put("role", "user");
        userContent.putArray("parts").addObject().put("text", userMessage);

        // Generation config
        ObjectNode genConfig = body.putObject("generationConfig");
        genConfig.put("temperature", 0.7);
        genConfig.put("maxOutputTokens", 2048);

        String url = String.format(GEMINI_URL, model, apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Gemini API error (HTTP {}): {}", response.statusCode(), response.body());
            throw new RuntimeException("AI service returned HTTP " + response.statusCode());
        }

        JsonNode root = mapper.readTree(response.body());
        JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");

        if (textNode.isMissingNode()) {
            log.warn("Unexpected Gemini response structure: {}", response.body());
            return "No response from AI.";
        }

        return textNode.asText();
    }
}
