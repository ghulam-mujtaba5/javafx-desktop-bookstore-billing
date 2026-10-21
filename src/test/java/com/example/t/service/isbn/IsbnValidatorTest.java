package com.example.t.service.isbn;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 1.1-B: ISBN Validator & Bidirectional Converter Tests")
public class IsbnValidatorTest {

    // --- Cleaning & Normalization Tests ---

    @Test
    @DisplayName("clean(): Handles null, empty, and blank strings safely")
    public void testCleanNullAndBlank() {
        assertEquals("", IsbnValidator.clean(null));
        assertEquals("", IsbnValidator.clean(""));
        assertEquals("", IsbnValidator.clean("   "));
        assertEquals("", IsbnValidator.clean("\t\n"));
    }

    @Test
    @DisplayName("clean(): Strips hyphens, spaces, and punctuation")
    public void testCleanHyphensAndWhitespace() {
        assertEquals("0132350882", IsbnValidator.clean("0-13-235088-2"));
        assertEquals("9780132350884", IsbnValidator.clean("978-0-13-235088-4"));
        assertEquals("080442957X", IsbnValidator.clean(" 0-8044-2957-X "));
        assertEquals("080442957X", IsbnValidator.clean("(0.8044.2957.X)"));
    }

    @Test
    @DisplayName("clean(): Strips common ISBN prefixes case-insensitively")
    public void testCleanPrefixes() {
        assertEquals("0132350882", IsbnValidator.clean("ISBN: 0-13-235088-2"));
        assertEquals("0132350882", IsbnValidator.clean("isbn-10: 0-13-235088-2"));
        assertEquals("9780132350884", IsbnValidator.clean("ISBN-13: 978-0-13-235088-4"));
        assertEquals("9780132350884", IsbnValidator.clean("isbn 978-0-13-235088-4"));
        assertEquals("080442957X", IsbnValidator.clean("ISBN:080442957X"));
    }

    @Test
    @DisplayName("clean(): Converts lowercase 'x' check digit to uppercase 'X'")
    public void testCleanLowercaseX() {
        assertEquals("080442957X", IsbnValidator.clean("0-8044-2957-x"));
        assertEquals("020163361X", IsbnValidator.clean("020163361x"));
    }

    @Test
    @DisplayName("clean(): Idempotence property clean(clean(s)) == clean(s)")
    public void testCleanIdempotence() {
        String[] samples = {
                "ISBN: 0-13-235088-2",
                "978-0-13-235088-4",
                "0-8044-2957-x",
                "  [9780132350884]  ",
                "invalid-isbn-123"
        };
        for (String sample : samples) {
            String once = IsbnValidator.clean(sample);
            String twice = IsbnValidator.clean(once);
            assertEquals(once, twice, "clean() must be idempotent for input: " + sample);
        }
    }

    // --- ISBN-10 Validation Tests ---

    @Test
    @DisplayName("isValidIsbn10(): Returns true for valid ISBN-10 numbers")
    public void testValidIsbn10() {
        String[] valids = {
                "0-13-235088-2",        // Clean Code
                "0-451-52493-4",        // 1984
                "0-201-63361-2",        // Design Patterns
                "0-8044-2957-X",        // Check digit 'X'
                "0-8044-2957-x",        // Lowercase 'x' check digit
                "080442957X",           // Unhyphenated check digit 'X'
                "ISBN-10: 0-13-235088-2"
        };
        for (String raw : valids) {
            assertTrue(IsbnValidator.isValidIsbn10(raw), "Expected valid ISBN-10: " + raw);
            assertTrue(IsbnValidator.isValid(raw), "Expected isValid true for: " + raw);
        }
    }

    @Test
    @DisplayName("isValidIsbn10(): Returns false for invalid checksums, lengths, and characters")
    public void testInvalidIsbn10() {
        String[] invalids = {
                "0-13-235088-3",        // Checksum off by 1
                "0-451-52493-5",        // Checksum off by 1
                "0-8044-2957-0",        // Should end in X, ends in 0
                "0-8044-2957-9",        // Should end in X, ends in 9
                "013235088",            // 9 digits (too short)
                "01323508829",          // 11 digits (too long)
                "01323X0882",           // 'X' in non-check position
                "abcdefghij",           // Letters
                "",                     // Empty
                "   "                   // Blank
        };
        for (String raw : invalids) {
            assertFalse(IsbnValidator.isValidIsbn10(raw), "Expected invalid ISBN-10: " + raw);
        }
    }

    // --- ISBN-13 Validation Tests ---

    @Test
    @DisplayName("isValidIsbn13(): Returns true for valid ISBN-13 numbers")
    public void testValidIsbn13() {
        String[] valids = {
                "978-0-13-235088-4",    // Clean Code
                "978-0-451-52493-5",    // 1984
                "978-0-201-63361-0",    // Design Patterns
                "978-0-8044-2957-3",    // Converted from 080442957X
                "979-10-90636-07-1",    // 979 prefix
                "9780132350884",        // Unhyphenated
                "ISBN-13: 978-0-13-235088-4"
        };
        for (String raw : valids) {
            assertTrue(IsbnValidator.isValidIsbn13(raw), "Expected valid ISBN-13: " + raw);
            assertTrue(IsbnValidator.isValid(raw), "Expected isValid true for: " + raw);
        }
    }

    @Test
    @DisplayName("isValidIsbn13(): Returns false for invalid checksums, lengths, and characters")
    public void testInvalidIsbn13() {
        String[] invalids = {
                "978-0-13-235088-5",    // Checksum off by 1
                "978-0-451-52493-4",    // Checksum off by 1
                "978013235088",         // 12 digits (too short)
                "97801323508844",       // 14 digits (too long)
                "978-0-1323508-X",      // 'X' in ISBN-13 is strictly invalid
                "978-0-13X35088-4",     // 'X' in body
                "",                     // Empty
                "   "                   // Blank
        };
        for (String raw : invalids) {
            assertFalse(IsbnValidator.isValidIsbn13(raw), "Expected invalid ISBN-13: " + raw);
        }
    }

    // --- Bidirectional Conversion Tests ---

    @Test
    @DisplayName("toIsbn13(): Converts valid ISBN-10 to 13-digit ISBN with 978 prefix")
    public void testToIsbn13Success() {
        assertEquals("9780132350884", IsbnValidator.toIsbn13("0-13-235088-2"));
        assertEquals("9780451524935", IsbnValidator.toIsbn13("0-451-52493-4"));
        assertEquals("9780201633610", IsbnValidator.toIsbn13("0-201-63361-2"));
        assertEquals("9780804429573", IsbnValidator.toIsbn13("080442957X"));
        assertEquals("9780804429573", IsbnValidator.toIsbn13("0-8044-2957-x"));
    }

    @Test
    @DisplayName("toIsbn13(): Throws IllegalArgumentException for invalid ISBN-10")
    public void testToIsbn13InvalidThrows() {
        assertThrows(IllegalArgumentException.class, () -> IsbnValidator.toIsbn13("0-13-235088-3"));
        assertThrows(IllegalArgumentException.class, () -> IsbnValidator.toIsbn13("invalid"));
        assertThrows(IllegalArgumentException.class, () -> IsbnValidator.toIsbn13(null));
    }

    @Test
    @DisplayName("toIsbn10(): Converts valid 978-prefixed ISBN-13 to 10-digit ISBN")
    public void testToIsbn10Success() {
        Optional<String> isbn10_1 = IsbnValidator.toIsbn10("978-0-13-235088-4");
        assertTrue(isbn10_1.isPresent());
        assertEquals("0132350882", isbn10_1.get());

        Optional<String> isbn10_2 = IsbnValidator.toIsbn10("978-0-451-52493-5");
        assertTrue(isbn10_2.isPresent());
        assertEquals("0451524934", isbn10_2.get());

        // Produces check digit 'X'
        Optional<String> isbn10_3 = IsbnValidator.toIsbn10("978-0-8044-2957-3");
        assertTrue(isbn10_3.isPresent());
        assertEquals("080442957X", isbn10_3.get());
    }

    @Test
    @DisplayName("toIsbn10(): Returns Optional.empty() for 979 prefix")
    public void testToIsbn10With979PrefixReturnsEmpty() {
        // Valid ISBN-13 with 979 prefix cannot be mapped to 10-digit ISBN
        Optional<String> res = IsbnValidator.toIsbn10("979-10-90636-07-1");
        assertFalse(res.isPresent(), "979 prefix cannot be converted to ISBN-10");
    }

    @Test
    @DisplayName("toIsbn10(): Returns Optional.empty() for invalid ISBN-13")
    public void testToIsbn10InvalidReturnsEmpty() {
        assertFalse(IsbnValidator.toIsbn10("978-0-13-235088-5").isPresent());
        assertFalse(IsbnValidator.toIsbn10("not-an-isbn").isPresent());
        assertFalse(IsbnValidator.toIsbn10(null).isPresent());
    }

    @Test
    @DisplayName("Bidirectional conversion is reflexive for 978 ISBNs")
    public void testBidirectionalReflexivity() {
        String[] isbn10Samples = {
                "0132350882",
                "0451524934",
                "0201633612",
                "080442957X"
        };
        for (String isbn10 : isbn10Samples) {
            String isbn13 = IsbnValidator.toIsbn13(isbn10);
            Optional<String> convertedBack = IsbnValidator.toIsbn10(isbn13);
            assertTrue(convertedBack.isPresent());
            assertEquals(isbn10, convertedBack.get(), "Round-trip conversion failed for " + isbn10);
        }
    }
}
