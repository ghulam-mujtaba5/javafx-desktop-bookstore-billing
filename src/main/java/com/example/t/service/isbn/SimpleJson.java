package com.example.t.service.isbn;

import java.util.*;

/**
 * Lightweight, zero-dependency JSON parser that parses JSON strings into standard
 * Java types: Map<String, Object>, List<Object>, String, Number (Long or Double),
 * Boolean, and null.
 */
public final class SimpleJson {

    private SimpleJson() {}

    public static Object parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return new Parser(json).parseValue();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object val = parse(json);
        if (val instanceof Map) {
            return (Map<String, Object>) val;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> parseArray(String json) {
        Object val = parse(json);
        if (val instanceof List) {
            return (List<Object>) val;
        }
        return Collections.emptyList();
    }

    private static final class Parser {
        private final String src;
        private final int len;
        private int pos;

        Parser(String src) {
            this.src = src;
            this.len = src.length();
            this.pos = 0;
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= len) {
                return null;
            }
            char c = src.charAt(pos);
            switch (c) {
                case '{':
                    return parseObject();
                case '[':
                    return parseArray();
                case '"':
                    return parseString();
                case 't':
                case 'f':
                    return parseBoolean();
                case 'n':
                    return parseNull();
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return parseNumber();
                    }
                    throw new IllegalArgumentException("Unexpected character at position " + pos + ": " + c);
            }
        }

        private Map<String, Object> parseObject() {
            match('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (pos < len && src.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (pos < len) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                match(':');
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (pos < len && src.charAt(pos) == ',') {
                    pos++;
                    continue;
                } else if (pos < len && src.charAt(pos) == '}') {
                    pos++;
                    break;
                } else {
                    throw new IllegalArgumentException("Expected ',' or '}' at position " + pos);
                }
            }
            return map;
        }

        private List<Object> parseArray() {
            match('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (pos < len && src.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (pos < len) {
                Object val = parseValue();
                list.add(val);
                skipWhitespace();
                if (pos < len && src.charAt(pos) == ',') {
                    pos++;
                    continue;
                } else if (pos < len && src.charAt(pos) == ']') {
                    pos++;
                    break;
                } else {
                    throw new IllegalArgumentException("Expected ',' or ']' at position " + pos);
                }
            }
            return list;
        }

        private String parseString() {
            skipWhitespace();
            match('"');
            StringBuilder sb = new StringBuilder();
            while (pos < len) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= len) {
                        throw new IllegalArgumentException("Unterminated escape sequence at end of string");
                    }
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > len) {
                                throw new IllegalArgumentException("Invalid unicode escape at position " + pos);
                            }
                            String hex = src.substring(pos, pos + 4);
                            pos += 4;
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException e) {
                                throw new IllegalArgumentException("Invalid unicode hex: " + hex);
                            }
                            break;
                        default:
                            sb.append(esc);
                            break;
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        private Number parseNumber() {
            skipWhitespace();
            int start = pos;
            if (pos < len && (src.charAt(pos) == '-' || src.charAt(pos) == '+')) {
                pos++;
            }
            boolean isFloating = false;
            while (pos < len) {
                char c = src.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    isFloating = true;
                    pos++;
                } else {
                    break;
                }
            }
            String numStr = src.substring(start, pos);
            try {
                if (isFloating) {
                    return Double.parseDouble(numStr);
                } else {
                    long l = Long.parseLong(numStr);
                    if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                        return (int) l;
                    }
                    return l;
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number: " + numStr, e);
            }
        }

        private Boolean parseBoolean() {
            skipWhitespace();
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            } else if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Invalid boolean literal at position " + pos);
        }

        private Object parseNull() {
            skipWhitespace();
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Invalid null literal at position " + pos);
        }

        private void skipWhitespace() {
            while (pos < len) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private void match(char expected) {
            skipWhitespace();
            if (pos >= len || src.charAt(pos) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at position " + pos +
                        (pos < len ? ", found '" + src.charAt(pos) + "'" : ", found EOF"));
            }
            pos++;
        }
    }
}
