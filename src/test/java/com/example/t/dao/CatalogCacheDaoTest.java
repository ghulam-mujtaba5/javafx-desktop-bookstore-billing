package com.example.t.dao;

import com.example.t.database.DatabaseManager;
import com.example.t.model.BookMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 1.1-A - CatalogCacheDao Persistence Tests")
public class CatalogCacheDaoTest {

    @TempDir
    Path tempDir;

    private CatalogCacheDao cacheDao;

    @BeforeEach
    public void setUp() throws SQLException {
        Path testDb = tempDir.resolve("test_cache.db");
        DatabaseManager.setCustomDatabasePath(testDb);
        DatabaseManager.initializeDatabase();
        cacheDao = new CatalogCacheDao();
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.resetToDefault();
    }

    @Test
    @DisplayName("Verify save and findByIsbn with exact ISBN-13 and ISBN-10")
    public void testSaveAndFindByIsbn() throws SQLException {
        BookMetadata book = new BookMetadata();
        book.setIsbn13("978-0132350884");
        book.setIsbn10("0132350882");
        book.setTitle("Clean Code");
        book.setSubtitle("A Handbook of Agile Software Craftsmanship");
        book.setAuthors("Robert C. Martin");
        book.setPublisher("Prentice Hall");
        book.setPublishedYear("2008");
        book.setEdition("1st");
        book.setFormat("Paperback");
        book.setGenre("Computers / Programming");
        book.setDescription("Even bad code can function. But if code isn't clean, it can bring a development organization to its knees.");
        book.setPageCount(464);
        book.setCoverImageUrl("https://example.com/covers/cleancode.jpg");
        book.setCoverImagePath("C:\\data\\covers\\9780132350884.jpg");
        book.setSource("GOOGLE_BOOKS");
        book.setCachedAt(new Timestamp(System.currentTimeMillis()));
        book.setRawJson("{\"title\":\"Clean Code\"}");

        cacheDao.save(book);

        // Lookup with exact ISBN-13
        Optional<BookMetadata> found13 = cacheDao.findByIsbn("978-0132350884");
        assertTrue(found13.isPresent(), "Must find book by formatted ISBN-13");
        assertEquals("Clean Code", found13.get().getTitle());
        assertEquals("Robert C. Martin", found13.get().getAuthors());
        assertEquals(464, found13.get().getPageCount());

        // Lookup with unhyphenated ISBN-13
        Optional<BookMetadata> found13Raw = cacheDao.findByIsbn("9780132350884");
        assertTrue(found13Raw.isPresent(), "Must find book by unhyphenated ISBN-13");
        assertEquals("Clean Code", found13Raw.get().getTitle());

        // Lookup with ISBN-10
        Optional<BookMetadata> found10 = cacheDao.findByIsbn("0132350882");
        assertTrue(found10.isPresent(), "Must find book by ISBN-10");
        assertEquals("Clean Code", found10.get().getTitle());

        // Lookup non-existing ISBN
        Optional<BookMetadata> notFound = cacheDao.findByIsbn("978-0000000000");
        assertFalse(notFound.isPresent(), "Non-existent ISBN must return empty");
    }

    @Test
    @DisplayName("Verify update / upsert overwrites existing cache entry")
    public void testUpsertCacheEntry() throws SQLException {
        BookMetadata b1 = new BookMetadata();
        b1.setIsbn13("978-1617293566");
        b1.setTitle("Modern Java in Action");
        b1.setAuthors("Raoul-Gabriel Urma");
        cacheDao.save(b1);

        assertEquals(1, cacheDao.getCount());
        assertEquals("Modern Java in Action", cacheDao.findByIsbn("978-1617293566").get().getTitle());

        // Update title and page count
        b1.setTitle("Modern Java in Action (2nd Edition)");
        b1.setPageCount(592);
        cacheDao.save(b1);

        assertEquals(1, cacheDao.getCount(), "Count must remain 1 after upsert");
        BookMetadata updated = cacheDao.findByIsbn("978-1617293566").get();
        assertEquals("Modern Java in Action (2nd Edition)", updated.getTitle());
        assertEquals(592, updated.getPageCount());
    }

    @Test
    @DisplayName("Verify delete and getAll and clear")
    public void testDeleteAndGetAll() throws SQLException {
        BookMetadata b1 = new BookMetadata();
        b1.setIsbn13("978-1111111111");
        b1.setTitle("Book One");
        cacheDao.save(b1);

        BookMetadata b2 = new BookMetadata();
        b2.setIsbn13("978-2222222222");
        b2.setTitle("Book Two");
        cacheDao.save(b2);

        List<BookMetadata> all = cacheDao.getAll();
        assertEquals(2, all.size(), "Should have 2 cached books");

        boolean deleted = cacheDao.delete("978-1111111111");
        assertTrue(deleted, "Delete should return true for existing book");
        assertEquals(1, cacheDao.getCount(), "Count should be 1 after deletion");

        cacheDao.clear();
        assertEquals(0, cacheDao.getCount(), "Count should be 0 after clear");
    }

    @Test
    @DisplayName("CatalogCacheDao: Lookup handles uppercase X and lowercase x check digits interchangeably")
    public void testSaveAndFindByIsbnWithCheckDigitXVariations() throws SQLException {
        BookMetadata book = new BookMetadata();
        book.setIsbn13("978-0804429573");
        book.setIsbn10("080442957X");
        book.setTitle("The Catcher in the Rye Study Guide");
        book.setAuthors("J. D. Salinger");
        cacheDao.save(book);

        // 1. Exact uppercase
        Optional<BookMetadata> foundUpper = cacheDao.findByIsbn("080442957X");
        assertTrue(foundUpper.isPresent(), "Must match uppercase 'X'");
        assertEquals("The Catcher in the Rye Study Guide", foundUpper.get().getTitle());

        // 2. Lowercase 'x'
        Optional<BookMetadata> foundLower = cacheDao.findByIsbn("080442957x");
        assertTrue(foundLower.isPresent(), "Must match lowercase 'x'");

        // 3. Hyphenated uppercase
        Optional<BookMetadata> foundHyphenUpper = cacheDao.findByIsbn("0-8044-2957-X");
        assertTrue(foundHyphenUpper.isPresent(), "Must match hyphenated uppercase 'X'");

        // 4. Hyphenated lowercase
        Optional<BookMetadata> foundHyphenLower = cacheDao.findByIsbn("0-8044-2957-x");
        assertTrue(foundHyphenLower.isPresent(), "Must match hyphenated lowercase 'x'");

        // 5. Whitespace padding
        Optional<BookMetadata> foundPadded = cacheDao.findByIsbn("   080442957x   ");
        assertTrue(foundPadded.isPresent(), "Must match whitespace padded lowercase 'x'");

        // 6. Unhyphenated ISBN-13 cross-lookup
        Optional<BookMetadata> found13 = cacheDao.findByIsbn("9780804429573");
        assertTrue(found13.isPresent(), "Must match unhyphenated ISBN-13");
    }

    @Test
    @DisplayName("CatalogCacheDao: Record saved with lowercase 'x' check digit is retrievable by uppercase 'X'")
    public void testSaveWithLowercaseXNormalizesOnSave() throws SQLException {
        BookMetadata book = new BookMetadata();
        book.setIsbn13("978-0201633610");
        book.setIsbn10("020163361x"); // Saved with lowercase 'x'
        book.setTitle("Design Patterns");
        cacheDao.save(book);

        // Uppercase lookup must succeed
        Optional<BookMetadata> foundUpper = cacheDao.findByIsbn("020163361X");
        assertTrue(foundUpper.isPresent(), "Uppercase 'X' must find lowercase 'x' record");

        // Hyphenated uppercase lookup must succeed
        Optional<BookMetadata> foundHyphenUpper = cacheDao.findByIsbn("0-201-63361-X");
        assertTrue(foundHyphenUpper.isPresent(), "Hyphenated uppercase 'X' must find record");

        // Lowercase lookup must succeed
        Optional<BookMetadata> foundLower = cacheDao.findByIsbn("020163361x");
        assertTrue(foundLower.isPresent(), "Lowercase 'x' must find record");
    }

    @Test
    @DisplayName("CatalogCacheDao: Delete handles case variations and hyphens for ISBN-10 check digit")
    public void testDeleteWithCaseVariationsAndHyphens() throws SQLException {
        BookMetadata book = new BookMetadata();
        book.setIsbn13("978-0804429573");
        book.setIsbn10("080442957X");
        book.setTitle("Deletable Book");
        cacheDao.save(book);
        assertEquals(1, cacheDao.getCount());

        // Delete using hyphenated lowercase 'x'
        boolean deleted = cacheDao.delete("0-8044-2957-x");
        assertTrue(deleted, "Delete with hyphenated lowercase 'x' should return true");
        assertEquals(0, cacheDao.getCount(), "Cache count should be 0 after delete");
        assertFalse(cacheDao.findByIsbn("080442957X").isPresent(), "Record should no longer exist");
    }

    @Test
    @DisplayName("CatalogCacheDao: findByIsbn strips non-alphanumeric punctuation without regex range bug")
    public void testRegexStrippingPunctuationAndText() throws SQLException {
        BookMetadata book = new BookMetadata();
        book.setIsbn13("978-0804429573");
        book.setIsbn10("080442957X");
        book.setTitle("Punctuation Book");
        cacheDao.save(book);

        // Trailing dots, brackets, colons, slashes
        assertTrue(cacheDao.findByIsbn("080442957X.").isPresent(), "Trailing dot must be stripped");
        assertTrue(cacheDao.findByIsbn("/080442957x/").isPresent(), "Slashes must be stripped");
        assertTrue(cacheDao.findByIsbn("(0-8044-2957-x)").isPresent(), "Parentheses must be stripped");
    }
}
