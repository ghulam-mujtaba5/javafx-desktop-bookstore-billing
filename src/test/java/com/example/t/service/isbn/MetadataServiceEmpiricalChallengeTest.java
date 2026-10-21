package com.example.t.service.isbn;

import com.example.t.Product;
import com.example.t.controller.PosBillingController;
import com.example.t.dao.CatalogCacheDao;
import com.example.t.dao.InvoiceDao;
import com.example.t.dao.ProductDao;
import com.example.t.dao.SettingsDao;
import com.example.t.database.DatabaseManager;
import com.example.t.model.BookMetadata;
import com.example.t.service.PdfInvoiceService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.*;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Challenge Test Harness for Milestone 1.1-B:
 * MetadataService, Multi-Tier Caching, Network/Offline Resilience, Async Concurrency, and UI Search Integration.
 */
@DisplayName("Milestone 1.1-B: MetadataService & UI Concurrency Empirical Challenge Suite")
public class MetadataServiceEmpiricalChallengeTest {

    private static Path tempDir;
    private CatalogCacheDao cacheDao;
    private ProductDao productDao;
    private InvoiceDao invoiceDao;
    private SettingsDao settingsDao;
    private MetadataService service;

    @BeforeAll
    public static void initPlatform() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized in another test suite
        }
    }

    @BeforeEach
    public void setUp() throws IOException, SQLException {
        tempDir = Files.createTempDirectory("m1b_challenge_test_");
        Path dbPath = tempDir.resolve("m1b_challenge.db");
        DatabaseManager.setCustomDatabasePath(dbPath);
        DatabaseManager.initializeDatabase();

        cacheDao = new CatalogCacheDao();
        productDao = new ProductDao();
        invoiceDao = new InvoiceDao();
        settingsDao = new SettingsDao();
        service = new MetadataService();
    }

    @AfterEach
    public void tearDown() {
        if (service != null) {
            service.shutdown();
        }
        DatabaseManager.close();
        DatabaseManager.setCustomDatabasePath(null);
    }

    // =========================================================================
    // 1. MULTI-TIER CACHING CONCURRENCY & INTEGRITY STRESS
    // =========================================================================

    @Test
    @DisplayName("Challenge 1A: 100 concurrent threads hitting L1 cache simultaneously with dual-indexed queries")
    public void testL1ConcurrentCacheStress() throws Exception {
        // Pre-populate L1 with multiple distinct titles
        int bookCount = 20;
        List<BookMetadata> testBooks = new ArrayList<>();
        for (int i = 0; i < bookCount; i++) {
            BookMetadata b = new BookMetadata();
            // Generate valid ISBN-10 and ISBN-13
            // Base ISBN-10: 0132350882
            String isbn13 = "978013235088" + (i % 10);
            if (!IsbnValidator.isValidIsbn13(isbn13)) {
                isbn13 = "9780132350884"; // valid fallback
            }
            b.setIsbn13(isbn13);
            b.setIsbn10("0132350882");
            b.setTitle("Concurrent Book " + i);
            b.setAuthors("Author " + i);
            b.setPublisher("Publisher " + i);
            service.persistAndCache(b);
            testBooks.add(b);
        }

        int threadCount = 100;
        int operationsPerThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    startLatch.await();
                    for (int op = 0; op < operationsPerThread; op++) {
                        // Vary query formats: clean ISBN-13, hyphenated ISBN-13, ISBN-10, hyphenated ISBN-10, lowercase x
                        BookMetadata target = testBooks.get((threadId + op) % testBooks.size());
                        String query;
                        int mod = op % 5;
                        if (mod == 0) {
                            query = target.getIsbn13();
                        } else if (mod == 1) {
                            query = "978-0-13-235088-4";
                        } else if (mod == 2) {
                            query = "0132350882";
                        } else if (mod == 3) {
                            query = "0-13-235088-2";
                        } else {
                            query = "ISBN: 978-0-13-235088-4";
                        }

                        Optional<BookMetadata> result = service.lookupSync(query);
                        if (result.isPresent()) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (Throwable th) {
                    errors.add(th);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(completed, "All 100 concurrent threads should complete within deadline");
        assertTrue(errors.isEmpty(), "Zero exceptions allowed during concurrent L1 stress. Errors: " + errors);
        assertEquals(threadCount * operationsPerThread, successCount.get(), "Every concurrent query should hit cache");
    }

    @Test
    @DisplayName("Challenge 1B: L2 SQLite cache persistence and retrieval using hyphenated, clean, ISBN-10 with 'x'/'X', and 979 ISBN-13")
    public void testL2SqlitePersistenceAndRetrievalMatrix() throws SQLException {
        // Record 1: Check digit 'X'
        BookMetadata metaX = new BookMetadata();
        metaX.setIsbn13("9780804429573");
        metaX.setIsbn10("080442957X");
        metaX.setTitle("Check Digit X Title");
        metaX.setAuthors("X Author");
        metaX.setPublisher("X Publisher");
        metaX.setPublishedYear("1980");
        cacheDao.save(metaX);

        // Record 2: Clean standard ISBN-13
        BookMetadata metaClean = new BookMetadata();
        metaClean.setIsbn13("9780201633610");
        metaClean.setIsbn10("0201633612");
        metaClean.setTitle("Design Patterns");
        metaClean.setAuthors("Erich Gamma");
        metaClean.setPublisher("Addison-Wesley");
        metaClean.setPublishedYear("1994");
        cacheDao.save(metaClean);

        // Record 3: ISBN-13 with 979 prefix (valid French publication, has NO ISBN-10 equivalent)
        BookMetadata meta979 = new BookMetadata();
        meta979.setIsbn13("9791090636071");
        meta979.setIsbn10(null);
        meta979.setTitle("French 979 Novel");
        meta979.setAuthors("French Author");
        meta979.setPublisher("Editions 979");
        meta979.setPublishedYear("2015");
        cacheDao.save(meta979);

        // Clear L1 memory cache completely to ensure all lookups hit L2 SQLite
        service.clearMemoryCache();
        assertEquals(0, service.getMemoryCacheSize());

        // 1. Query metaX using lowercase 'x' with hyphens: "0-8044-2957-x"
        Optional<BookMetadata> resXLower = service.lookupSync("0-8044-2957-x");
        assertTrue(resXLower.isPresent(), "L2 lookup with lowercase 'x' and hyphens must succeed");
        assertEquals("Check Digit X Title", resXLower.get().getTitle());

        // Clear L1 again
        service.clearMemoryCache();

        // 2. Query metaX using uppercase 'X' clean: "080442957X"
        Optional<BookMetadata> resXUpper = service.lookupSync("080442957X");
        assertTrue(resXUpper.isPresent(), "L2 lookup with uppercase 'X' must succeed");
        assertEquals("Check Digit X Title", resXUpper.get().getTitle());

        // Clear L1 again
        service.clearMemoryCache();

        // 3. Query metaClean using hyphenated ISBN-13: "978-0-201-63361-0"
        Optional<BookMetadata> resHyphen13 = service.lookupSync("978-0-201-63361-0");
        assertTrue(resHyphen13.isPresent(), "L2 lookup with hyphenated ISBN-13 must succeed");
        assertEquals("Design Patterns", resHyphen13.get().getTitle());

        // 4. Query metaClean using clean ISBN-13: "9780201633610"
        Optional<BookMetadata> resClean13 = service.lookupSync("9780201633610");
        assertTrue(resClean13.isPresent(), "L2 lookup with clean ISBN-13 must succeed");

        // 5. Query metaClean using clean ISBN-10: "0201633612"
        Optional<BookMetadata> resClean10 = service.lookupSync("0201633612");
        assertTrue(resClean10.isPresent(), "L2 lookup with clean ISBN-10 must succeed");

        // 6. Query metaClean using prefixed string: "ISBN: 978-0-201-63361-0"
        Optional<BookMetadata> resPrefix = service.lookupSync("ISBN: 978-0-201-63361-0");
        assertTrue(resPrefix.isPresent(), "L2 lookup with prefix 'ISBN:' must succeed");

        // Clear L1 again
        service.clearMemoryCache();

        // 7. Query meta979 with clean 979 ISBN-13: "9791090636071"
        Optional<BookMetadata> res979Clean = service.lookupSync("9791090636071");
        assertTrue(res979Clean.isPresent(), "L2 lookup with 979 ISBN-13 must succeed without NPE");
        assertEquals("French 979 Novel", res979Clean.get().getTitle());

        // 8. Query meta979 with hyphenated 979: "979-10-90636-07-1"
        Optional<BookMetadata> res979Hyphen = service.lookupSync("979-10-90636-07-1");
        assertTrue(res979Hyphen.isPresent(), "L2 lookup with hyphenated 979 must succeed");

        // Verify L1 cache was populated by the lookups
        assertTrue(service.getMemoryCacheSize() > 0, "L1 memory cache must be repopulated after L2 hits");
    }

    // =========================================================================
    // 2. NETWORK TIMEOUT, OFFLINE RESILIENCE & ASYNC CONCURRENCY
    // =========================================================================

    @Test
    @DisplayName("Challenge 2A: Offline simulation returns Optional.empty() in <50ms without exceptions or UI lockup")
    public void testOfflineSimulationSpeedAndSafety() {
        // Mock client that immediately throws ConnectException / IOException
        HttpClient offlineClient = new AdversarialMockHttpClient(new IOException("Connection refused: connect"));
        MetadataService offlineService = new MetadataService(offlineClient, cacheDao, Executors.newFixedThreadPool(2));

        // Test with 10 iterations to verify consistent <50ms response
        for (int i = 0; i < 10; i++) {
            long startNanos = System.nanoTime();
            Optional<BookMetadata> res = offlineService.lookupSync("978-0-13-235088-4");
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

            assertFalse(res.isPresent(), "Offline query must return Optional.empty()");
            assertTrue(elapsedMillis < 50, "Offline resolution must complete in <50ms. Actual: " + elapsedMillis + "ms");
        }

        offlineService.shutdown();
    }

    @Test
    @DisplayName("Challenge 2B: Network timeout simulation returns Optional.empty() gracefully without thread leak")
    public void testTimeoutSimulationResilience() {
        // Mock client throwing HttpConnectTimeoutException
        HttpClient timeoutClient = new AdversarialMockHttpClient(new HttpConnectTimeoutException("Connect timed out after 3000ms"));
        MetadataService timeoutService = new MetadataService(timeoutClient, cacheDao, Executors.newSingleThreadExecutor());

        long start = System.currentTimeMillis();
        Optional<BookMetadata> res = timeoutService.lookupSync("978-0-13-235088-4");
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(res.isPresent(), "Timed out query must return Optional.empty()");
        assertTrue(elapsed < 100, "Mock timeout must complete promptly without thread hang");

        timeoutService.shutdown();
    }

    @Test
    @DisplayName("Challenge 2C: 50 concurrent lookupAsync calls with mixed offline failures, invalid ISBNs, and cache hits")
    public void test50ConcurrentAsyncLookupsWithoutThreadExhaustion() throws Exception {
        // Construct service with mock client simulating offline responses (<1ms)
        HttpClient offlineClient = new AdversarialMockHttpClient(new IOException("Simulated network down / offline mode"));
        MetadataService concurrentService = new MetadataService(offlineClient, cacheDao, Executors.newFixedThreadPool(4));

        // Pre-cache one book
        BookMetadata cached = new BookMetadata();
        cached.setIsbn13("9780132350884");
        cached.setIsbn10("0132350882");
        cached.setTitle("Clean Code Cached");
        concurrentService.persistAndCache(cached);

        int taskCount = 50;
        List<CompletableFuture<Optional<BookMetadata>>> futures = new ArrayList<>(taskCount);

        for (int i = 0; i < taskCount; i++) {
            String isbnToLookup;
            if (i % 3 == 0) {
                // Cached hit
                isbnToLookup = "978-0-13-235088-4";
            } else if (i % 3 == 1) {
                // Invalid ISBN (checksum failure)
                isbnToLookup = "978-0-13-235088-9";
            } else {
                // Valid ISBN not in cache (triggers fast offline fallback)
                isbnToLookup = "978-0-201-63361-0";
            }

            // Launch asynchronously
            CompletableFuture<Optional<BookMetadata>> future = concurrentService.lookupAsync(isbnToLookup);
            futures.add(future);
        }

        // Wait for all 50 futures to complete with a strict timeout
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        assertDoesNotThrow(() -> allOf.get(5, TimeUnit.SECONDS), "All 50 async lookups must complete without deadlock");

        int foundCount = 0;
        for (int i = 0; i < taskCount; i++) {
            Optional<BookMetadata> opt = futures.get(i).get();
            if (i % 3 == 0) {
                assertTrue(opt.isPresent(), "Task " + i + " was cached and should be present");
                assertEquals("Clean Code Cached", opt.get().getTitle());
                foundCount++;
            } else {
                assertFalse(opt.isPresent(), "Task " + i + " should be empty (invalid checksum or offline uncached)");
            }
        }

        assertEquals(17, foundCount, "Expected exactly 17 cached hits out of 50 tasks");
        concurrentService.shutdown();
    }

    @Test
    @DisplayName("Challenge 2D: Malformed, truncated, and corrupt JSON responses from upstream APIs return Optional.empty()")
    public void testCorruptedJsonResilience() {
        String[] corruptPayloads = new String[] {
                "",
                "   ",
                "{ not valid json at all",
                "<html><body>502 Bad Gateway</body></html>",
                "{\"kind\": \"books#volumes\", \"totalItems\": 1}", // missing items array
                "{\"kind\": \"books#volumes\", \"totalItems\": 1, \"items\": [ null ]}",
                "{\"kind\": \"books#volumes\", \"totalItems\": 1, \"items\": [ {} ]}", // missing volumeInfo
                "{\"kind\": \"books#volumes\", \"totalItems\": 1, \"items\": [ {\"volumeInfo\": null} ]}",
                "{\"kind\": \"books#volumes\", \"totalItems\": 1, \"items\": [ {\"volumeInfo\": {\"title\": \"\"}} ]}", // empty title
                "[]",
                "12345"
        };

        for (String payload : corruptPayloads) {
            Optional<BookMetadata> googleRes = service.parseGoogleBooksResponse(payload, "9780132350884", "0132350882");
            assertFalse(googleRes.isPresent(), "Google Books parser must return Optional.empty() on: " + payload);

            Optional<BookMetadata> openLibRes = service.parseOpenLibraryResponse(payload, "9780132350884", "9780132350884", "0132350882");
            assertFalse(openLibRes.isPresent(), "Open Library parser must return Optional.empty() on: " + payload);
        }
    }

    // =========================================================================
    // 3. UI SEARCH INTEGRATION & ADVERSARIAL ProductDao.findBySkuOrIsbn
    // =========================================================================

    @Test
    @DisplayName("Challenge 3A: ProductDao.findBySkuOrIsbn with blanks, single chars, wildcards, SQL injections, and blank-row safety")
    public void testProductDaoFindBySkuOrIsbnAdversarialQueries() throws SQLException {
        // Seed edge-case products in DB
        // 1. Regular book with valid ISBN
        Product pBook = new Product(0, "Java Concurrency in Practice", 10, 50.0, 30.0);
        pBook.setSku("BK-JCIP-01");
        pBook.setEdition("978-0321349606"); // stored in isbn column
        productDao.save(pBook);

        // 2. Stationery with null ISBN
        Product pPencil = new Product(0, "Stationery Pencil", 100, 1.5, 0.5);
        pPencil.setSku("ST-PENCIL-99");
        pPencil.setEdition(null);
        productDao.save(pPencil);

        // 3. Stationery with empty ISBN
        Product pEraser = new Product(0, "Stationery Eraser", 50, 0.99, 0.3);
        pEraser.setSku("ST-ERASER-88");
        pEraser.setEdition("");
        productDao.save(pEraser);

        // 4. Book with check digit X
        Product pXBook = new Product(0, "Catcher Guide", 5, 20.0, 10.0);
        pXBook.setSku("BK-X-10");
        pXBook.setEdition("080442957X");
        productDao.save(pXBook);

        // --- Adversarial Blank & Syntax Queries (Must NEVER match blank rows!) ---
        String[] blankAndSyntaxQueries = new String[] {
                null,
                "",
                " ",
                "   \t\n   ",
                "%",
                "%%",
                "_",
                "%",
                "'",
                "\"",
                "''",
                "' OR 1=1 --",
                "'; DROP TABLE products; --",
                "A",
                "B",
                "1",
                "X",
                "x",
                "BK",
                "ST",
                "978",
                "013",
                "NON-EXISTENT-SKU-9999"
        };

        for (String badQuery : blankAndSyntaxQueries) {
            Optional<Product> match = productDao.findBySkuOrIsbn(badQuery);
            assertFalse(match.isPresent(), "Adversarial query '" + badQuery + "' must NOT match any product (especially blank ISBN rows)");
        }

        // --- Legitimate Queries (Must match accurately) ---
        // 1. Exact SKU match
        Optional<Product> bySku = productDao.findBySkuOrIsbn("BK-JCIP-01");
        assertTrue(bySku.isPresent(), "Exact SKU must match");
        assertEquals("Java Concurrency in Practice", bySku.get().getProductName());

        // 2. Case-insensitive SKU match
        Optional<Product> bySkuCase = productDao.findBySkuOrIsbn("bk-jcip-01");
        assertTrue(bySkuCase.isPresent(), "Case-insensitive SKU must match");

        // 3. Stationery by SKU with null ISBN
        Optional<Product> byPencilSku = productDao.findBySkuOrIsbn("ST-PENCIL-99");
        assertTrue(byPencilSku.isPresent());
        assertEquals("Stationery Pencil", byPencilSku.get().getProductName());

        // 4. Stationery by SKU with empty ISBN
        Optional<Product> byEraserSku = productDao.findBySkuOrIsbn("ST-ERASER-88");
        assertTrue(byEraserSku.isPresent());
        assertEquals("Stationery Eraser", byEraserSku.get().getProductName());

        // 5. Valid ISBN-13 unhyphenated matching hyphenated stored ISBN
        Optional<Product> byCleanIsbn = productDao.findBySkuOrIsbn("9780321349606");
        assertTrue(byCleanIsbn.isPresent(), "Clean ISBN-13 must match hyphenated stored ISBN");
        assertEquals("Java Concurrency in Practice", byCleanIsbn.get().getProductName());

        // 6. Valid ISBN-13 hyphenated matching hyphenated stored ISBN
        Optional<Product> byHyphenIsbn = productDao.findBySkuOrIsbn("978-0-321-34960-6");
        assertTrue(byHyphenIsbn.isPresent(), "Hyphenated ISBN-13 must match stored ISBN");

        // 7. Valid ISBN-10 with check digit 'X'
        Optional<Product> byUpperX = productDao.findBySkuOrIsbn("080442957X");
        assertTrue(byUpperX.isPresent(), "Uppercase X check digit must match");
        assertEquals("Catcher Guide", byUpperX.get().getProductName());

        // 8. Valid ISBN-10 with lowercase 'x' and hyphens
        Optional<Product> byLowerX = productDao.findBySkuOrIsbn("0-8044-2957-x");
        assertTrue(byLowerX.isPresent(), "Lowercase x check digit with hyphens must match");
        assertEquals("Catcher Guide", byLowerX.get().getProductName());
    }

    @Test
    @DisplayName("Challenge 3B: PosBillingController UI search Enter action does not add phantom cart rows on empty or invalid barcode")
    public void testPosBillingControllerSearchEnterSafety() throws Exception {
        // Seed database
        Product book = new Product(0, "Test POS Book", 20, 25.0, 15.0);
        book.setSku("BK-POS-100");
        book.setEdition("0132350882");
        productDao.save(book);

        // Load FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/PosBilling.fxml"));
        Parent root = loader.load();
        assertNotNull(root);
        PosBillingController pos = loader.getController();
        pos.setProductDao(productDao);
        pos.setInvoiceDao(invoiceDao);
        pos.setSettingsDao(settingsDao);
        pos.setPdfInvoiceService(new PdfInvoiceService(tempDir));
        pos.setCatalogCacheDao(cacheDao);

        Field searchFieldProp = PosBillingController.class.getDeclaredField("searchField");
        searchFieldProp.setAccessible(true);
        TextField searchField = (TextField) searchFieldProp.get(pos);

        // 1. Enter with empty text
        searchField.setText("");
        pos.handleSearchEnter(new ActionEvent());
        assertEquals(0, pos.getCartItems().size(), "Empty search must not add items to cart");

        // 2. Enter with spaces
        searchField.setText("    ");
        pos.handleSearchEnter(new ActionEvent());
        assertEquals(0, pos.getCartItems().size(), "Whitespace search must not add items to cart");

        // 3. Enter with SQL injection
        searchField.setText("' OR '1'='1");
        pos.handleSearchEnter(new ActionEvent());
        assertEquals(0, pos.getCartItems().size(), "SQL injection search must not add items to cart");

        // 4. Enter with single letter
        searchField.setText("Z");
        pos.handleSearchEnter(new ActionEvent());
        assertEquals(0, pos.getCartItems().size(), "Non-matching single letter must not add items to cart");

        // 5. Enter with valid ISBN-10
        searchField.setText("0-13-235088-2");
        pos.handleSearchEnter(new ActionEvent());
        assertEquals(1, pos.getCartItems().size(), "Valid ISBN search must add exactly 1 item to cart");
        assertEquals("Test POS Book", pos.getCartItems().get(0).getProductName());
    }

    // =========================================================================
    // HELPER MOCK HTTP CLIENT
    // =========================================================================

    private static class AdversarialMockHttpClient extends HttpClient {
        private final Exception exceptionToThrow;

        AdversarialMockHttpClient(Exception exceptionToThrow) {
            this.exceptionToThrow = exceptionToThrow;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseHandler) throws IOException, InterruptedException {
            if (exceptionToThrow instanceof IOException) {
                throw (IOException) exceptionToThrow;
            } else if (exceptionToThrow instanceof InterruptedException) {
                throw (InterruptedException) exceptionToThrow;
            } else if (exceptionToThrow != null) {
                throw new RuntimeException(exceptionToThrow);
            }
            return new MockHttpResponse<>(200, null);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseHandler) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return send(request, responseHandler);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            });
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseHandler);
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.of(Duration.ofSeconds(3)); }
        @Override public Redirect followRedirects() { return Redirect.NORMAL; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return null; }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_2; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
    }

    private static class MockHttpResponse<T> implements HttpResponse<T> {
        private final int statusCode;
        private final T body;

        MockHttpResponse(int statusCode, T body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override public int statusCode() { return statusCode; }
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Collections.emptyMap(), (k, v) -> true); }
        @Override public T body() { return body; }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return URI.create("http://localhost"); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_2; }
    }
}
