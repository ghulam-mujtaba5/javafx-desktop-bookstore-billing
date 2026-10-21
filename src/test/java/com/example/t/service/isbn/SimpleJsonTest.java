package com.example.t.service.isbn;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 1.1-B: SimpleJson Parser Tests")
public class SimpleJsonTest {

    @Test
    @DisplayName("Parses null, blank, and empty strings")
    public void testEmptyAndBlank() {
        assertNull(SimpleJson.parse(null));
        assertNull(SimpleJson.parse(""));
        assertNull(SimpleJson.parse("   "));
        assertTrue(SimpleJson.parseObject("").isEmpty());
        assertTrue(SimpleJson.parseArray("").isEmpty());
    }

    @Test
    @DisplayName("Parses primitive types: strings, numbers, booleans, and null")
    public void testPrimitives() {
        assertEquals("hello world", SimpleJson.parse("\"hello world\""));
        assertEquals(42, SimpleJson.parse("42"));
        assertEquals(-123, SimpleJson.parse("-123"));
        assertEquals(3.14159, (Double) SimpleJson.parse("3.14159"), 0.00001);
        assertEquals(Boolean.TRUE, SimpleJson.parse("true"));
        assertEquals(Boolean.FALSE, SimpleJson.parse("false"));
        assertNull(SimpleJson.parse("null"));
    }

    @Test
    @DisplayName("Parses escape sequences in strings")
    public void testEscapeSequences() {
        assertEquals("Line 1\nLine 2\tTabbed \"Quoted\" \\Backslash\\",
                SimpleJson.parse("\"Line 1\\nLine 2\\tTabbed \\\"Quoted\\\" \\\\Backslash\\\\\""));
        assertEquals("Unicode: \u0041\u0042", SimpleJson.parse("\"Unicode: \\u0041\\u0042\""));
    }

    @Test
    @DisplayName("Parses JSON objects and nested structures")
    public void testObjectParsing() {
        String json = "{\n" +
                "  \"title\": \"Clean Code\",\n" +
                "  \"totalItems\": 1,\n" +
                "  \"published\": true,\n" +
                "  \"rating\": 4.8,\n" +
                "  \"authors\": [\"Robert C. Martin\", \"Dean Wampler\"],\n" +
                "  \"details\": {\n" +
                "    \"pages\": 464,\n" +
                "    \"publisher\": \"Prentice Hall\"\n" +
                "  }\n" +
                "}";

        Map<String, Object> map = SimpleJson.parseObject(json);
        assertEquals("Clean Code", map.get("title"));
        assertEquals(1, ((Number) map.get("totalItems")).intValue());
        assertEquals(Boolean.TRUE, map.get("published"));
        assertEquals(4.8, ((Number) map.get("rating")).doubleValue(), 0.001);

        @SuppressWarnings("unchecked")
        List<Object> authors = (List<Object>) map.get("authors");
        assertEquals(2, authors.size());
        assertEquals("Robert C. Martin", authors.get(0));
        assertEquals("Dean Wampler", authors.get(1));

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) map.get("details");
        assertEquals(464, ((Number) details.get("pages")).intValue());
        assertEquals("Prentice Hall", details.get("publisher"));
    }
}
