package com.example.aisqlcopilot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CodeParser} — SQL block extraction from AI responses.
 */
class CodeParserTest {

    // ── Positive cases ───────────────────────────────────────

    @Test
    @DisplayName("Extracts SQL from standard ```sql block")
    void extractStandardSqlBlock() {
        String chat = "Here's the fix:\n```sql\nSELECT * FROM users;\n```\nLet me know.";
        Optional<String> result = CodeParser.extractLastSqlBlock(chat);

        assertTrue(result.isPresent());
        assertEquals("SELECT * FROM users;", result.get());
    }

    @Test
    @DisplayName("Extracts SQL from uppercase ```SQL block")
    void extractUppercaseSqlBlock() {
        String chat = "Try this:\n```SQL\nSELECT id, name FROM orders;\n```\n";
        Optional<String> result = CodeParser.extractLastSqlBlock(chat);

        assertTrue(result.isPresent());
        assertEquals("SELECT id, name FROM orders;", result.get());
    }

    @Test
    @DisplayName("Extracts SQL from ```mysql block")
    void extractMysqlBlock() {
        String chat = "```mysql\nSHOW TABLES;\n```";
        Optional<String> result = CodeParser.extractLastSqlBlock(chat);

        assertTrue(result.isPresent());
        assertEquals("SHOW TABLES;", result.get());
    }

    @Test
    @DisplayName("Extracts last block when multiple are present")
    void extractLastOfMultipleBlocks() {
        String chat = """
                First fix:
                ```sql
                SELECT 1;
                ```
                Actually, try this instead:
                ```sql
                SELECT 2;
                ```
                """;
        Optional<String> result = CodeParser.extractLastSqlBlock(chat);

        assertTrue(result.isPresent());
        assertEquals("SELECT 2;", result.get());
    }

    @Test
    @DisplayName("Falls back to untagged code block")
    void fallbackToUntaggedBlock() {
        String chat = "Here's the code:\n```\nSELECT * FROM products;\n```\n";
        Optional<String> result = CodeParser.extractLastSqlBlock(chat);

        assertTrue(result.isPresent());
        assertEquals("SELECT * FROM products;", result.get());
    }

    @Test
    @DisplayName("Handles multi-line SQL blocks")
    void extractMultiLineSql() {
        String chat = """
                ```sql
                SELECT u.id, u.name, o.total
                FROM users u
                INNER JOIN orders o ON u.id = o.user_id
                WHERE o.status = 'active'
                ORDER BY o.total DESC
                LIMIT 10;
                ```
                """;
        Optional<String> result = CodeParser.extractLastSqlBlock(chat);

        assertTrue(result.isPresent());
        assertTrue(result.get().contains("INNER JOIN"));
        assertTrue(result.get().contains("LIMIT 10;"));
    }

    // ── Negative cases ───────────────────────────────────────

    @Test
    @DisplayName("Returns empty for null input")
    void emptyForNull() {
        assertTrue(CodeParser.extractLastSqlBlock(null).isEmpty());
    }

    @Test
    @DisplayName("Returns empty for blank input")
    void emptyForBlank() {
        assertTrue(CodeParser.extractLastSqlBlock("   ").isEmpty());
    }

    @Test
    @DisplayName("Returns empty when no code blocks exist")
    void emptyForNoBlocks() {
        String chat = "You should try using a JOIN here instead of a subquery.";
        assertTrue(CodeParser.extractLastSqlBlock(chat).isEmpty());
    }

    @Test
    @DisplayName("Returns empty for incomplete code block (no closing fence)")
    void emptyForIncompleteBlock() {
        String chat = "```sql\nSELECT 1;\n";
        // No closing ``` — should not match
        assertTrue(CodeParser.extractLastSqlBlock(chat).isEmpty());
    }
}
