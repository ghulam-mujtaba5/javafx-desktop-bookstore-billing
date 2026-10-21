package com.example.t.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Generates Saudi ZATCA (Zakat, Tax and Customs Authority) Phase 1 & 2
 * compliant TLV (Tag-Length-Value) Base64 QR code payloads for Simplified
 * Tax Invoices in Bookstore & Stationery Retail operations.
 *
 * TLV Tags:
 * Tag 1: Seller's name
 * Tag 2: Seller's VAT registration number (15 digits)
 * Tag 3: Time stamp (ISO 8601 or YYYY-MM-DDTHH:MM:SS)
 * Tag 4: Invoice total (with VAT)
 * Tag 5: Total VAT amount
 */
public class ZatcaQrService {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    /**
     * Generates a Base64-encoded TLV payload for embedding into Simplified Tax Invoices.
     *
     * @param sellerName  Legal trade name of the bookstore/stationery shop
     * @param vatNumber   15-digit VAT registration number
     * @param timestamp   Invoice date and time
     * @param totalWithVat Grand total payable including VAT
     * @param vatAmount   Total VAT amount collected
     * @return Base64-encoded TLV string
     */
    public static String generateTlvBase64(String sellerName,
                                          String vatNumber,
                                          LocalDateTime timestamp,
                                          double totalWithVat,
                                          double vatAmount) {
        if (sellerName == null || sellerName.isBlank()) sellerName = "Bookstore & Stationery";
        if (vatNumber == null || vatNumber.isBlank()) vatNumber = "300000000000003";
        if (timestamp == null) timestamp = LocalDateTime.now();

        String timeStr = timestamp.format(ISO_FORMATTER);
        String totalStr = BigDecimal.valueOf(totalWithVat).setScale(2, RoundingMode.HALF_UP).toPlainString();
        String vatStr = BigDecimal.valueOf(vatAmount).setScale(2, RoundingMode.HALF_UP).toPlainString();

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeTlv(out, 1, sellerName.getBytes(StandardCharsets.UTF_8));
            writeTlv(out, 2, vatNumber.getBytes(StandardCharsets.UTF_8));
            writeTlv(out, 3, timeStr.getBytes(StandardCharsets.UTF_8));
            writeTlv(out, 4, totalStr.getBytes(StandardCharsets.UTF_8));
            writeTlv(out, 5, vatStr.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    private static void writeTlv(ByteArrayOutputStream out, int tag, byte[] value) {
        out.write(tag);
        out.write(value.length);
        out.write(value, 0, value.length);
    }
}
