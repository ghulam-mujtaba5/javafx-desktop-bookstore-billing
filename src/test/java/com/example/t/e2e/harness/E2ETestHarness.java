package com.example.t.e2e.harness;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;

/**
 * Master Test Harness for End-to-End Test Execution.
 * Manages hermetic sandboxes, temporary SQLite database lifecycle,
 * standard catalog seeding, and isolated filesystem teardown.
 */
public class E2ETestHarness implements AutoCloseable {

    private final Path tempDir;
    private final Path dbFile;
    private final SqliteTestDatabase database;

    private int bookCategoryId;
    private int stationeryCategoryId;

    private int cleanCodeId;
    private int java21Id;
    private int orwellId;
    private int notebookId;
    private int penId;
    private int stickyNotesId;

    public E2ETestHarness() throws IOException, SQLException {
        this.tempDir = Files.createTempDirectory("bookstore_e2e_");
        this.dbFile = tempDir.resolve("bookstore_test.db");
        this.database = new SqliteTestDatabase(dbFile);
        seedCatalog();
    }

    public Path getTempDir() {
        return tempDir;
    }

    public Path getDbFile() {
        return dbFile;
    }

    public SqliteTestDatabase getDatabase() {
        return database;
    }

    public int getBookCategoryId() { return bookCategoryId; }
    public int getStationeryCategoryId() { return stationeryCategoryId; }

    public int getCleanCodeId() { return cleanCodeId; }
    public int getJava21Id() { return java21Id; }
    public int getOrwellId() { return orwellId; }
    public int getNotebookId() { return notebookId; }
    public int getPenId() { return penId; }
    public int getStickyNotesId() { return stickyNotesId; }

    private void seedCatalog() throws SQLException {
        // 1. Categories
        bookCategoryId = database.insertCategory("Books", "Printed books and literature");
        stationeryCategoryId = database.insertCategory("Stationery", "Office and school supplies");

        // 2. Books
        cleanCodeId = database.insertProduct("BK-001", "Clean Code: A Handbook of Agile Craftsmanship",
                bookCategoryId, 35.00, 22.00, 50, 5, "Robert C. Martin", "Prentice Hall", "978-0132350884");

        java21Id = database.insertProduct("BK-002", "Modern Java in Action (JDK 21 Edition)",
                bookCategoryId, 45.00, 28.00, 30, 5, "Raoul-Gabriel Urma", "Manning", "978-1617293566");

        orwellId = database.insertProduct("BK-003", "1984",
                bookCategoryId, 15.00, 8.50, 10, 5, "George Orwell", "Secker & Warburg", "978-0451524935");

        // 3. Stationery
        notebookId = database.insertProduct("ST-001", "A5 Spiral Hardcover Notebook",
                stationeryCategoryId, 4.50, 2.10, 100, 10, null, "Oxford Stationery", null);

        penId = database.insertProduct("ST-002", "Premium Gel Ink Pen 0.5mm (Pack of 3)",
                stationeryCategoryId, 6.00, 3.20, 75, 10, null, "Pilot Supplies", null);

        stickyNotesId = database.insertProduct("ST-003", "Yellow Sticky Notes Pad 3x3",
                stationeryCategoryId, 2.50, 1.00, 4, 5, null, "PostIt Stationery", null);
    }

    @Override
    public void close() throws Exception {
        if (database != null) {
            database.close();
        }
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
