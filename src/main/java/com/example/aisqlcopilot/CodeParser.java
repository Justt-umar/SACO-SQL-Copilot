package com.example.aisqlcopilot;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for extracting SQL code blocks from AI-generated chat responses.
 *
 * Handles multiple markdown formats:
 * <ul>
 *   <li>{@code ```sql ... ```}</li>
 *   <li>{@code ```SQL ... ```}</li>
 *   <li>{@code ```mysql ... ```}</li>
 *   <li>{@code ``` ... ```} (bare code blocks, no language tag)</li>
 * </ul>
 *
 * Always returns the <em>last</em> matching block in the text so that
 * the most recent AI suggestion is grabbed.
 */
public class CodeParser {

    /**
     * Matches fenced code blocks with an optional sql/mysql language tag.
     * Group 1 captures everything between the opening and closing fences.
     */
    private static final Pattern SQL_BLOCK_PATTERN = Pattern.compile(
            "```(?:sql|mysql)?\\s*\\n(.*?)\\n?```",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    /**
     * Fallback: matches any fenced code block (no language tag required).
     */
    private static final Pattern ANY_BLOCK_PATTERN = Pattern.compile(
            "```\\s*\\n(.*?)\\n?```",
            Pattern.DOTALL
    );

    private CodeParser() { /* utility class */ }

    /**
     * Extracts the last SQL code block from the given text.
     *
     * First tries blocks tagged with {@code sql} or {@code mysql}.
     * If none are found, falls back to any fenced code block.
     *
     * @param text the full chat history text
     * @return the extracted SQL code, or empty if no code block was found
     */
    public static Optional<String> extractLastSqlBlock(String text) {
        if (text == null || text.isBlank()) return Optional.empty();

        // 1. Try SQL-tagged blocks first
        Optional<String> result = findLast(SQL_BLOCK_PATTERN, text);
        if (result.isPresent()) return result;

        // 2. Fall back to any fenced code block
        return findLast(ANY_BLOCK_PATTERN, text);
    }

    /**
     * Finds the last match of the given pattern in the text.
     */
    private static Optional<String> findLast(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        String lastMatch = null;
        while (matcher.find()) {
            String candidate = matcher.group(1).trim();
            if (!candidate.isEmpty()) {
                lastMatch = candidate;
            }
        }
        return Optional.ofNullable(lastMatch);
    }
}
