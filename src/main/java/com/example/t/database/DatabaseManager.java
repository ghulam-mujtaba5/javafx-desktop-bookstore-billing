package com.example.t.database;

import com.example.t.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite Connection Pool and AppData Directory Resolver for Bookstore POS.
 * Enforces WAL journal mode, foreign key constraint verification, and automatic schema migrations.
 */
public class DatabaseManager {

    private static final String APP_DIR_NAME = "BookstoreBilling";
    private static final String DB_FILE_NAME = "bookstore.db";

    private static Path customDatabasePath = null;
    private static String customJdbcUrl = null;
    private static Connection sharedConnection = null;

    /**
     * Resolves the authoritative application data directory.
     * Hierarchy:
     * 1. System property: bookstore.data.dir
     * 2. Windows %LOCALAPPDATA%\BookstoreBilling\data
     * 3. Windows %APPDATA%\BookstoreBilling\data
     * 4. User home ~/.bookstorebilling/data
     * 5. Fallback ./data
     */
    public static Path getDataDirectory() {
        // 1. Explicit JVM Property Override (essential for testing)
        String override = System.getProperty("bookstore.data.dir");
        if (override != null && !override.trim().isEmpty()) {
            return ensureDirectory(Paths.get(override.trim()));
        }

        // 2. Windows %LOCALAPPDATA%
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.trim().isEmpty()) {
            return ensureDirectory(Paths.get(localAppData.trim(), APP_DIR_NAME, "data"));
        }

        // 3. Fallback to Windows %APPDATA%
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.trim().isEmpty()) {
            return ensureDirectory(Paths.get(appData.trim(), APP_DIR_NAME, "data"));
        }

        // 4. Fallback to user home
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.trim().isEmpty()) {
            return ensureDirectory(Paths.get(userHome.trim(), "." + APP_DIR_NAME.toLowerCase(), "data"));
        }

        // 5. Final fallback to local working directory
        return ensureDirectory(Paths.get(".", "data"));
    }

    /**
     * Returns the full path to the SQLite database file.
     */
    public static Path getDatabasePath() {
        if (customDatabasePath != null) {
            return customDatabasePath;
        }
        String pathOverride = System.getProperty("bookstore.db.path");
        if (pathOverride != null && !pathOverride.trim().isEmpty()) {
            return Paths.get(pathOverride.trim());
        }
        return getDataDirectory().resolve(DB_FILE_NAME);
    }

    /**
     * Returns the exports directory for CSV and report exports.
     */
    public static Path getExportsDirectory() {
        return ensureDirectory(getDataDirectory().resolve("exports"));
    }

    /**
     * Returns the receipts directory for PDF receipts.
     */
    public static Path getReceiptsDirectory() {
        return ensureDirectory(getDataDirectory().resolve("receipts"));
    }

    /**
     * Returns the covers directory for cached book cover images.
     */
    public static Path getCoversDirectory() {
        return ensureDirectory(getDataDirectory().resolve("covers"));
    }

    /**
     * Configures a custom database path for isolated test execution.
     */
    public static synchronized void setCustomDatabasePath(Path path) {
        close();
        customDatabasePath = path;
        customJdbcUrl = null;
    }

    /**
     * Configures an in-memory or custom JDBC URL for testing.
     */
    public static synchronized void setCustomJdbcUrl(String url) {
        close();
        customJdbcUrl = url;
        customDatabasePath = null;
    }

    /**
     * Resets database paths back to default production resolution.
     */
    public static synchronized void resetToDefault() {
        close();
        customDatabasePath = null;
        customJdbcUrl = null;
    }

    public static synchronized void resetDatabasePath() {
        resetToDefault();
    }


    /**
     * Returns an active SQLite Connection configured with foreign keys and WAL mode.
     */
    public static Connection getConnection() throws SQLException {
        String url;
        boolean isMemory = false;

        if (customJdbcUrl != null) {
            url = customJdbcUrl;
            isMemory = url.contains(":memory:");
        } else {
            Path dbPath = getDatabasePath();
            ensureDirectory(dbPath.getParent());
            url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        }

        Connection conn = DriverManager.getConnection(url);
        configurePragmas(conn, isMemory);
        return conn;
    }

    private static void configurePragmas(Connection conn, boolean isMemory) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
            if (!isMemory) {
                // WAL mode persists on disk-based databases
                stmt.execute("PRAGMA journal_mode = WAL;");
            }
        }
    }

    /**
     * Initializes the database schema, seeds standard catalog items,
     * and triggers automatic legacy flat-file migration if bookstore.db is newly created.
     */
    public static synchronized void initializeDatabase() {
        try (Connection conn = getConnection()) {
            boolean newlyMigrated = SchemaMigrationManager.migrate(conn);
            if (newlyMigrated) {
                Logger.info("Database newly initialized. Checking for legacy flat files to import...");
                LegacyDataImporter.importIfLegacyExists(conn);
            }
        } catch (SQLException e) {
            Logger.error("Failed to initialize database: " + e.getMessage());
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    public static synchronized void close() {
        if (sharedConnection != null) {
            try {
                if (!sharedConnection.isClosed()) {
                    sharedConnection.close();
                }
            } catch (SQLException ignored) {}
            sharedConnection = null;
        }
    }

    private static Path ensureDirectory(Path dir) {
        if (dir == null) return Paths.get(".");
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            return dir;
        } catch (IOException e) {
            Logger.warn("Failed to create directory: " + dir + " - " + e.getMessage());
            return dir;
        }
    }
}
