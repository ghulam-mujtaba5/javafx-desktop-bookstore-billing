package com.example.t.dao;

import com.example.t.database.DatabaseManager;
import com.example.t.model.Customer;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Customers and Loyalty Ledger.
 */
public class CustomerDao {

    public CustomerDao() {
        initTable();
    }

    private void initTable() {
        String sql = "CREATE TABLE IF NOT EXISTS customers (" +
                "customer_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "phone TEXT UNIQUE, " +
                "email TEXT, " +
                "loyalty_points INTEGER DEFAULT 0, " +
                "store_credit REAL DEFAULT 0.0, " +
                "total_spent REAL DEFAULT 0.0, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ");";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException ignored) {}
    }

    public int saveCustomer(Customer customer) throws SQLException {
        if (customer == null) throw new IllegalArgumentException("Customer cannot be null");

        String sql;
        if (customer.getCustomerId() > 0) {
            sql = "UPDATE customers SET name = ?, phone = ?, email = ?, loyalty_points = ?, " +
                    "store_credit = ?, total_spent = ? WHERE customer_id = ?;";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, customer.getName());
                pstmt.setString(2, customer.getPhone());
                pstmt.setString(3, customer.getEmail());
                pstmt.setInt(4, customer.getLoyaltyPoints());
                pstmt.setDouble(5, customer.getStoreCredit());
                pstmt.setDouble(6, customer.getTotalSpent());
                pstmt.setInt(7, customer.getCustomerId());
                pstmt.executeUpdate();
                return customer.getCustomerId();
            }
        } else {
            sql = "INSERT INTO customers (name, phone, email, loyalty_points, store_credit, total_spent) " +
                    "VALUES (?, ?, ?, ?, ?, ?);";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, customer.getName());
                pstmt.setString(2, customer.getPhone());
                pstmt.setString(3, customer.getEmail());
                pstmt.setInt(4, customer.getLoyaltyPoints());
                pstmt.setDouble(5, customer.getStoreCredit());
                pstmt.setDouble(6, customer.getTotalSpent());
                pstmt.executeUpdate();

                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        customer.setCustomerId(id);
                        return id;
                    }
                }
            }
        }
        return 0;
    }

    public List<Customer> getAllCustomers() throws SQLException {
        List<Customer> list = new ArrayList<>();
        String sql = "SELECT * FROM customers ORDER BY name ASC;";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapCustomer(rs));
            }
        }
        return list;
    }

    public Optional<Customer> findByPhone(String phone) throws SQLException {
        if (phone == null || phone.trim().isEmpty()) return Optional.empty();
        String sql = "SELECT * FROM customers WHERE phone = ? COLLATE NOCASE;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, phone.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapCustomer(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<Customer> searchByNameOrPhone(String query) throws SQLException {
        List<Customer> list = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) return getAllCustomers();

        String q = "%" + query.trim() + "%";
        String sql = "SELECT * FROM customers WHERE name LIKE ? OR phone LIKE ? ORDER BY name ASC;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, q);
            pstmt.setString(2, q);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapCustomer(rs));
                }
            }
        }
        return list;
    }

    public void addLoyaltyPoints(int customerId, int points) throws SQLException {
        String sql = "UPDATE customers SET loyalty_points = loyalty_points + ? WHERE customer_id = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, points);
            pstmt.setInt(2, customerId);
            pstmt.executeUpdate();
        }
    }

    public void updateSpend(int customerId, double amount) throws SQLException {
        String sql = "UPDATE customers SET total_spent = total_spent + ? WHERE customer_id = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, amount);
            pstmt.setInt(2, customerId);
            pstmt.executeUpdate();
        }
    }

    private Customer mapCustomer(ResultSet rs) throws SQLException {
        return new Customer(
                rs.getInt("customer_id"),
                rs.getString("name"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getInt("loyalty_points"),
                rs.getDouble("store_credit"),
                rs.getDouble("total_spent")
        );
    }
}
