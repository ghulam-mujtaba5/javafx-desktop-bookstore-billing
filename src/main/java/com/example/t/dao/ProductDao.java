package com.example.t.dao;

import com.example.t.Product;
import com.example.t.database.DatabaseManager;
import com.example.t.service.isbn.IsbnValidator;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Product catalog and stock inventory.
 */
public class ProductDao {

    public List<Product> getAllProducts() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return getAllProducts(conn);
        }
    }

    public List<Product> getAllProducts(Connection conn) throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = "SELECT p.*, c.name AS category_name FROM products p " +
                "LEFT JOIN categories c ON p.category_id = c.category_id " +
                "ORDER BY p.product_id ASC;";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public Optional<Product> findById(int id) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findById(id, conn);
        }
    }

    public Optional<Product> findById(int id, Connection conn) throws SQLException {
        String sql = "SELECT p.*, c.name AS category_name FROM products p " +
                "LEFT JOIN categories c ON p.category_id = c.category_id " +
                "WHERE p.product_id = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Product> findBySku(String sku) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findBySku(sku, conn);
        }
    }

    public Optional<Product> findBySku(String sku, Connection conn) throws SQLException {
        String sql = "SELECT p.*, c.name AS category_name FROM products p " +
                "LEFT JOIN categories c ON p.category_id = c.category_id " +
                "WHERE p.sku = ? COLLATE NOCASE;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sku);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Product> findBySkuOrIsbn(String query) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findBySkuOrIsbn(query, conn);
        }
    }

    public Optional<Product> findBySkuOrIsbn(String query, Connection conn) throws SQLException {
        if (query == null || query.trim().isEmpty()) {
            return Optional.empty();
        }
        String trimmed = query.trim();

        // 1. Direct match by exact SKU or exact non-empty ISBN
        String exactSql = "SELECT p.*, c.name AS category_name FROM products p " +
                "LEFT JOIN categories c ON p.category_id = c.category_id " +
                "WHERE p.sku = ? COLLATE NOCASE " +
                "   OR (p.isbn IS NOT NULL AND p.isbn != '' AND p.isbn = ? COLLATE NOCASE) " +
                "LIMIT 1;";
        try (PreparedStatement pstmt = conn.prepareStatement(exactSql)) {
            pstmt.setString(1, trimmed);
            pstmt.setString(2, trimmed);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }

        // 2. Normalized ISBN match (only if query is a valid ISBN-10 or ISBN-13)
        if (IsbnValidator.isValid(trimmed)) {
            String clean = IsbnValidator.clean(trimmed);
            String isbnSql = "SELECT p.*, c.name AS category_name FROM products p " +
                    "LEFT JOIN categories c ON p.category_id = c.category_id " +
                    "WHERE p.isbn IS NOT NULL AND p.isbn != '' " +
                    "  AND REPLACE(p.isbn, '-', '') = ? COLLATE NOCASE " +
                    "LIMIT 1;";
            try (PreparedStatement pstmt = conn.prepareStatement(isbnSql)) {
                pstmt.setString(1, clean);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                }
            }
        }

        return Optional.empty();
    }

    public List<Product> searchByName(String query) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return searchByName(query, conn);
        }
    }

    public List<Product> searchByName(String query, Connection conn) throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = "SELECT p.*, c.name AS category_name FROM products p " +
                "LEFT JOIN categories c ON p.category_id = c.category_id " +
                "WHERE p.name LIKE ? OR p.sku LIKE ? OR p.author LIKE ? " +
                "ORDER BY p.name ASC;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            String wildcard = "%" + (query != null ? query.trim() : "") + "%";
            pstmt.setString(1, wildcard);
            pstmt.setString(2, wildcard);
            pstmt.setString(3, wildcard);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public List<Product> findByCategoryId(int categoryId) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findByCategoryId(categoryId, conn);
        }
    }

    public List<Product> findByCategoryId(int categoryId, Connection conn) throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = "SELECT p.*, c.name AS category_name FROM products p " +
                "LEFT JOIN categories c ON p.category_id = c.category_id " +
                "WHERE p.category_id = ? ORDER BY p.name ASC;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, categoryId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public int save(Product product) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return save(product, conn);
        }
    }

    public int save(Product product, Connection conn) throws SQLException {
        Integer categoryId = resolveCategoryId(conn, product.getCategory());
        String sql = "INSERT INTO products (sku, name, category_id, price, purchase_price, " +
                "stock_quantity, min_stock_threshold, author, publisher, isbn, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            String sku = product.getSku();
            if (sku == null || sku.isBlank()) {
                sku = "SKU-" + System.currentTimeMillis();
                product.setSku(sku);
            }
            pstmt.setString(1, sku);
            pstmt.setString(2, product.getProductName());
            if (categoryId != null) {
                pstmt.setInt(3, categoryId);
            } else {
                pstmt.setNull(3, Types.INTEGER);
            }
            pstmt.setDouble(4, product.getPrice());
            pstmt.setDouble(5, product.getPurchasePrice());
            pstmt.setInt(6, product.getQuantity());
            pstmt.setInt(7, product.getMinStockThreshold());
            pstmt.setString(8, product.getAuthor());
            pstmt.setString(9, product.getPublisher());
            pstmt.setString(10, product.getEdition()); // Stored in isbn column
            pstmt.setString(11, product.getStatus() ? "ACTIVE" : "INACTIVE");
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    product.setProductId(id);
                    return id;
                }
            }
        }
        return -1;
    }

    public boolean update(Product product) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return update(product, conn);
        }
    }

    public boolean update(Product product, Connection conn) throws SQLException {
        Integer categoryId = resolveCategoryId(conn, product.getCategory());
        String sql = "UPDATE products SET name = ?, price = ?, purchase_price = ?, stock_quantity = ?, " +
                "min_stock_threshold = ?, category_id = ?, author = ?, publisher = ?, isbn = ?, " +
                "status = ?, sku = ?, updated_at = CURRENT_TIMESTAMP WHERE product_id = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, product.getProductName());
            pstmt.setDouble(2, product.getPrice());
            pstmt.setDouble(3, product.getPurchasePrice());
            pstmt.setInt(4, product.getQuantity());
            pstmt.setInt(5, product.getMinStockThreshold());
            if (categoryId != null) {
                pstmt.setInt(6, categoryId);
            } else {
                pstmt.setNull(6, Types.INTEGER);
            }
            pstmt.setString(7, product.getAuthor());
            pstmt.setString(8, product.getPublisher());
            pstmt.setString(9, product.getEdition());
            pstmt.setString(10, product.getStatus() ? "ACTIVE" : "INACTIVE");
            pstmt.setString(11, product.getSku());
            pstmt.setInt(12, product.getProductId());
            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean delete(int productId) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return delete(productId, conn);
        }
    }

    public boolean delete(int productId, Connection conn) throws SQLException {
        String sql = "DELETE FROM products WHERE product_id = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, productId);
            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean decrementStock(int productId, int quantity, Connection conn) throws SQLException {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to decrement must be positive: " + quantity);
        }
        String sql = "UPDATE products SET stock_quantity = stock_quantity - ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE product_id = ? AND stock_quantity >= ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, quantity);
            pstmt.setInt(2, productId);
            pstmt.setInt(3, quantity);
            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean decrementStock(int productId, int quantity) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return decrementStock(productId, quantity, conn);
        }
    }

    public boolean restockProduct(int productId, int quantity) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return restockProduct(productId, quantity, conn);
        }
    }

    public boolean restockProduct(int productId, int quantity, Connection conn) throws SQLException {
        if (conn == null) {
            return restockProduct(productId, quantity);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to restock must be positive: " + quantity);
        }
        String sql = "UPDATE products SET stock_quantity = stock_quantity + ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE product_id = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, quantity);
            pstmt.setInt(2, productId);
            return pstmt.executeUpdate() > 0;
        }
    }

    public int getStock(int productId) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            String sql = "SELECT stock_quantity FROM products WHERE product_id = ?;";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, productId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        return -1;
    }

    private Product mapRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("product_id");
        String name = rs.getString("name");
        String cat = rs.getString("category_name");
        if (cat == null) cat = "General";
        String sku = rs.getString("sku");
        String author = rs.getString("author");
        String publisher = rs.getString("publisher");
        String edition = rs.getString("isbn");
        double purchasePrice = rs.getDouble("purchase_price");
        double price = rs.getDouble("price");
        int quantity = rs.getInt("stock_quantity");
        int minStock = rs.getInt("min_stock_threshold");
        String statusStr = rs.getString("status");
        boolean status = !"INACTIVE".equalsIgnoreCase(statusStr);

        Product p = new Product(id, name, cat, sku, author, publisher, edition,
                purchasePrice, price, quantity, minStock);
        p.setStatus(status);
        return p;
    }

    private Integer resolveCategoryId(Connection conn, String catName) {
        if (catName == null || catName.isBlank()) return null;
        try {
            String sql = "SELECT category_id FROM categories WHERE name = ? COLLATE NOCASE;";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, catName.trim());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            // Auto create if does not exist
            String insertSql = "INSERT INTO categories (name, description) VALUES (?, ?);";
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, catName.trim());
                pstmt.setString(2, "Auto-created category");
                pstmt.executeUpdate();
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } catch (SQLException ignored) {}
        return null;
    }
}
