package com.example.t;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ProductTest {

    @Test
    @DisplayName("Default constructor should initialize with safe defaults")
    void testDefaultConstructor() {
        Product p = new Product();
        assertEquals(0, p.getProductId());
        assertEquals("", p.getProductName());
        assertEquals("General", p.getCategory());
        assertEquals(0.0, p.getPrice());
        assertEquals(0, p.getQuantity());
        assertTrue(p.getStatus());
    }

    @Test
    @DisplayName("5-arg constructor should initialize price, purchasePrice, and quantity properly")
    void testLegacyConstructor() {
        Product p = new Product(101, "Java in Action", 15, 49.99, 30.00);
        assertEquals(101, p.getProductId());
        assertEquals("Java in Action", p.getProductName());
        assertEquals(15, p.getQuantity());
        assertEquals(49.99, p.getPrice(), 0.001);
        assertEquals(30.00, p.getPurchasePrice(), 0.001);
        assertEquals("SKU-101", p.getSku());
    }

    @Test
    @DisplayName("Comprehensive constructor should store Books vs Stationery metadata")
    void testComprehensiveConstructor() {
        Product book = new Product(102, "Clean Architecture", "Books", "ISBN-978-0134494166",
                "Robert C. Martin", "Prentice Hall", "1st", 25.0, 45.0, 20, 5);

        assertEquals("Books", book.getCategory());
        assertEquals("ISBN-978-0134494166", book.getSku());
        assertEquals("Robert C. Martin", book.getAuthor());
        assertEquals("Prentice Hall", book.getPublisher());
        assertEquals("1st", book.getEdition());
        assertEquals(5, book.getMinStockThreshold());
    }

    @Test
    @DisplayName("JavaFX properties should update and notify listeners")
    void testPropertyBinding() {
        Product p = new Product(1, "Notebook", 50, 4.50, 2.50);

        boolean[] nameChanged = {false};
        p.productNameProperty().addListener((obs, oldVal, newVal) -> nameChanged[0] = true);
        p.setProductName("Spiral Notebook A4");
        assertTrue(nameChanged[0]);
        assertEquals("Spiral Notebook A4", p.getProductName());

        boolean[] qtyChanged = {false};
        p.quantityProperty().addListener((obs, oldVal, newVal) -> qtyChanged[0] = true);
        p.setQuantity(45);
        assertTrue(qtyChanged[0]);
        assertEquals(45, p.getQuantity());
    }

    @Test
    @DisplayName("calculateDiscountedPrice should compute discounted price correctly")
    void testDiscountCalculation() {
        Product p = new Product(2, "Parker Pen", 10, 100.0, 60.0);
        p.setDiscount(20.0); // 20% discount on 100.0
        assertEquals(80.0, p.getDiscountedPrice(), 0.001);

        p.setDiscount(0.0);
        assertEquals(100.0, p.getDiscountedPrice(), 0.001);
    }

    @Test
    @DisplayName("updateProduct should update core fields in a single call")
    void testUpdateProduct() {
        Product p = new Product(3, "Pencil", 100, 1.0, 0.5);
        p.updateProduct(3, "Mechanical Pencil", 2.5, 80, true, 1.2);

        assertEquals("Mechanical Pencil", p.getProductName());
        assertEquals(2.5, p.getPrice(), 0.001);
        assertEquals(80, p.getQuantity());
        assertEquals(1.2, p.getPurchasePrice(), 0.001);
        assertTrue(p.getStatus());
    }
}
