package com.example.t.dao;

import com.example.t.model.Customer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class CustomerDaoTest {

    @Test
    @DisplayName("Should create customer, find by phone, and accrue loyalty points")
    void testCustomerLifecycle() throws SQLException {
        CustomerDao dao = new CustomerDao();

        String uniquePhone = "0300" + (System.currentTimeMillis() % 10000000);
        Customer customer = new Customer(0, "Alice Reader", uniquePhone, "alice@example.com", 50, 10.0, 150.0);

        int id = dao.saveCustomer(customer);
        assertTrue(id > 0, "Customer ID should be positive");

        Optional<Customer> found = dao.findByPhone(uniquePhone);
        assertTrue(found.isPresent(), "Customer should be findable by phone");
        assertEquals("Alice Reader", found.get().getName());
        assertEquals(50, found.get().getLoyaltyPoints());

        // Accrue loyalty points
        dao.addLoyaltyPoints(id, 25);
        Optional<Customer> updated = dao.findByPhone(uniquePhone);
        assertTrue(updated.isPresent());
        assertEquals(75, updated.get().getLoyaltyPoints());
    }
}
