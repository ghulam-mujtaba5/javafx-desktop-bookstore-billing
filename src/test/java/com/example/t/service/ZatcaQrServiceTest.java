package com.example.t.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

public class ZatcaQrServiceTest {

    @Test
    @DisplayName("Should generate valid Base64 TLV payload for ZATCA Gulf e-invoicing")
    void testZatcaTlvGeneration() {
        String seller = "Al-Noor Bookstore";
        String vatNum = "310123456700003";
        LocalDateTime now = LocalDateTime.of(2026, 10, 21, 14, 30, 0);
        double total = 250.00;
        double vat = 32.61;

        String base64Tlv = ZatcaQrService.generateTlvBase64(seller, vatNum, now, total, vat);
        assertNotNull(base64Tlv);
        assertFalse(base64Tlv.isEmpty());

        byte[] decoded = Base64.getDecoder().decode(base64Tlv);
        assertTrue(decoded.length > 20);

        // Verify Tag 1
        assertEquals(1, decoded[0]);
        int tag1Len = decoded[1];
        String decodedSeller = new String(decoded, 2, tag1Len, StandardCharsets.UTF_8);
        assertEquals(seller, decodedSeller);
    }
}
