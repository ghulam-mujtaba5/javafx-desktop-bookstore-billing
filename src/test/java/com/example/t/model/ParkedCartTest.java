package com.example.t.model;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ParkedCartTest {

    @BeforeAll
    public static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {}
    }

    @Test
    @DisplayName("ParkedCart preserves domain attributes and builds formatted display name")
    public void testParkedCartPropertiesAndDisplay() {
        InvoiceItem item1 = new InvoiceItem(1, "Java Concurrency", "BK-001", 2, 45.00);
        InvoiceItem item2 = new InvoiceItem(2, "Gel Pen", "ST-002", 3, 5.00);

        ParkedCart cart = new ParkedCart(
                "Alice Smith",
                "555-1234",
                List.of(item1, item2),
                10.0,
                "Hold for 10 minutes",
                true
        );

        assertEquals("Alice Smith", cart.getCustomerName());
        assertEquals("555-1234", cart.getCustomerPhone());
        assertEquals(5, cart.getTotalQuantity()); // 2 + 3
        assertEquals(105.00, cart.getEstimatedSubtotal(), 0.001); // (2*45) + (3*5) = 90 + 15 = 105.00
        assertEquals(10.0, cart.getDiscountPercent(), 0.001);
        assertEquals("Hold for 10 minutes", cart.getNotes());
        assertTrue(cart.isGiftReceipt());
        assertNotNull(cart.getParkedAt());
        assertNotNull(cart.getCartId());
        assertTrue(cart.toString().contains("Alice Smith"));
        assertTrue(cart.toString().contains("5 items"));
    }

    @Test
    @DisplayName("Snapshot isolation: mutating source item does not affect ParkedCart items")
    public void testSnapshotDeepCopyIsolation() {
        InvoiceItem item = new InvoiceItem(10, 100, 1, "Refactoring", "BK-99", 1, 50.00, 50.00, 0.0, 0.0, "Original Note");
        List<InvoiceItem> sourceItems = new ArrayList<>();
        sourceItems.add(item);

        ParkedCart cart = new ParkedCart("Bob", "123", sourceItems, 0.0, "", false);

        // Mutate original item after parking
        item.setQuantity(10);
        item.setUnitPrice(99.00);
        item.setDiscountPercent(25.0);
        item.setNotes("Changed Note");

        // ParkedCart copy must remain isolated
        InvoiceItem parkedItem = cart.getItems().get(0);
        assertEquals(1, parkedItem.getQuantity(), "Parked quantity must not change");
        assertEquals(50.00, parkedItem.getUnitPrice(), 0.001, "Parked unit price must not change");
        assertEquals(0.0, parkedItem.getDiscountPercent(), 0.001, "Parked discount percent must not change");
        assertEquals(50.00, parkedItem.getLineTotal(), 0.001, "Parked line total must not change");
        assertEquals("Original Note", parkedItem.getNotes(), "Parked note must not change");
    }

    @Test
    @DisplayName("Restoring items creates new independent instances via createDeepCopyItems")
    public void testCreateDeepCopyItems() {
        InvoiceItem item = new InvoiceItem(1, "Clean Architecture", "BK-02", 1, 40.00);
        ParkedCart cart = new ParkedCart("Charlie", "", List.of(item), 0.0, "", false);

        List<InvoiceItem> restored = cart.createDeepCopyItems();
        assertEquals(1, restored.size());

        // Mutate restored item
        restored.get(0).setQuantity(5);

        // Verify cart items unaffected
        assertEquals(1, cart.getItems().get(0).getQuantity());
    }

    @Test
    @DisplayName("Line-item discount recalculates line total accurately on InvoiceItem")
    public void testInvoiceItemLineDiscountRecalculation() {
        InvoiceItem item = new InvoiceItem(1, "Domain-Driven Design", "BK-DDD", 2, 50.00);
        assertEquals(100.00, item.getLineTotal(), 0.001);
        assertEquals(0.0, item.getDiscountPercent(), 0.001);
        assertEquals(0.0, item.getDiscountAmount(), 0.001);

        // Apply 20% line discount
        item.setDiscountPercent(20.0);
        assertEquals(20.0, item.getDiscountPercent(), 0.001);
        assertEquals(20.00, item.getDiscountAmount(), 0.001); // 20% of 100.00 = 20.00
        assertEquals(80.00, item.getLineTotal(), 0.001);    // 100.00 - 20.00 = 80.00

        // Increase quantity to 3 -> raw 150.00, 20% disc = 30.00, total = 120.00
        item.setQuantity(3);
        assertEquals(30.00, item.getDiscountAmount(), 0.001);
        assertEquals(120.00, item.getLineTotal(), 0.001);
    }
}
