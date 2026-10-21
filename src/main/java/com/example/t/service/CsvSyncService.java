package com.example.t.service;

import com.example.t.Invoice;
import com.example.t.Product;
import com.example.t.database.DatabaseManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Service for RFC 4180 compliant bidirectional CSV synchronization.
 * Handles escaping of special characters (quotes, commas, newlines) and preserves round-trip fidelity.
 */
public class CsvSyncService {

    private static final String PRODUCT_HEADER = "sku,name,price,stock_quantity,author";
    private static final String INVOICE_HEADER = "Invoice ID,Invoice Date,Customer Name,Subtotal,Tax,Net Total";

    /**
     * Formats a row of fields into an RFC 4180 compliant CSV line.
     */
    public static String formatRow(List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(escapeField(fields.get(i)));
        }
        return sb.toString();
    }

    /**
     * Escapes a single field according to RFC 4180:
     * - If field contains comma, quote, or newline, enclose in double quotes.
     * - Double quotes within field are escaped as double double-quotes ("").
     */
    public static String escapeField(String field) {
        if (field == null) {
            return "";
        }
        if (field.contains("\"") || field.contains(",") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    /**
     * Parses an RFC 4180 compliant CSV text into a 2D list of rows and fields.
     */
    public static List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return rows;
        }

        List<String> currentRow = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);

            if (inQuotes) {
                if (c == '\"') {
                    if (i + 1 < len && text.charAt(i + 1) == '\"') {
                        currentField.append('\"');
                        i++; // skip escaped quote
                    } else {
                        inQuotes = false;
                    }
                } else {
                    currentField.append(c);
                }
            } else {
                if (c == '\"') {
                    inQuotes = true;
                } else if (c == ',') {
                    currentRow.add(currentField.toString());
                    currentField.setLength(0);
                } else if (c == '\r') {
                    if (i + 1 < len && text.charAt(i + 1) == '\n') {
                        i++; // handle CRLF
                    }
                    currentRow.add(currentField.toString());
                    currentField.setLength(0);
                    rows.add(currentRow);
                    currentRow = new ArrayList<>();
                } else if (c == '\n') {
                    currentRow.add(currentField.toString());
                    currentField.setLength(0);
                    rows.add(currentRow);
                    currentRow = new ArrayList<>();
                } else {
                    currentField.append(c);
                }
            }
        }

        // Add final field and row if any characters remain
        if (currentField.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(currentField.toString());
            rows.add(currentRow);
        }

        return rows;
    }

    public static List<List<String>> parseCsv(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int read;
        while ((read = reader.read(buf)) != -1) {
            sb.append(buf, 0, read);
        }
        return parseCsv(sb.toString());
    }

    /**
     * Exports a list of Product objects to an RFC 4180 CSV string.
     */
    public static String exportProductsToCsv(List<Product> products) {
        StringBuilder sb = new StringBuilder();
        sb.append(PRODUCT_HEADER).append("\n");

        if (products != null) {
            for (Product p : products) {
                List<String> row = Arrays.asList(
                        p.getSku() != null ? p.getSku() : "",
                        p.getProductName() != null ? p.getProductName() : "",
                        String.valueOf(p.getPrice()),
                        String.valueOf(p.getQuantity()),
                        p.getAuthor() != null ? p.getAuthor() : ""
                );
                sb.append(formatRow(row)).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Exports products to the specified file path.
     */
    public static void exportProductsToFile(List<Product> products, Path file) throws IOException {
        if (file.getParent() != null && !Files.exists(file.getParent())) {
            Files.createDirectories(file.getParent());
        }
        String csv = exportProductsToCsv(products);
        Files.writeString(file, csv, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    /**
     * Imports products from CSV text into a list of Product objects.
     */
    public static List<Product> importProductsFromCsv(String csvText) {
        List<Product> products = new ArrayList<>();
        List<List<String>> records = parseCsv(csvText);
        if (records.isEmpty()) return products;

        int startIndex = 0;
        // Check if first row is header
        List<String> firstRow = records.get(0);
        if (!firstRow.isEmpty() && (firstRow.get(0).equalsIgnoreCase("sku") || firstRow.get(0).toLowerCase().contains("product"))) {
            startIndex = 1;
        }

        for (int i = startIndex; i < records.size(); i++) {
            List<String> r = records.get(i);
            if (r.size() >= 3) {
                try {
                    String sku = r.get(0).trim();
                    String name = r.get(1).trim();
                    double price = Double.parseDouble(r.get(2).trim());
                    int quantity = r.size() > 3 ? Integer.parseInt(r.get(3).trim()) : 0;
                    String author = r.size() > 4 ? r.get(4).trim() : "";

                    Product p = new Product(0, name, quantity, price, price * 0.7);
                    p.setSku(sku);
                    p.setAuthor(author);
                    products.add(p);
                } catch (Exception ignored) {}
            }
        }
        return products;
    }

    /**
     * Imports products from a file.
     */
    public static List<Product> importProductsFromFile(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        return importProductsFromCsv(content);
    }

    /**
     * Exports a list of Invoice objects to an RFC 4180 CSV string.
     */
    public static String exportInvoicesToCsv(List<Invoice> invoices) {
        StringBuilder sb = new StringBuilder();
        sb.append(INVOICE_HEADER).append("\n");

        if (invoices != null) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            for (Invoice inv : invoices) {
                String dateStr = inv.getInvoiceDate() != null ? inv.getInvoiceDate().format(fmt) : "";
                List<String> row = Arrays.asList(
                        String.valueOf(inv.getInvoiceId()),
                        dateStr,
                        inv.getCustomerName() != null ? inv.getCustomerName() : "Walk-in Customer",
                        String.format("%.2f", inv.getOrderTotal()),
                        String.format("%.2f", inv.getDiscount()),
                        String.format("%.2f", inv.getNetPrice())
                );
                sb.append(formatRow(row)).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Exports invoice records to the specified file path.
     */
    public static void exportInvoicesToFile(List<Invoice> invoices, Path file) throws IOException {
        if (file.getParent() != null && !Files.exists(file.getParent())) {
            Files.createDirectories(file.getParent());
        }
        String csv = exportInvoicesToCsv(invoices);
        Files.writeString(file, csv, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    /**
     * Appends an individual invoice to the historical CSV ledger file.
     */
    public static synchronized void appendInvoiceToLedger(Invoice invoice, Path ledgerPath) throws IOException {
        if (ledgerPath == null) {
            ledgerPath = DatabaseManager.getExportsDirectory().resolve("invoice_history.csv");
        }
        if (ledgerPath.getParent() != null && !Files.exists(ledgerPath.getParent())) {
            Files.createDirectories(ledgerPath.getParent());
        }

        boolean writeHeader = !Files.exists(ledgerPath) || Files.size(ledgerPath) == 0;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String dateStr = invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().format(fmt) : "";

        List<String> row = Arrays.asList(
                String.valueOf(invoice.getInvoiceId()),
                dateStr,
                invoice.getCustomerName() != null ? invoice.getCustomerName() : "Walk-in Customer",
                String.format("%.2f", invoice.getOrderTotal()),
                String.format("%.2f", invoice.getDiscount()),
                String.format("%.2f", invoice.getNetPrice())
        );

        StringBuilder sb = new StringBuilder();
        if (writeHeader) {
            sb.append(INVOICE_HEADER).append("\n");
        }
        sb.append(formatRow(row)).append("\n");

        Files.writeString(ledgerPath, sb.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
    }
}
