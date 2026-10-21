package com.example.t.service.isbn;

import java.util.Optional;

/**
 * Universal ISBN validator and bidirectional converter supporting ISBN-10 (Modulo 11)
 * and ISBN-13 (Modulo 10).
 *
 * Provides canonical cleaning, checksum verification, and bidirectional conversion
 * between 10-digit and 13-digit ("978...") book identifiers.
 */
public final class IsbnValidator {

    private IsbnValidator() {
        // Utility class
    }

    /**
     * Canonicalizes raw ISBN input:
     * 1. Returns empty string ("") if raw is null or blank.
     * 2. Strips common prefixes such as "ISBN:", "ISBN-10:", "ISBN-13:", "isbn ".
     * 3. Converts lowercase 'x' check digits to uppercase 'X'.
     * 4. Strips all characters outside [0-9X].
     *
     * @param raw the raw input string
     * @return clean, uppercase alphanumeric string containing only digits and 'X'
     */
    public static String clean(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.trim();
        // Remove common ISBN prefixes before stripping non-digits
        s = s.replaceFirst("(?i)^ISBN(?:-?1[03])?[:\\s]+", "");
        // Uppercase first, then strip anything not a digit 0-9 or 'X'
        return s.toUpperCase().replaceAll("[^0-9X]", "").trim();
    }

    /**
     * Validates an ISBN-10 string using Modulo 11 checksum verification.
     * Characters 0-8 must be digits, character 9 can be a digit or 'X' (representing 10).
     * Sum = \sum_{i=0}^{9} d_i * (10 - i). Valid iff Sum % 11 == 0.
     *
     * @param raw the raw or cleaned ISBN-10
     * @return true if valid ISBN-10, false otherwise
     */
    public static boolean isValidIsbn10(String raw) {
        String s = clean(raw);
        if (s.length() != 10) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            char c = s.charAt(i);
            if (!Character.isDigit(c)) {
                return false;
            }
            sum += (c - '0') * (10 - i);
        }
        char last = s.charAt(9);
        if (last == 'X') {
            sum += 10;
        } else if (Character.isDigit(last)) {
            sum += (last - '0');
        } else {
            return false;
        }
        return sum % 11 == 0;
    }

    /**
     * Validates an ISBN-13 string using Modulo 10 checksum verification with alternating
     * weights 1 and 3. All 13 characters must be digits.
     * Sum = \sum_{i=0}^{12} d_i * (i % 2 == 0 ? 1 : 3). Valid iff Sum % 10 == 0.
     *
     * @param raw the raw or cleaned ISBN-13
     * @return true if valid ISBN-13, false otherwise
     */
    public static boolean isValidIsbn13(String raw) {
        String s = clean(raw);
        if (s.length() != 13) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            char c = s.charAt(i);
            if (!Character.isDigit(c)) {
                return false;
            }
            int weight = (i % 2 == 0) ? 1 : 3;
            sum += (c - '0') * weight;
        }
        return sum % 10 == 0;
    }

    /**
     * Validates an ISBN string of either 10 or 13 digits.
     *
     * @param raw the raw or cleaned ISBN
     * @return true if valid ISBN-10 or ISBN-13, false otherwise
     */
    public static boolean isValid(String raw) {
        String s = clean(raw);
        if (s.length() == 10) {
            return isValidIsbn10(s);
        }
        if (s.length() == 13) {
            return isValidIsbn13(s);
        }
        return false;
    }

    /**
     * Converts a valid 10-digit ISBN to a 13-digit ISBN with "978" prefix
     * and newly computed Modulo 10 check digit.
     *
     * @param isbn10Raw the raw or cleaned ISBN-10
     * @return the 13-digit ISBN string
     * @throws IllegalArgumentException if the input is not a valid ISBN-10
     */
    public static String toIsbn13(String isbn10Raw) {
        if (!isValidIsbn10(isbn10Raw)) {
            throw new IllegalArgumentException("Invalid ISBN-10: " + isbn10Raw);
        }
        String s = clean(isbn10Raw);
        String prefix12 = "978" + s.substring(0, 9);
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int weight = (i % 2 == 0) ? 1 : 3;
            sum += (prefix12.charAt(i) - '0') * weight;
        }
        int check = (10 - (sum % 10)) % 10;
        return prefix12 + check;
    }

    /**
     * Converts a valid 13-digit ISBN with "978" prefix to a 10-digit ISBN with newly
     * computed Modulo 11 check digit.
     * Returns Optional.empty() if the ISBN-13 is invalid or has a "979" prefix.
     *
     * @param isbn13Raw the raw or cleaned ISBN-13
     * @return Optional containing the 10-digit ISBN if convertible, or empty
     */
    public static Optional<String> toIsbn10(String isbn13Raw) {
        if (!isValidIsbn13(isbn13Raw)) {
            return Optional.empty();
        }
        String s = clean(isbn13Raw);
        if (!s.startsWith("978")) {
            // ISBN-13s with 979 prefix cannot be converted to ISBN-10
            return Optional.empty();
        }
        String core9 = s.substring(3, 12);
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (core9.charAt(i) - '0') * (10 - i);
        }
        int rem = sum % 11;
        int check = (11 - rem) % 11;
        char checkChar = (check == 10) ? 'X' : (char) ('0' + check);
        return Optional.of(core9 + checkChar);
    }
}
