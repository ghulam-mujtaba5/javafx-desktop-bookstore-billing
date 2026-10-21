package com.example.t.e2e.tier2;

import com.example.t.e2e.harness.CsvTestHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tier 2 - Feature 8: CSV Synchronization Boundary & Corner Cases")
public class CsvBoundariesE2ETest {

    @Test
    @DisplayName("B8.1: Completely Empty CSV String yields Empty Record List")
    public void testEmptyCsvContent() throws Exception {
        List<List<String>> records = CsvTestHelper.parseCsv("");
        assertNotNull(records);
        assertTrue(records.isEmpty());

        List<List<String>> nullRecords = CsvTestHelper.parseCsv(null);
        assertNotNull(nullRecords);
        assertTrue(nullRecords.isEmpty());
    }

    @Test
    @DisplayName("B8.2: CSV Content with Whitespace Only yields Empty Record List")
    public void testWhitespaceOnlyCsv() throws Exception {
        List<List<String>> records = CsvTestHelper.parseCsv("   \n\t  \r\n  ");
        assertNotNull(records);
        assertTrue(records.isEmpty());
    }

    @Test
    @DisplayName("B8.3: RFC 4180 Multiline Field with Embedded Newlines in Quotes")
    public void testEmbeddedNewlinesInQuotedField() throws Exception {
        String csv = "sku,description,price\n" +
                "BK-NOTE,\"This is line 1.\nThis is line 2 of notes.\nThis is line 3.\",15.00\n";

        List<List<String>> records = CsvTestHelper.parseCsv(csv);
        assertEquals(2, records.size()); // 1 header + 1 record

        List<String> row = records.get(1);
        assertEquals("BK-NOTE", row.get(0));
        assertEquals("This is line 1.\nThis is line 2 of notes.\nThis is line 3.", row.get(1));
        assertEquals("15.00", row.get(2));
    }

    @Test
    @DisplayName("B8.4: Multiple Consecutive Commas for Empty / Null Fields")
    public void testEmptyAndConsecutiveCommaFields() throws Exception {
        String csv = "BK-1,,Stationery,,0.00\n";
        List<List<String>> records = CsvTestHelper.parseCsv(csv);
        assertEquals(1, records.size());

        List<String> row = records.get(0);
        assertEquals(5, row.size());
        assertEquals("BK-1", row.get(0));
        assertEquals("", row.get(1));
        assertEquals("Stationery", row.get(2));
        assertEquals("", row.get(3));
        assertEquals("0.00", row.get(4));
    }

    @Test
    @DisplayName("B8.5: Windows CRLF Line Endings parsed accurately")
    public void testWindowsCrlfLineEndings() throws Exception {
        String csv = "sku,name,price\r\nBK-1,Book One,10.00\r\nBK-2,Book Two,20.00\r\n";
        List<List<String>> records = CsvTestHelper.parseCsv(csv);
        assertEquals(3, records.size());
        assertEquals("Book One", records.get(1).get(1));
        assertEquals("Book Two", records.get(2).get(1));
    }

    @Test
    @DisplayName("B8.6: Formatting Row with Empty and Null Fields produces valid RFC 4180 CSV")
    public void testFormatRowWithNullsAndQuotes() {
        List<String> fields = Arrays.asList("SKU-1", null, "Item, with \"Special\" Quotes", "");
        String formatted = CsvTestHelper.formatRow(fields);

        assertEquals("SKU-1,,\"Item, with \"\"Special\"\" Quotes\",", formatted);
    }
}
