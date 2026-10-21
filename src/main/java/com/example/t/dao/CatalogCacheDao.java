package com.example.t.dao;

import com.example.t.database.DatabaseManager;
import com.example.t.model.BookMetadata;
import com.example.t.service.isbn.IsbnValidator;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for local bibliographic catalog cache (catalog_cache table).
 * Enables instant offline lookups and eliminates repetitive API queries.
 */
public class CatalogCacheDao {

    public void save(BookMetadata metadata) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            save(metadata, conn);
        }
    }

    public void save(BookMetadata metadata, Connection conn) throws SQLException {
        if (metadata == null || metadata.getIsbn13() == null || metadata.getIsbn13().trim().isEmpty()) {
            throw new IllegalArgumentException("Cannot cache BookMetadata with null or empty isbn13");
        }

        String sql = "INSERT OR REPLACE INTO catalog_cache (" +
                "isbn13, isbn10, title, subtitle, authors, publisher, published_year, edition, " +
                "format, genre, description, page_count, cover_image_path, cover_url, source, cached_at, raw_json" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP), ?);";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, metadata.getIsbn13().trim());
            pstmt.setString(2, metadata.getIsbn10() != null ? metadata.getIsbn10().trim().toUpperCase() : null);
            pstmt.setString(3, metadata.getTitle() != null ? metadata.getTitle() : "Unknown Title");
            pstmt.setString(4, metadata.getSubtitle());
            pstmt.setString(5, metadata.getAuthors());
            pstmt.setString(6, metadata.getPublisher());
            pstmt.setString(7, metadata.getPublishedYear());
            pstmt.setString(8, metadata.getEdition());
            pstmt.setString(9, metadata.getFormat());
            pstmt.setString(10, metadata.getGenre());
            pstmt.setString(11, metadata.getDescription());
            pstmt.setInt(12, metadata.getPageCount());
            pstmt.setString(13, metadata.getCoverImagePath());
            pstmt.setString(14, metadata.getCoverUrl());
            pstmt.setString(15, metadata.getSource() != null ? metadata.getSource() : "LOCAL");
            if (metadata.getCachedAt() != null) {
                pstmt.setTimestamp(16, metadata.getCachedAt());
            } else {
                pstmt.setTimestamp(16, new Timestamp(System.currentTimeMillis()));
            }
            pstmt.setString(17, metadata.getRawJson());

            pstmt.executeUpdate();
        }
    }

    public Optional<BookMetadata> findByIsbn(String rawIsbn) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return findByIsbn(rawIsbn, conn);
        }
    }

    public Optional<BookMetadata> findByIsbn(String rawIsbn, Connection conn) throws SQLException {
        if (rawIsbn == null || rawIsbn.trim().isEmpty()) {
            return Optional.empty();
        }

        String clean = IsbnValidator.clean(rawIsbn);
        String sql = "SELECT * FROM catalog_cache WHERE " +
                "isbn13 = ? OR isbn10 = ? COLLATE NOCASE OR " +
                "REPLACE(isbn13, '-', '') = ? OR REPLACE(isbn10, '-', '') = ? COLLATE NOCASE " +
                "LIMIT 1;";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, rawIsbn.trim());
            pstmt.setString(2, rawIsbn.trim().toUpperCase());
            pstmt.setString(3, clean);
            pstmt.setString(4, clean);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public boolean delete(String isbn) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return delete(isbn, conn);
        }
    }

    public boolean delete(String isbn, Connection conn) throws SQLException {
        if (isbn == null || isbn.trim().isEmpty()) {
            return false;
        }

        String clean = IsbnValidator.clean(isbn);
        String sql = "DELETE FROM catalog_cache WHERE " +
                "isbn13 = ? OR isbn10 = ? COLLATE NOCASE OR " +
                "REPLACE(isbn13, '-', '') = ? OR REPLACE(isbn10, '-', '') = ? COLLATE NOCASE;";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, isbn.trim());
            pstmt.setString(2, isbn.trim().toUpperCase());
            pstmt.setString(3, clean);
            pstmt.setString(4, clean);
            return pstmt.executeUpdate() > 0;
        }
    }

    public List<BookMetadata> getAll() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return getAll(conn);
        }
    }

    public List<BookMetadata> getAll(Connection conn) throws SQLException {
        List<BookMetadata> list = new ArrayList<>();
        String sql = "SELECT * FROM catalog_cache ORDER BY cached_at DESC;";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public int getCount() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            return getCount(conn);
        }
    }

    public int getCount(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM catalog_cache;";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    public void clear() throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            clear(conn);
        }
    }

    public void clear(Connection conn) throws SQLException {
        String sql = "DELETE FROM catalog_cache;";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    private BookMetadata mapRow(ResultSet rs) throws SQLException {
        BookMetadata m = new BookMetadata();
        m.setIsbn13(rs.getString("isbn13"));
        m.setIsbn10(rs.getString("isbn10"));
        m.setTitle(rs.getString("title"));
        m.setSubtitle(rs.getString("subtitle"));
        m.setAuthors(rs.getString("authors"));
        m.setPublisher(rs.getString("publisher"));
        m.setPublishedYear(rs.getString("published_year"));
        m.setEdition(rs.getString("edition"));
        m.setFormat(rs.getString("format"));
        m.setGenre(rs.getString("genre"));
        m.setDescription(rs.getString("description"));
        m.setPageCount(rs.getInt("page_count"));
        m.setCoverImagePath(rs.getString("cover_image_path"));
        m.setCoverUrl(rs.getString("cover_url"));
        m.setSource(rs.getString("source"));
        m.setCachedAt(rs.getTimestamp("cached_at"));
        m.setRawJson(rs.getString("raw_json"));
        return m;
    }
}
