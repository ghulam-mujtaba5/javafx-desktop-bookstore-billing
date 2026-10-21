package com.example.t.service.isbn;

import com.example.t.dao.CatalogCacheDao;
import com.example.t.database.DatabaseManager;
import com.example.t.model.BookMetadata;
import org.junit.jupiter.api.*;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
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

@DisplayName("Milestone 1.1-B: MetadataService Multi-Tier Fallback & Cache Tests")
public class MetadataServiceTest {

    private static Path tempDir;
    private CatalogCacheDao cacheDao;
    private MetadataService service;

    @BeforeEach
    public void setUp() throws IOException, SQLException {
        tempDir = Files.createTempDirectory("m1b_metadata_test_");
        Path dbPath = tempDir.resolve("metadata_test.db");
        DatabaseManager.setCustomDatabasePath(dbPath);
        DatabaseManager.initializeDatabase();

        cacheDao = new CatalogCacheDao();
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

    // --- Tier 1: L1 In-Memory Cache Tests ---

    @Test
    @DisplayName("L1 In-Memory Cache: Stores and dual-indexes by ISBN-13 and ISBN-10")
    public void testL1MemoryCacheDualIndexing() {
        BookMetadata meta = new BookMetadata();
        meta.setIsbn13("9780132350884");
        meta.setIsbn10("0132350882");
        meta.setTitle("Clean Code");
        meta.setAuthors("Robert C. Martin");
        meta.setPublisher("Prentice Hall");

        service.persistAndCache(meta);

        // Query by clean ISBN-13
        Optional<BookMetadata> by13 = service.lookupSync("9780132350884");
        assertTrue(by13.isPresent(), "Must hit L1 memory cache with ISBN-13");
        assertEquals("Clean Code", by13.get().getTitle());

        // Query by hyphenated ISBN-13
        Optional<BookMetadata> byHyphen13 = service.lookupSync("978-0-13-235088-4");
        assertTrue(byHyphen13.isPresent(), "Must hit L1 memory cache with hyphenated ISBN-13");

        // Query by ISBN-10
        Optional<BookMetadata> by10 = service.lookupSync("0132350882");
        assertTrue(by10.isPresent(), "Must hit L1 memory cache with ISBN-10");

        // Query by hyphenated ISBN-10
        Optional<BookMetadata> byHyphen10 = service.lookupSync("0-13-235088-2");
        assertTrue(byHyphen10.isPresent(), "Must hit L1 memory cache with hyphenated ISBN-10");

        // Cache size check
        assertTrue(service.getMemoryCacheSize() >= 2, "Expected dual-indexed entries in L1 memory cache");

        // Clear cache check
        service.clearMemoryCache();
        assertEquals(0, service.getMemoryCacheSize(), "Memory cache should be empty after clearMemoryCache()");
    }

    @Test
    @DisplayName("L1 In-Memory Cache: Case-invariant lookup with 'X' check digit")
    public void testL1MemoryCacheCheckDigitXCaseInvariance() {
        BookMetadata meta = new BookMetadata();
        meta.setIsbn13("9780804429573");
        meta.setIsbn10("080442957X");
        meta.setTitle("The Catcher in the Rye Guide");
        meta.setAuthors("J. D. Salinger");

        service.persistAndCache(meta);

        // Look up with lowercase 'x'
        Optional<BookMetadata> foundLower = service.lookupSync("0-8044-2957-x");
        assertTrue(foundLower.isPresent(), "L1 lookup with lowercase 'x' must succeed");
        assertEquals("The Catcher in the Rye Guide", foundLower.get().getTitle());

        // Look up with uppercase 'X'
        Optional<BookMetadata> foundUpper = service.lookupSync("080442957X");
        assertTrue(foundUpper.isPresent(), "L1 lookup with uppercase 'X' must succeed");
    }

    // --- Tier 2: L2 SQLite Cache Tests ---

    @Test
    @DisplayName("L2 Local SQLite Cache: Retrieves persisted metadata and repopulates L1 memory cache")
    public void testL2SqliteCacheHit() throws SQLException {
        BookMetadata meta = new BookMetadata();
        meta.setIsbn13("9780201633610");
        meta.setIsbn10("0201633612");
        meta.setTitle("Design Patterns");
        meta.setAuthors("Gang of Four");
        meta.setPublisher("Addison-Wesley");
        meta.setPublishedYear("1994");
        meta.setSource("LOCAL_CACHE");
        cacheDao.save(meta);

        // Ensure L1 is clear so query must fall back to L2
        service.clearMemoryCache();
        assertEquals(0, service.getMemoryCacheSize());

        Optional<BookMetadata> resolved = service.lookupSync("978-0-201-63361-0");
        assertTrue(resolved.isPresent(), "Must retrieve metadata from L2 SQLite cache");
        assertEquals("Design Patterns", resolved.get().getTitle());
        assertEquals("Gang of Four", resolved.get().getAuthors());

        // Verify L1 is now populated from L2
        assertTrue(service.getMemoryCacheSize() > 0, "L1 memory cache must be repopulated after L2 cache hit");
    }

    // --- Offline Fallback & Non-blocking Async Tests ---

    @Test
    @DisplayName("Offline Fallback: Unreachable network gracefully returns Optional.empty() in <50ms without exceptions")
    public void testOfflineGracefulFallback() {
        // Construct MetadataService with mock HttpClient that immediately throws IOException
        HttpClient mockOfflineClient = new MockHttpClient(new IOException("Simulated network down / offline mode"));
        MetadataService offlineService = new MetadataService(mockOfflineClient, cacheDao, Executors.newSingleThreadExecutor());

        long start = System.currentTimeMillis();
        // Valid ISBN not in cache
        Optional<BookMetadata> result = offlineService.lookupSync("978-0-13-235088-4");
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(result.isPresent(), "Offline query must return Optional.empty()");
        assertTrue(elapsed < 200, "Offline resolution must complete quickly without UI hanging. Elapsed: " + elapsed + "ms");
        offlineService.shutdown();
    }

    @Test
    @DisplayName("lookupAsync(): Completes asynchronously on worker pool")
    public void testLookupAsyncExecution() throws Exception {
        BookMetadata meta = new BookMetadata();
        meta.setIsbn13("9780132350884");
        meta.setIsbn10("0132350882");
        meta.setTitle("Clean Code Async");
        service.persistAndCache(meta);

        CompletableFuture<Optional<BookMetadata>> future = service.lookupAsync("978-0-13-235088-4");
        assertNotNull(future);
        Optional<BookMetadata> opt = future.get(5, TimeUnit.SECONDS);
        assertTrue(opt.isPresent());
        assertEquals("Clean Code Async", opt.get().getTitle());
    }

    // --- JSON Parsing Unit Tests ---

    @Test
    @DisplayName("Google Books JSON Parser: Extracts full bibliographic details correctly")
    public void testParseGoogleBooksResponse() {
        String json = "{\n" +
                "  \"kind\": \"books#volumes\",\n" +
                "  \"totalItems\": 1,\n" +
                "  \"items\": [\n" +
                "    {\n" +
                "      \"volumeInfo\": {\n" +
                "        \"title\": \"Clean Code\",\n" +
                "        \"subtitle\": \"A Handbook of Agile Software Craftsmanship\",\n" +
                "        \"authors\": [\"Robert C. Martin\"],\n" +
                "        \"publisher\": \"Prentice Hall\",\n" +
                "        \"publishedDate\": \"2008-08-01\",\n" +
                "        \"description\": \"Even bad code can function. But if code isn't clean...\",\n" +
                "        \"industryIdentifiers\": [\n" +
                "          { \"type\": \"ISBN_10\", \"identifier\": \"0132350882\" },\n" +
                "          { \"type\": \"ISBN_13\", \"identifier\": \"9780132350884\" }\n" +
                "        ],\n" +
                "        \"pageCount\": 464,\n" +
                "        \"categories\": [\"Computers / Programming / General\"],\n" +
                "        \"imageLinks\": {\n" +
                "          \"smallThumbnail\": \"http://books.google.com/thumbnail.jpg\",\n" +
                "          \"thumbnail\": \"http://books.google.com/full_thumb.jpg\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        Optional<BookMetadata> opt = service.parseGoogleBooksResponse(json, "9780132350884", "0132350882");
        assertTrue(opt.isPresent());
        BookMetadata meta = opt.get();
        assertEquals("Clean Code", meta.getTitle());
        assertEquals("A Handbook of Agile Software Craftsmanship", meta.getSubtitle());
        assertEquals("Robert C. Martin", meta.getAuthors());
        assertEquals("Prentice Hall", meta.getPublisher());
        assertEquals("2008", meta.getPublishedYear());
        assertEquals(464, meta.getPageCount());
        assertEquals("Computers / Programming / General", meta.getGenre());
        assertTrue(meta.getCoverUrl().startsWith("https://books.google.com/"), "Thumbnail URL should be upgraded to HTTPS");
        assertEquals("GOOGLE_BOOKS", meta.getSource());
    }

    @Test
    @DisplayName("Open Library JSON Parser: Extracts full bibliographic details correctly")
    public void testParseOpenLibraryResponse() {
        String json = "{\n" +
                "  \"ISBN:9780132350884\": {\n" +
                "    \"title\": \"Clean Code\",\n" +
                "    \"subtitle\": \"A Handbook of Agile Software Craftsmanship\",\n" +
                "    \"authors\": [\n" +
                "      { \"name\": \"Robert C. Martin\", \"url\": \"https://openlibrary.org/authors/OL123\" }\n" +
                "    ],\n" +
                "    \"publishers\": [\n" +
                "      { \"name\": \"Prentice Hall\" }\n" +
                "    ],\n" +
                "    \"publish_date\": \"August 1, 2008\",\n" +
                "    \"number_of_pages\": 464,\n" +
                "    \"cover\": {\n" +
                "      \"small\": \"https://covers.openlibrary.org/b/id/1-S.jpg\",\n" +
                "      \"medium\": \"https://covers.openlibrary.org/b/id/1-M.jpg\",\n" +
                "      \"large\": \"https://covers.openlibrary.org/b/id/1-L.jpg\"\n" +
                "    },\n" +
                "    \"identifiers\": {\n" +
                "      \"isbn_10\": [\"0132350882\"],\n" +
                "      \"isbn_13\": [\"9780132350884\"]\n" +
                "    }\n" +
                "  }\n" +
                "}";

        Optional<BookMetadata> opt = service.parseOpenLibraryResponse(json, "9780132350884", "9780132350884", "0132350882");
        assertTrue(opt.isPresent());
        BookMetadata meta = opt.get();
        assertEquals("Clean Code", meta.getTitle());
        assertEquals("A Handbook of Agile Software Craftsmanship", meta.getSubtitle());
        assertEquals("Robert C. Martin", meta.getAuthors());
        assertEquals("Prentice Hall", meta.getPublisher());
        assertEquals("2008", meta.getPublishedYear());
        assertEquals(464, meta.getPageCount());
        assertEquals("https://covers.openlibrary.org/b/id/1-M.jpg", meta.getCoverUrl());
        assertEquals("OPEN_LIBRARY", meta.getSource());
    }

    // --- Mock HTTP Client for Testing ---

    private static class MockHttpClient extends HttpClient {
        private final Exception exceptionToThrow;
        private final String responseBody;
        private final int statusCode;

        MockHttpClient(Exception exceptionToThrow) {
            this.exceptionToThrow = exceptionToThrow;
            this.responseBody = null;
            this.statusCode = 0;
        }

        MockHttpClient(String responseBody, int statusCode) {
            this.exceptionToThrow = null;
            this.responseBody = responseBody;
            this.statusCode = statusCode;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseHandler) throws IOException, InterruptedException {
            if (exceptionToThrow instanceof IOException) {
                throw (IOException) exceptionToThrow;
            } else if (exceptionToThrow instanceof InterruptedException) {
                throw (InterruptedException) exceptionToThrow;
            } else if (exceptionToThrow != null) {
                throw new RuntimeException(exceptionToThrow);
            }

            return new MockHttpResponse<>(statusCode, (T) responseBody);
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

        @Override
        public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override
        public Optional<Duration> connectTimeout() { return Optional.of(Duration.ofSeconds(3)); }
        @Override
        public Redirect followRedirects() { return Redirect.NORMAL; }
        @Override
        public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override
        public SSLContext sslContext() { return null; }
        @Override
        public SSLParameters sslParameters() { return null; }
        @Override
        public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override
        public Version version() { return Version.HTTP_2; }
        @Override
        public Optional<Executor> executor() { return Optional.empty(); }
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
