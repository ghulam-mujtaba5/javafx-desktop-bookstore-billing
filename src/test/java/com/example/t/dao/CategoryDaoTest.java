package com.example.t.dao;

import com.example.t.database.DatabaseManager;
import com.example.t.model.Category;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 2 - CategoryDao Unit Tests")
public class CategoryDaoTest {

    @TempDir
    Path tempDir;

    private CategoryDao categoryDao;

    @BeforeEach
    public void setUp() {
        Path testDb = tempDir.resolve("category_test.db");
        DatabaseManager.setCustomDatabasePath(testDb);
        DatabaseManager.initializeDatabase();
        categoryDao = new CategoryDao();
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.resetToDefault();
    }

    @Test
    @DisplayName("Verify Initial Seed Categories are Retrievable")
    public void testGetAllCategories() throws SQLException {
        List<Category> all = categoryDao.getAllCategories();
        assertFalse(all.isEmpty(), "Categories list should not be empty");
        assertTrue(all.stream().anyMatch(c -> c.getName().equalsIgnoreCase("Books")));
        assertTrue(all.stream().anyMatch(c -> c.getName().equalsIgnoreCase("Stationery")));
    }

    @Test
    @DisplayName("Verify Create, Read, Update, and Delete on Category")
    public void testCategoryCrud() throws SQLException {
        // Create
        Category cat = new Category("Comics & Graphic Novels", "Illustrated books");
        int id = categoryDao.save(cat);
        assertTrue(id > 0, "Generated ID must be positive");

        // Read by ID
        Optional<Category> fetched = categoryDao.findById(id);
        assertTrue(fetched.isPresent());
        assertEquals("Comics & Graphic Novels", fetched.get().getName());
        assertEquals("Illustrated books", fetched.get().getDescription());

        // Read by Name
        Optional<Category> byName = categoryDao.findByName("Comics & Graphic Novels");
        assertTrue(byName.isPresent());
        assertEquals(id, byName.get().getCategoryId());

        // Update
        cat.setDescription("Graphic novels, manga and comics");
        boolean updated = categoryDao.update(cat);
        assertTrue(updated);

        Optional<Category> updatedCat = categoryDao.findById(id);
        assertTrue(updatedCat.isPresent());
        assertEquals("Graphic novels, manga and comics", updatedCat.get().getDescription());

        // Delete
        boolean deleted = categoryDao.delete(id);
        assertTrue(deleted);
        assertFalse(categoryDao.findById(id).isPresent());
    }

    @Test
    @DisplayName("Verify Unique Constraint Violation on Duplicate Category Name")
    public void testDuplicateCategoryNameThrows() throws SQLException {
        Category first = new Category("UniqueCategory", "Description");
        categoryDao.save(first);

        Category second = new Category("UniqueCategory", "Another description");
        assertThrows(SQLException.class, () -> categoryDao.save(second));
    }
}
