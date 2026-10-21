package com.example.t.controller;

import com.example.t.dao.InvoiceDao;
import com.example.t.dao.ProductDao;
import com.example.t.dao.SettingsDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ReportsDashboardControllerTest {

    @Test
    @DisplayName("ReportsDashboardController instance should initialize properly")
    void testControllerInstantiation() {
        ReportsDashboardController controller = new ReportsDashboardController();
        assertNotNull(controller);

        InvoiceDao invoiceDao = new InvoiceDao();
        ProductDao productDao = new ProductDao();
        SettingsDao settingsDao = new SettingsDao();

        controller.setInvoiceDao(invoiceDao);
        controller.setProductDao(productDao);
        controller.setSettingsDao(settingsDao);

        // Test TopProductRow model
        ReportsDashboardController.TopProductRow row =
                new ReportsDashboardController.TopProductRow("Clean Code", "Books", 15, 599.85);

        assertEquals("Clean Code", row.getName());
        assertEquals("Books", row.getCategory());
        assertEquals(15, row.getQuantitySold());
        assertEquals(599.85, row.getRevenue(), 0.001);
    }
}
