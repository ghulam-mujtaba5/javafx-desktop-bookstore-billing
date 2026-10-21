package com.example.t.service.isbn;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 1.1-B: Empirical Adversarial Challenge Test (IsbnValidator & SimpleJson)")
public class IsbnAndSimpleJsonEmpiricalChallengeTest {

    // =========================================================================
    // 1. ADVERSARIAL CHALLENGES FOR IsbnValidator
    // =========================================================================

    @Nested
    @DisplayName("1.1 ISBN-10 Checksum & Position Constraints")
    class Isbn10ChecksumChallenges {

        @Test
        @DisplayName("Valid check digit 'X' representing 10 is accepted")
        void testValidCheckDigitX() {
            String[] validWithX = {
                    "0-8044-2957-X",
                    "080442957X",
                    "0-8044-2957-x",   // Lowercase x
                    "080442957x",
                    "0-9752298-0-X",   // 097522980X: sum % 11 == 0
                    "1-55860-832-X",   // 155860832X: sum % 11 == 0
                    "0-393-04002-X"    // 039304002X: sum % 11 == 0
            };
            for (String isbn : validWithX) {
                assertTrue(IsbnValidator.isValidIsbn10(isbn), "Failed valid ISBN-10 with X: " + isbn);
                assertTrue(IsbnValidator.isValid(isbn), "Failed isValid for: " + isbn);
            }
        }

        @Test
        @DisplayName("Invalid 'X' in position other than check digit (pos 0 to 8) MUST be rejected")
        void testInvalidXInBodyPositions() {
            // Check position 0 to 8 with 'X'
            String[] invalidXPositions = {
                    "X804429573",  // pos 0
                    "0X04429573",  // pos 1
                    "08X4429573",  // pos 2
                    "080X429573",  // pos 3
                    "0804X29573",  // pos 4
                    "08044X9573",  // pos 5
                    "080442X573",  // pos 6
                    "0804429X73",  // pos 7
                    "08044295X3",  // pos 8
                    "XXXXXXXXXX",  // All X's
                    "08044295XX",  // Multiple X's
                    "01323X0882",  // X in middle of Clean Code
                    "0X32350882",
                    "X132350882"
            };

            for (String invalid : invalidXPositions) {
                assertFalse(IsbnValidator.isValidIsbn10(invalid),
                        "ISBN-10 with 'X' in non-check position must be invalid: " + invalid);
                assertFalse(IsbnValidator.isValid(invalid),
                        "isValid must return false for: " + invalid);
            }
        }

        @Test
        @DisplayName("ISBN-10 check digit mutations off by 1 to 10 are rejected")
        void testIsbn10OffByOneMutations() {
            // Clean Code valid ISBN-10: 0-13-235088-2
            // Check digits 0, 1, 3, 4, 5, 6, 7, 8, 9, X must all fail
            char[] allCheckDigits = {'0', '1', '3', '4', '5', '6', '7', '8', '9', 'X'};
            for (char ch : allCheckDigits) {
                String corrupted = "013235088" + ch;
                assertFalse(IsbnValidator.isValidIsbn10(corrupted),
                        "Corrupted ISBN-10 check digit '" + ch + "' must be invalid: " + corrupted);
            }

            // 0-8044-2957-X valid check digit is X (10)
            // Digits 0 to 9 must all fail
            for (char ch = '0'; ch <= '9'; ch++) {
                String corrupted = "080442957" + ch;
                assertFalse(IsbnValidator.isValidIsbn10(corrupted),
                        "Corrupted ISBN-10 check digit '" + ch + "' must be invalid: " + corrupted);
            }
        }

        @Test
        @DisplayName("ISBN-10 length and character boundaries (9, 11 digits, alpha, empty)")
        void testIsbn10BoundaryCases() {
            assertFalse(IsbnValidator.isValidIsbn10(null));
            assertFalse(IsbnValidator.isValidIsbn10(""));
            assertFalse(IsbnValidator.isValidIsbn10("   "));
            assertFalse(IsbnValidator.isValidIsbn10("013235088"));       // 9 digits
            assertFalse(IsbnValidator.isValidIsbn10("01323508822"));     // 11 digits
            assertFalse(IsbnValidator.isValidIsbn10("abcdefghij"));     // 10 letters
            assertFalse(IsbnValidator.isValidIsbn10("013235088@"));     // Special char check
        }
    }

    @Nested
    @DisplayName("1.2 ISBN-13 Checksum & Alternating Modulo 10 Math")
    class Isbn13ChecksumChallenges {

        @Test
        @DisplayName("Valid ISBN-13 with alternating 1 and 3 weights pass")
        void testValidIsbn13Numbers() {
            String[] validIsbn13 = {
                    "978-0-13-468599-1",    // Effective Java 3rd Ed
                    "978-0-13-235088-4",    // Clean Code
                    "978-0-451-52493-5",    // 1984
                    "978-0-201-63361-0",    // Design Patterns
                    "978-0-8044-2957-3",    // Converted from 080442957X
                    "979-10-90636-07-1",    // 979 prefix
                    "979-8-3503-4903-0",    // 979 prefix
                    "9780134685991"         // Clean digits
            };
            for (String isbn : validIsbn13) {
                assertTrue(IsbnValidator.isValidIsbn13(isbn), "Failed valid ISBN-13: " + isbn);
                assertTrue(IsbnValidator.isValid(isbn), "Failed isValid for: " + isbn);
            }
        }

        @Test
        @DisplayName("ISBN-13 check digit 'X' is strictly invalid (X only allowed in ISBN-10)")
        void testIsbn13WithXIsRejected() {
            String[] isbn13WithX = {
                    "978-0-13-46859-X",
                    "978-0-13468599X",
                    "978-0-13X68599-1",
                    "978-X-13-468599-1",
                    "979-10-90636-0-X"
            };
            for (String isbn : isbn13WithX) {
                assertFalse(IsbnValidator.isValidIsbn13(isbn),
                        "ISBN-13 must reject 'X': " + isbn);
            }
        }

        @Test
        @DisplayName("ISBN-13 check digit off by 1 to 9 mutations are rejected")
        void testIsbn13CheckDigitMutations() {
            // 978-0-13-468599-1: check digit is 1
            // 0, 2, 3, 4, 5, 6, 7, 8, 9 must all fail
            char[] invalidChecks = {'0', '2', '3', '4', '5', '6', '7', '8', '9'};
            for (char ch : invalidChecks) {
                String corrupted = "978013468599" + ch;
                assertFalse(IsbnValidator.isValidIsbn13(corrupted),
                        "Corrupted ISBN-13 check digit '" + ch + "' must be invalid: " + corrupted);
            }
        }

        @Test
        @DisplayName("ISBN-13 single transposition detection")
        void testIsbn13TranspositionDetection() {
            // Swap digits 3 and 4 in 9780134685991 (pos 5 and 6: '3' and '4')
            // '3' (weight 3) and '4' (weight 1): diff = 3(3)+4 - (3+3*4) = 13 - 15 = -2 != 0 mod 10
            String transposed = "9780143685991";
            assertFalse(IsbnValidator.isValidIsbn13(transposed),
                    "Transposed digits in ISBN-13 must be detected: " + transposed);
        }

        @Test
        @DisplayName("ISBN-13 length boundaries (12, 14 digits, empty, alpha)")
        void testIsbn13BoundaryCases() {
            assertFalse(IsbnValidator.isValidIsbn13(null));
            assertFalse(IsbnValidator.isValidIsbn13(""));
            assertFalse(IsbnValidator.isValidIsbn13("   "));
            assertFalse(IsbnValidator.isValidIsbn13("978013468599"));      // 12 digits
            assertFalse(IsbnValidator.isValidIsbn13("97801346859911"));    // 14 digits
            assertFalse(IsbnValidator.isValidIsbn13("978013468599a"));    // Trailing alpha
        }
    }

    @Nested
    @DisplayName("1.3 Canonical Prefix Cleaning & Formatting Robustness")
    class PrefixCleaningChallenges {

        @Test
        @DisplayName("Pervasive prefix and formatting variations resolve to canonical ISBN-13")
        void testIsbn13PrefixVariations() {
            String[] samples = {
                    "ISBN: 978-0-13-468599-1",
                    "isbn13:  9780134685991 ",
                    "ISBN-13: 978-0-13-468599-1",
                    "isbn 978-0-13-468599-1",
                    "  ISBN:   978-0-13-468599-1   ",
                    "ISBN-13 978 0 13 468599 1",
                    "isbn13:9780134685991",
                    "978---0--13--468599--1",
                    "(978) 0-13-468599-1",
                    "978.0.13.468599.1",
                    "\t978-0-13-468599-1\n"
            };

            for (String raw : samples) {
                assertEquals("9780134685991", IsbnValidator.clean(raw), "Failed cleaning raw input: " + raw);
                assertTrue(IsbnValidator.isValid(raw), "Failed isValid for raw input: " + raw);
                assertTrue(IsbnValidator.isValidIsbn13(raw), "Failed isValidIsbn13 for raw input: " + raw);
            }
        }

        @Test
        @DisplayName("Pervasive prefix and formatting variations resolve to canonical ISBN-10")
        void testIsbn10PrefixVariations() {
            String[] samples = {
                    "ISBN: 0-13-235088-2",
                    "isbn10: 0-13-235088-2",
                    "ISBN-10: 0-13-235088-2",
                    "isbn: 0-13-235088-2",
                    "  isbn:  0132350882  ",
                    "isbn10:0132350882",
                    "0--13--235088--2",
                    "(0.13.235088.2)"
            };

            for (String raw : samples) {
                assertEquals("0132350882", IsbnValidator.clean(raw), "Failed cleaning raw input: " + raw);
                assertTrue(IsbnValidator.isValid(raw), "Failed isValid for raw input: " + raw);
                assertTrue(IsbnValidator.isValidIsbn10(raw), "Failed isValidIsbn10 for raw input: " + raw);
            }
        }

        @Test
        @DisplayName("Lowercase check digit 'x' is canonicalized to uppercase 'X'")
        void testLowercaseCheckDigitCanonicalization() {
            assertEquals("080442957X", IsbnValidator.clean("isbn: 0-8044-2957-x"));
            assertEquals("080442957X", IsbnValidator.clean("080442957x"));
            assertTrue(IsbnValidator.isValid("080442957x"));
            assertTrue(IsbnValidator.isValidIsbn10("080442957x"));
        }
    }

    @Nested
    @DisplayName("1.4 Conversion Edge Cases & Bidirectional Integrity")
    class ConversionIntegrityChallenges {

        @Test
        @DisplayName("toIsbn10 on '979' prefix MUST return Optional.empty()")
        void testToIsbn10On979PrefixReturnsEmpty() {
            // Valid 979 ISBN-13s
            String[] isbn979 = {
                    "979-10-90636-07-1",
                    "979-8-3503-4903-0",
                    "9791090636071",
                    "9798350349030"
            };

            for (String isbn : isbn979) {
                assertTrue(IsbnValidator.isValidIsbn13(isbn), "Precondition: 979 ISBN must be valid ISBN-13: " + isbn);
                Optional<String> result = IsbnValidator.toIsbn10(isbn);
                assertTrue(result.isEmpty(), "toIsbn10 on 979 prefix must return Optional.empty() for: " + isbn);
            }
        }

        @Test
        @DisplayName("toIsbn10 on invalid ISBN-13 returns Optional.empty()")
        void testToIsbn10OnInvalidInputReturnsEmpty() {
            assertTrue(IsbnValidator.toIsbn10("978-0-13-468599-0").isEmpty()); // Bad checksum
            assertTrue(IsbnValidator.toIsbn10("97801346859").isEmpty());        // Short
            assertTrue(IsbnValidator.toIsbn10("not-an-isbn").isEmpty());
            assertTrue(IsbnValidator.toIsbn10("").isEmpty());
            assertTrue(IsbnValidator.toIsbn10(null).isEmpty());
        }

        @Test
        @DisplayName("toIsbn13 on invalid ISBN-10 throws IllegalArgumentException")
        void testToIsbn13OnInvalidInputThrows() {
            assertThrows(IllegalArgumentException.class, () -> IsbnValidator.toIsbn13("0-13-235088-3")); // Bad checksum
            assertThrows(IllegalArgumentException.class, () -> IsbnValidator.toIsbn13("01323508"));      // Short
            assertThrows(IllegalArgumentException.class, () -> IsbnValidator.toIsbn13("invalid"));
            assertThrows(IllegalArgumentException.class, () -> IsbnValidator.toIsbn13(null));
        }

        @Test
        @DisplayName("Bidirectional conversion integrity (toIsbn13(toIsbn10(x)) == x for 978 prefix)")
        void testBidirectionalConversionIntegrity() {
            String[] testIsbn13s = {
                    "9780134685991",    // Effective Java
                    "9780132350884",    // Clean Code
                    "9780451524935",    // 1984
                    "9780201633610",    // Design Patterns
                    "9780804429573"     // Ends in X in ISBN-10
            };

            for (String original13 : testIsbn13s) {
                Optional<String> isbn10Opt = IsbnValidator.toIsbn10(original13);
                assertTrue(isbn10Opt.isPresent(), "Conversion to ISBN-10 failed for: " + original13);
                String isbn10 = isbn10Opt.get();
                assertTrue(IsbnValidator.isValidIsbn10(isbn10), "Resulting ISBN-10 must be valid: " + isbn10);

                String roundTrip13 = IsbnValidator.toIsbn13(isbn10);
                assertEquals(original13, roundTrip13,
                        "Round trip toIsbn13(toIsbn10(" + original13 + ")) failed!");
            }
        }

        @Test
        @DisplayName("Bidirectional conversion integrity (toIsbn10(toIsbn13(x)) == x for valid ISBN-10)")
        void testBidirectionalFromIsbn10Integrity() {
            String[] testIsbn10s = {
                    "0134685997",
                    "0132350882",
                    "0451524934",
                    "0201633612",
                    "080442957X"
            };

            for (String original10 : testIsbn10s) {
                String isbn13 = IsbnValidator.toIsbn13(original10);
                assertTrue(IsbnValidator.isValidIsbn13(isbn13), "Resulting ISBN-13 must be valid: " + isbn13);

                Optional<String> roundTrip10Opt = IsbnValidator.toIsbn10(isbn13);
                assertTrue(roundTrip10Opt.isPresent(), "Round trip back to ISBN-10 must be present");
                assertEquals(original10, roundTrip10Opt.get(),
                        "Round trip toIsbn10(toIsbn13(" + original10 + ")) failed!");
            }
        }
    }

    // =========================================================================
    // 2. ADVERSARIAL CHALLENGES FOR SimpleJson
    // =========================================================================

    @Nested
    @DisplayName("2.1 Malformed & Truncated JSON Resilience")
    class MalformedAndTruncatedJsonChallenges {

        @Test
        @DisplayName("Malformed JSON syntax throws IllegalArgumentException without crashing VM")
        void testMalformedJsonThrowsIllegalArgumentException() {
            String[] malformedPayloads = {
                    "{\"missing_colon\" \"value\"}",
                    "{unquoted_key: \"value\"}",
                    "{\"key\": }",
                    "{\"key\": \"val\",, \"key2\": 1}",
                    "{\"key\": \"val\", \"key2\": }",
                    "}",
                    "]",
                    "{\"k\": truth}",
                    "{\"k\": falsity}",
                    "{\"k\": nullify}"
            };

            for (String badJson : malformedPayloads) {
                assertThrows(IllegalArgumentException.class, () -> SimpleJson.parse(badJson),
                        "Malformed JSON should throw IllegalArgumentException: " + badJson);
            }
        }

        @Test
        @DisplayName("Truncated JSON strings and escape sequences throw IllegalArgumentException")
        void testTruncatedJsonThrows() {
            String[] truncatedPayloads = {
                    "\"unterminated string",
                    "\"unterminated escape \\",
                    "\"truncated unicode \\u00",
                    "\"truncated unicode \\u004",
                    "{\"unclosed_key\": \"val\"",
                    "{\"unclosed_brace\": 1",
                    "[1, 2, 3",
                    "{\"nested\": {\"inner\": 1}"
            };

            for (String truncated : truncatedPayloads) {
                assertThrows(IllegalArgumentException.class, () -> SimpleJson.parse(truncated),
                        "Truncated JSON must throw IllegalArgumentException: " + truncated);
            }
        }

        @Test
        @DisplayName("Invalid numbers throw IllegalArgumentException")
        void testInvalidNumbersThrow() {
            String[] badNumbers = {
                    "-",
                    "+",
                    "--1",
                    "1.2.3",
                    "1e2e3"
            };

            for (String badNum : badNumbers) {
                assertThrows(IllegalArgumentException.class, () -> SimpleJson.parse(badNum),
                        "Invalid number string should throw IllegalArgumentException: " + badNum);
            }
        }
    }

    @Nested
    @DisplayName("2.2 Escaped Characters & Unicode Resilience")
    class EscapesAndUnicodeChallenges {

        @Test
        @DisplayName("All standard JSON escape sequences are parsed correctly")
        void testAllStandardEscapes() {
            String json = "\"Quote: \\\" Backslash: \\\\ Slash: \\/ Bell: \\b FF: \\f LF: \\n CR: \\r Tab: \\t\"";
            Object parsed = SimpleJson.parse(json);
            assertNotNull(parsed);
            assertEquals("Quote: \" Backslash: \\ Slash: / Bell: \b FF: \f LF: \n CR: \r Tab: \t", parsed);
        }

        @Test
        @DisplayName("Unicode 4-digit hex escapes (\\uXXXX) are decoded accurately")
        void testUnicodeHexEscapes() {
            // \u0041 = 'A', \u0042 = 'B', \u0639 = 'ع', \u4E16 = '世'
            String json = "\"\\u0041\\u0042 - \\u0639 - \\u4E16\"";
            Object parsed = SimpleJson.parse(json);
            assertEquals("AB - ع - 世", parsed);
        }

        @Test
        @DisplayName("UTF-16 surrogate pairs for emoji (e.g. \\uD83D\\uDCD6 for 📖)")
        void testSurrogatePairEmoji() {
            String json = "\"Book: \\uD83D\\uDCD6\"";
            Object parsed = SimpleJson.parse(json);
            assertEquals("Book: 📖", parsed);
        }

        @Test
        @DisplayName("Direct UTF-8 multi-byte characters are preserved verbatim")
        void testDirectUtf8Characters() {
            String json = "{\n" +
                    "  \"arabic\": \"كتاب الفهرست للمكتبات\",\n" +
                    "  \"japanese\": \"吾輩は猫である\",\n" +
                    "  \"currency\": \"100.00 د.إ\",\n" +
                    "  \"symbols\": \"© ® ™ € £ ¥ §\",\n" +
                    "  \"emoji\": \"📚 🚀 💡\"\n" +
                    "}";

            Map<String, Object> map = SimpleJson.parseObject(json);
            assertEquals("كتاب الفهرست للمكتبات", map.get("arabic"));
            assertEquals("吾輩は猫である", map.get("japanese"));
            assertEquals("100.00 د.إ", map.get("currency"));
            assertEquals("© ® ™ € £ ¥ §", map.get("symbols"));
            assertEquals("📚 🚀 💡", map.get("emoji"));
        }
    }

    @Nested
    @DisplayName("2.3 Nested Structures & Mixed-Type Arrays")
    class NestedAndMixedStructuresChallenges {

        @Test
        @DisplayName("Mixed-type arrays preserve exact Java types")
        void testMixedTypeArray() {
            String json = "[100, 3.14159, \"mixed string\", true, false, null, [\"nested\", 42], {\"inner\": true}]";
            List<Object> list = SimpleJson.parseArray(json);
            assertEquals(8, list.size());

            assertEquals(100, ((Number) list.get(0)).intValue());
            assertEquals(3.14159, ((Number) list.get(1)).doubleValue(), 0.0001);
            assertEquals("mixed string", list.get(2));
            assertEquals(Boolean.TRUE, list.get(3));
            assertEquals(Boolean.FALSE, list.get(4));
            assertNull(list.get(5));

            @SuppressWarnings("unchecked")
            List<Object> subList = (List<Object>) list.get(6);
            assertEquals(2, subList.size());
            assertEquals("nested", subList.get(0));
            assertEquals(42, ((Number) subList.get(1)).intValue());

            @SuppressWarnings("unchecked")
            Map<String, Object> subMap = (Map<String, Object>) list.get(7);
            assertEquals(Boolean.TRUE, subMap.get("inner"));
        }

        @Test
        @DisplayName("Deeply nested objects and arrays parse without stack overflow")
        void testDeepNesting() {
            String deepJson = "{\"l1\": {\"l2\": {\"l3\": {\"l4\": {\"leaf\": [1, [2, [3, [4]]]]}}}}}";
            Map<String, Object> root = SimpleJson.parseObject(deepJson);
            assertNotNull(root);

            @SuppressWarnings("unchecked")
            Map<String, Object> l1 = (Map<String, Object>) root.get("l1");
            @SuppressWarnings("unchecked")
            Map<String, Object> l2 = (Map<String, Object>) l1.get("l2");
            @SuppressWarnings("unchecked")
            Map<String, Object> l3 = (Map<String, Object>) l2.get("l3");
            @SuppressWarnings("unchecked")
            Map<String, Object> l4 = (Map<String, Object>) l3.get("l4");

            @SuppressWarnings("unchecked")
            List<Object> leaf = (List<Object>) l4.get("leaf");
            assertNotNull(leaf);
            assertEquals(2, leaf.size());
            assertEquals(1, ((Number) leaf.get(0)).intValue());
        }

        @Test
        @DisplayName("Empty objects and empty arrays")
        void testEmptyContainers() {
            assertEquals(0, SimpleJson.parseObject("{}").size());
            assertEquals(0, SimpleJson.parseObject("{  \n\t  }").size());
            assertEquals(0, SimpleJson.parseArray("[]").size());
            assertEquals(0, SimpleJson.parseArray("[  \n\t  ]").size());
        }

        @Test
        @DisplayName("parseObject and parseArray handle mismatched types gracefully without crashing")
        void testGracefulTypeMismatches() {
            // parseObject when payload is an array
            Map<String, Object> objFromArr = SimpleJson.parseObject("[1, 2, 3]");
            assertNotNull(objFromArr);
            assertTrue(objFromArr.isEmpty());

            // parseArray when payload is an object
            List<Object> arrFromObj = SimpleJson.parseArray("{\"k\": \"v\"}");
            assertNotNull(arrFromObj);
            assertTrue(arrFromObj.isEmpty());

            // parseObject on primitives
            assertTrue(SimpleJson.parseObject("123").isEmpty());
            assertTrue(SimpleJson.parseObject("\"hello\"").isEmpty());
            assertTrue(SimpleJson.parseObject("true").isEmpty());
            assertTrue(SimpleJson.parseObject("null").isEmpty());

            // parseArray on primitives
            assertTrue(SimpleJson.parseArray("123").isEmpty());
            assertTrue(SimpleJson.parseArray("\"hello\"").isEmpty());
            assertTrue(SimpleJson.parseArray("false").isEmpty());
            assertTrue(SimpleJson.parseArray("null").isEmpty());
        }

        @Test
        @DisplayName("Numbers exceeding 32-bit Integer range parse cleanly as 64-bit Long")
        void testLongNumbers() {
            String json = "{\"bigLong\": 5000000000, \"smallInt\": 42}";
            Map<String, Object> map = SimpleJson.parseObject(json);
            assertEquals(5000000000L, map.get("bigLong"));
            assertEquals(42, map.get("smallInt"));
        }
    }
}
