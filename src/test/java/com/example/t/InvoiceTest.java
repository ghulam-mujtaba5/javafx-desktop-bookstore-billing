package com.example.t;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class InvoiceTest {

    @Test
    @DisplayName("Default constructor should initialize with default properties")
    void testDefaultConstructor() {
        Invoice inv = new Invoice();
        assertEquals(0, inv.getInvoiceId());
        assertEquals("Walk-in Customer", inv.getCustomerName());
        assertNotNull(inv.getInvoiceDate());
        assertNotNull(inv.getProducts());
        assertEquals(0.0, inv.getOrderTotal());
    }

    @Test
    @DisplayName("Parameterized constructor should initialize all fields correctly")
    void testParameterizedConstructor() {
        List<Product> items = new ArrayList<>();
        items.add(new Product(1, "Java Book", 2, 25.0, 15.0));

        LocalDate date = LocalDate.of(2026, 10, 19);
        Invoice inv = new Invoice(501, 1001, "Jane Smith", date, items, 50.0, 10.0, 5.0, 45.0);

        assertEquals(501, inv.getInvoiceId());
        assertEquals(1001, inv.getOrderNum());
        assertEquals("Jane Smith", inv.getCustomerName());
        assertEquals(date, inv.getInvoiceDate());
        assertEquals(1, inv.getProducts().size());
        assertEquals(50.0, inv.getOrderTotal(), 0.001);
        assertEquals(10.0, inv.getDiscountPercentage(), 0.001);
        assertEquals(5.0, inv.getDiscount(), 0.001);
        assertEquals(45.0, inv.getNetPrice(), 0.001);
    }

    @Test
    @DisplayName("JavaFX property accessors should reflect model updates")
    void testPropertyBindings() {
        Invoice inv = new Invoice();

        inv.setInvoiceId(777);
        assertEquals(777, inv.invoiceIdProperty().get());

        inv.setCustomerName("John Doe");
        assertEquals("John Doe", inv.customerNameProperty().get());

        LocalDate newDate = LocalDate.of(2026, 1, 1);
        inv.setInvoiceDate(newDate);
        assertEquals(newDate, inv.invoiceDateProperty().get());

        inv.setOrderTotal(199.95);
        assertEquals(199.95, inv.orderTotalProperty().get(), 0.001);
    }

    @Test
    @DisplayName("readInvoicesFromFile should return a list without throwing exceptions")
    void testReadInvoicesFromFile() {
        assertDoesNotThrow(() -> {
            List<Invoice> list = Invoice.readInvoicesFromFile();
            assertNotNull(list);
        });
    }
}
