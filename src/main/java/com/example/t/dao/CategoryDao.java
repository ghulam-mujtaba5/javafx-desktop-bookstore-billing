package com.example.t.dao;

import com.example.t.database.DatabaseManager;
import com.example.t.model.Category;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for inventory categories.
 */
public class CategoryDao {

    public List<Category> getAllCategories() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return getAllCategories(conn);
        }
    }

    public List<Category> getAllCategories(Connection conn) throws SQLException {
        List<Category> list = new ArrayList<>();
        String sql = "SELECT category_id, name, description FROM categories ORDER BY name ASC;";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public Optional<Category> findById(int id) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findById(id, conn);
        }
    }

    public Optional<Category> findById(int id, Connection conn) throws SQLException {
        String sql = "SELECT category_id, name, description FROM categories WHERE category_id = ?;";
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

    public Optional<Category> findByName(String name) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findByName(name, conn);
        }
    }

    public Optional<Category> findByName(String name, Connection conn) throws SQLException {
        String sql = "SELECT category_id, name, description FROM categories WHERE name = ? COLLATE NOCASE;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public int save(Category category) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return save(category, conn);
        }
    }

    public int save(Category category, Connection conn) throws SQLException {
        String sql = "INSERT INTO categories (name, description) VALUES (?, ?);";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, category.getName());
            pstmt.setString(2, category.getDescription());
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    category.setCategoryId(id);
                    return id;
                }
            }
        }
        return -1;
    }

    public boolean update(Category category) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return update(category, conn);
        }
    }

    public boolean update(Category category, Connection conn) throws SQLException {
        String sql = "UPDATE categories SET name = ?, description = ? WHERE category_id = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, category.getName());
            pstmt.setString(2, category.getDescription());
            pstmt.setInt(3, category.getCategoryId());
            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean delete(int categoryId) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return delete(categoryId, conn);
        }
    }

    public boolean delete(int categoryId, Connection conn) throws SQLException {
        String sql = "DELETE FROM categories WHERE category_id = ?;";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, categoryId);
            return pstmt.executeUpdate() > 0;
        }
    }

    private Category mapRow(ResultSet rs) throws SQLException {
        return new Category(
                rs.getInt("category_id"),
                rs.getString("name"),
                rs.getString("description")
        );
    }
}
