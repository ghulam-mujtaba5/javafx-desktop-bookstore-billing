package com.example.t.e2e.harness;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * RFC 4180 compliant CSV parser and generator for E2E tests.
 * Validates CSV imports, exports, and round-trip data fidelity.
 */
public class CsvTestHelper {

    /**
     * Formats a row of string fields into an RFC 4180 compliant CSV line.
     */
    public static String formatRow(List<String> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(",");
            String field = fields.get(i);
            if (field == null) {
                field = "";
            }
            boolean needsQuotes = field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r");
            if (needsQuotes) {
                sb.append("\"");
                sb.append(field.replace("\"", "\"\""));
                sb.append("\"");
            } else {
                sb.append(field);
            }
        }
        return sb.toString();
    }

    /**
     * Parses full CSV content into rows and fields following RFC 4180.
     */
    public static List<List<String>> parseCsv(String csvContent) throws IOException {
        List<List<String>> records = new ArrayList<>();
        if (csvContent == null || csvContent.trim().isEmpty()) {
            return records;
        }

        try (BufferedReader reader = new BufferedReader(new StringReader(csvContent))) {
            String line;
            StringBuilder multiLineBuffer = new StringBuilder();
            boolean insideQuotes = false;

            while ((line = reader.readLine()) != null) {
                if (insideQuotes) {
                    multiLineBuffer.append("\n").append(line);
                } else {
                    multiLineBuffer = new StringBuilder(line);
                }

                // Count unescaped quotes in the accumulated string
                String current = multiLineBuffer.toString();
                insideQuotes = isInsideQuotes(current);

                if (!insideQuotes) {
                    records.add(parseLine(current));
                    multiLineBuffer.setLength(0);
                }
            }
        }
        return records;
    }

    private static boolean isInsideQuotes(String s) {
        boolean inQuotes = false;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '"') {
                // If double quote (""), it's an escape sequence
                if (i + 1 < s.length() && s.charAt(i + 1) == '"') {
                    i++; // skip escaped quote
                } else {
                    inQuotes = !inQuotes;
                }
            }
        }
        return inQuotes;
    }

    private static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++; // skip escaped quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields;
    }
}
