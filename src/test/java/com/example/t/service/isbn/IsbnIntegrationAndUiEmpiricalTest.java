package com.example.t.service.isbn;

import com.example.t.Product;
import com.example.t.controller.PosBillingController;
import com.example.t.controller.StockManagementController;
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
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 1.1-B: ISBN Integration, Cover Download & UI Controller Empirical Tests")
public class IsbnIntegrationAndUiEmpiricalTest {

    private static Path tempDir;
    private ProductDao productDao;
    private CatalogCacheDao cacheDao;
    private InvoiceDao invoiceDao;
    private SettingsDao settingsDao;

    @BeforeAll
    public static void initPlatform() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
    }

    @BeforeEach
    public void setUp() throws IOException, SQLException {
        tempDir = Files.createTempDirectory("m1b_integration_test_");
        Path dbPath = tempDir.resolve("integration_test.db");
        DatabaseManager.setCustomDatabasePath(dbPath);
        DatabaseManager.initializeDatabase();

        productDao = new ProductDao();
        cacheDao = new CatalogCacheDao();
        invoiceDao = new InvoiceDao();
        settingsDao = new SettingsDao();
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.close();
        DatabaseManager.setCustomDatabasePath(null);
    }

    @Test
    @DisplayName("PosBillingController: Scanning or typing ISBN matches product via findBySkuOrIsbn")
    public void testPosBillingSearchByIsbn() throws Exception {
        // Seed a book with edition/isbn "0132350882"
        Product book = new Product(0, "Clean Code by Martin", 15, 35.00, 22.00);
        book.setSku("BK-MARTIN-01");
        book.setEdition("0132350882");
        productDao.save(book);

        // Load POS Billing Controller
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/PosBilling.fxml"));
        Parent root = loader.load();
        assertNotNull(root);
        PosBillingController pos = loader.getController();
        pos.setProductDao(productDao);
        pos.setInvoiceDao(invoiceDao);
        pos.setSettingsDao(settingsDao);
        pos.setPdfInvoiceService(new PdfInvoiceService(tempDir));
        pos.setCatalogCacheDao(cacheDao);

        // Search by ISBN-10
        Field searchFieldProp = PosBillingController.class.getDeclaredField("searchField");
        searchFieldProp.setAccessible(true);
        TextField searchField = (TextField) searchFieldProp.get(pos);
        searchField.setText("0132350882");

        pos.handleSearchEnter(new ActionEvent());

        assertEquals(1, pos.getCartItems().size(), "Cart should contain 1 item after ISBN-10 scan");
        assertEquals("Clean Code by Martin", pos.getCartItems().get(0).getProductName());
        assertTrue(searchField.getText().isEmpty(), "Search field should be cleared after match");

        // Search again with hyphenated ISBN-10
        searchField.setText("0-13-235088-2");
        pos.handleSearchEnter(new ActionEvent());
        assertEquals(1, pos.getCartItems().size(), "Cart should still have 1 row with incremented quantity");
        assertEquals(2, pos.getCartItems().get(0).getQuantity(), "Quantity should be incremented to 2");
    }

    @Test
    @DisplayName("ProductDao: findBySkuOrIsbn matches by SKU, exact ISBN, and unhyphenated ISBN")
    public void testProductDaoFindBySkuOrIsbn() throws SQLException {
        Product book = new Product(0, "Refactoring", 10, 45.00, 30.00);
        book.setSku("BK-REFAC-99");
        book.setEdition("978-0201485677");
        productDao.save(book);

        // 1. By SKU
        Optional<Product> bySku = productDao.findBySkuOrIsbn("BK-REFAC-99");
        assertTrue(bySku.isPresent());
        assertEquals("Refactoring", bySku.get().getProductName());

        // 2. By exact stored edition/ISBN
        Optional<Product> byExact = productDao.findBySkuOrIsbn("978-0201485677");
        assertTrue(byExact.isPresent());
        assertEquals("Refactoring", byExact.get().getProductName());

        // 3. By unhyphenated ISBN
        Optional<Product> byClean = productDao.findBySkuOrIsbn("9780201485677");
        assertTrue(byClean.isPresent());
        assertEquals("Refactoring", byClean.get().getProductName());

        // 4. Case-insensitive SKU match
        Optional<Product> bySkuCase = productDao.findBySkuOrIsbn("bk-refac-99");
        assertTrue(bySkuCase.isPresent());

        // 5. Non-existent query
        Optional<Product> nonExistent = productDao.findBySkuOrIsbn("NON-EXISTENT-SKU");
        assertFalse(nonExistent.isPresent());
    }

    @Test
    @DisplayName("Online Mock Enrichment: Google Books query parses metadata, downloads cover art, and persists to L2 SQLite cache")
    public void testOnlineEnrichmentAndCoverDownload() throws Exception {
        String googleBooksJson = "{\n" +
                "  \"kind\": \"books#volumes\",\n" +
                "  \"totalItems\": 1,\n" +
                "  \"items\": [\n" +
                "    {\n" +
                "      \"volumeInfo\": {\n" +
                "        \"title\": \"Pragmatic Programmer, The: 20th Anniversary Edition\",\n" +
                "        \"authors\": [\"David Thomas\", \"Andrew Hunt\"],\n" +
                "        \"publisher\": \"Addison-Wesley Professional\",\n" +
                "        \"publishedDate\": \"2019-09-13\",\n" +
                "        \"industryIdentifiers\": [\n" +
                "          { \"type\": \"ISBN_10\", \"identifier\": \"0135957052\" },\n" +
                "          { \"type\": \"ISBN_13\", \"identifier\": \"9780135957059\" }\n" +
                "        ],\n" +
                "        \"pageCount\": 352,\n" +
                "        \"categories\": [\"Computers / Software Development\"],\n" +
                "        \"imageLinks\": {\n" +
                "          \"thumbnail\": \"https://covers.example.com/pragmatic.jpg\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        byte[] fakeCoverBytes = new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x01, 0x02 }; // JPEG magic bytes

        TestMockHttpClient mockHttp = new TestMockHttpClient();
        mockHttp.addResponse("googleapis.com", 200, googleBooksJson);
        mockHttp.addByteResponse("covers.example.com", 200, fakeCoverBytes);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        MetadataService enrichmentService = new MetadataService(mockHttp, cacheDao, executor);

        Optional<BookMetadata> result = enrichmentService.lookupSync("978-0-13-595705-9");
        assertTrue(result.isPresent(), "Expected successful metadata resolution");
        BookMetadata meta = result.get();
        assertEquals("Pragmatic Programmer, The: 20th Anniversary Edition", meta.getTitle());
        assertEquals("David Thomas, Andrew Hunt", meta.getAuthors());
        assertEquals("Addison-Wesley Professional", meta.getPublisher());
        assertEquals("2019", meta.getPublishedYear());
        assertEquals("GOOGLE_BOOKS", meta.getSource());

        // Wait up to 2 seconds for async cover download and L2 persistence
        Path coverFile = DatabaseManager.getCoversDirectory().resolve("9780135957059.jpg");
        int retries = 20;
        while (!Files.exists(coverFile) && retries-- > 0) {
            Thread.sleep(100);
        }

        assertTrue(Files.exists(coverFile), "Cover JPEG file must be downloaded to covers directory: " + coverFile);
        assertArrayEquals(fakeCoverBytes, Files.readAllBytes(coverFile));

        // Verify L2 SQLite persistence
        Optional<BookMetadata> cachedInDb = cacheDao.findByIsbn("9780135957059");
        assertTrue(cachedInDb.isPresent(), "Must be persisted to L2 SQLite catalog_cache table");
        assertEquals("Pragmatic Programmer, The: 20th Anniversary Edition", cachedInDb.get().getTitle());

        enrichmentService.shutdown();
        executor.shutdown();
    }

    @Test
    @DisplayName("Online Mock Fallback: Open Library query succeeds when Google Books fails (404/Empty)")
    public void testOpenLibraryFallbackWhenGoogleBooksFails() {
        String openLibJson = "{\n" +
                "  \"ISBN:9780132350884\": {\n" +
                "    \"title\": \"Clean Code\",\n" +
                "    \"authors\": [\n" +
                "      { \"name\": \"Robert C. Martin\" }\n" +
                "    ],\n" +
                "    \"publishers\": [\n" +
                "      { \"name\": \"Prentice Hall\" }\n" +
                "    ],\n" +
                "    \"publish_date\": \"2008\",\n" +
                "    \"number_of_pages\": 464\n" +
                "  }\n" +
                "}";

        TestMockHttpClient mockHttp = new TestMockHttpClient();
        mockHttp.addResponse("googleapis.com", 404, "{\"totalItems\": 0}");
        mockHttp.addResponse("openlibrary.org", 200, openLibJson);

        MetadataService fallbackService = new MetadataService(mockHttp, cacheDao, Executors.newSingleThreadExecutor());

        Optional<BookMetadata> result = fallbackService.lookupSync("9780132350884");
        assertTrue(result.isPresent(), "Expected successful fallback to Open Library");
        assertEquals("Clean Code", result.get().getTitle());
        assertEquals("Robert C. Martin", result.get().getAuthors());
        assertEquals("OPEN_LIBRARY", result.get().getSource());

        fallbackService.shutdown();
    }

    // --- Mock HTTP Client Supporting Multi-Host Responses ---

    private static class TestMockHttpClient extends HttpClient {
        private final Map<String, String> stringResponses = new ConcurrentHashMap<>();
        private final Map<String, Integer> statusCodes = new ConcurrentHashMap<>();
        private final Map<String, byte[]> byteResponses = new ConcurrentHashMap<>();

        public void addResponse(String hostSubstr, int statusCode, String body) {
            stringResponses.put(hostSubstr, body);
            statusCodes.put(hostSubstr, statusCode);
        }

        public void addByteResponse(String hostSubstr, int statusCode, byte[] body) {
            byteResponses.put(hostSubstr, body);
            statusCodes.put(hostSubstr, statusCode);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseHandler) throws IOException {
            String uriStr = request.uri().toString();
            for (Map.Entry<String, String> entry : stringResponses.entrySet()) {
                if (uriStr.contains(entry.getKey())) {
                    int code = statusCodes.getOrDefault(entry.getKey(), 200);
                    return new MockHttpResponse<>(code, (T) entry.getValue());
                }
            }
            for (Map.Entry<String, byte[]> entry : byteResponses.entrySet()) {
                if (uriStr.contains(entry.getKey())) {
                    int code = statusCodes.getOrDefault(entry.getKey(), 200);
                    return new MockHttpResponse<>(code, (T) entry.getValue());
                }
            }
            return new MockHttpResponse<>(404, (T) "Not Found");
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
