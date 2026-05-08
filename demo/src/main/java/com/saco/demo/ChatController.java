package com.saco.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST controller for the AI chat endpoint.
 *
 * POST /api/chat — accepts { userPrompt, currentCode } and returns { response }.
 * Rate-limited to 30 requests/hour per IP to prevent abuse.
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private static final int RATE_LIMIT_MAX = 30;
    private static final long RATE_LIMIT_WINDOW = 3600_000L; // 1 hour

    private final GeminiService geminiService;
    private final ConcurrentHashMap<String, long[]> rateLimits = new ConcurrentHashMap<>();
    // rateLimits: IP → [windowStart, count]

    public ChatController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwarded,
            jakarta.servlet.http.HttpServletRequest servletRequest) {

        // Rate limiting
        String ip = (forwarded != null) ? forwarded.split(",")[0].trim() : servletRequest.getRemoteAddr();
        if (isRateLimited(ip)) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "Rate limit reached (30 queries/hour). Thanks for trying S.A.C.O.! 🙏"
            ));
        }

        String userPrompt = request.getOrDefault("userPrompt", "").trim();
        String currentCode = request.getOrDefault("currentCode", "");

        if (userPrompt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "userPrompt is required."));
        }

        try {
            log.info("Chat request from {}: {}", ip, userPrompt.substring(0, Math.min(60, userPrompt.length())));
            String aiResponse = geminiService.chat(userPrompt, currentCode);
            return ResponseEntity.ok(Map.of("response", aiResponse));
        } catch (Exception e) {
            log.error("Chat error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "AI service temporarily unavailable: " + e.getMessage()
            ));
        }
    }

    private boolean isRateLimited(String ip) {
        long now = System.currentTimeMillis();
        return rateLimits.compute(ip, (key, entry) -> {
            if (entry == null || now - entry[0] > RATE_LIMIT_WINDOW) {
                return new long[]{now, 1};
            }
            entry[1]++;
            return entry;
        })[1] > RATE_LIMIT_MAX;
    }
}
