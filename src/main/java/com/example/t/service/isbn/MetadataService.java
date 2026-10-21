package com.example.t.service.isbn;

import com.example.t.Logger;
import com.example.t.dao.CatalogCacheDao;
import com.example.t.database.DatabaseManager;
import com.example.t.model.BookMetadata;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Intelligent Online/Offline Bibliographic Metadata Retrieval Service.
 *
 * Implements a multi-tier fallback retrieval chain:
 * 1. L1 In-Memory Cache (ConcurrentHashMap, dual-indexed by clean ISBN-13 and uppercase clean ISBN-10)
 * 2. L2 Local SQLite Database Cache (via CatalogCacheDao)
 * 3. Online Provider 1: Google Books Volumes API (3s timeout)
 * 4. Online Provider 2: Open Library Books API (3s timeout)
 * 5. On Online Success: Persists to L1, L2 SQLite, and asynchronously downloads cover art
 * 6. Offline Fallback: Gracefully returns Optional.empty() without blocking or throwing unhandled exceptions
 */
public class MetadataService {

    private static final String GOOGLE_BOOKS_URL = "https://www.googleapis.com/books/v1/volumes?q=isbn:";
    private static final String OPEN_LIBRARY_URL = "https://openlibrary.org/api/books?bibkeys=ISBN:%s&format=json&jscmd=data";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(18|19|20)\\d{2}\\b");

    private final HttpClient httpClient;
    private final CatalogCacheDao cacheDao;
    private final ExecutorService executor;
    private final ConcurrentMap<String, BookMetadata> memoryCache = new ConcurrentHashMap<>();

    /**
     * Production constructor initializing default HTTP client and daemon executor.
     */
    public MetadataService() {
        this(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(DEFAULT_TIMEOUT)
                .build(),
             new CatalogCacheDao(),
             Executors.newFixedThreadPool(4, r -> {
                 Thread t = new Thread(r, "metadata-worker");
                 t.setDaemon(true);
                 return t;
             }));
    }

    /**
     * Dependency injection constructor for testing, mocking, or custom configurations.
     */
    public MetadataService(HttpClient httpClient, CatalogCacheDao cacheDao, ExecutorService executor) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.cacheDao = cacheDao;
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * Asynchronously retrieves book metadata for a raw ISBN input.
     * Executes off the JavaFX thread on daemon worker pool.
     *
     * @param rawIsbn raw ISBN string
     * @return CompletableFuture completing with Optional containing BookMetadata if found
     */
    public CompletableFuture<Optional<BookMetadata>> lookupAsync(String rawIsbn) {
        return CompletableFuture.supplyAsync(() -> lookupSync(rawIsbn), executor);
    }

    /**
     * Synchronously resolves book metadata following the multi-tier fallback chain.
     *
     * @param rawIsbn raw ISBN string
     * @return Optional containing BookMetadata if resolved, or Optional.empty()
     */
    public Optional<BookMetadata> lookupSync(String rawIsbn) {
        if (!IsbnValidator.isValid(rawIsbn)) {
            return Optional.empty();
        }

        String clean = IsbnValidator.clean(rawIsbn);
        String isbn13 = clean.length() == 13 ? clean : IsbnValidator.toIsbn13(clean);
        String isbn10 = clean.length() == 10 ? clean : IsbnValidator.toIsbn10(clean).orElse(null);

        // --- Tier 1: L1 In-Memory Cache (<0.1ms) ---
        BookMetadata cached = memoryCache.get(clean);
        if (cached != null) {
            return Optional.of(cached);
        }
        if (isbn13 != null) {
            cached = memoryCache.get(isbn13);
            if (cached != null) {
                memoryCache.put(clean, cached);
                return Optional.of(cached);
            }
        }
        if (isbn10 != null) {
            cached = memoryCache.get(isbn10);
            if (cached != null) {
                memoryCache.put(clean, cached);
                return Optional.of(cached);
            }
        }

        // --- Tier 2: L2 Local SQLite Database Cache (<2ms) ---
        if (cacheDao != null) {
            try {
                Optional<BookMetadata> dbCached = cacheDao.findByIsbn(clean);
                if (dbCached.isPresent()) {
                    BookMetadata meta = dbCached.get();
                    putIntoMemoryCache(meta);
                    return Optional.of(meta);
                }
            } catch (Exception e) {
                Logger.warn("L2 SQLite catalog cache lookup failed for ISBN " + clean + ": " + e.getMessage());
            }
        }

        // --- Tier 3: Online Provider 1 - Google Books API (3s timeout) ---
        String queryKey = (isbn13 != null) ? isbn13 : clean;
        Optional<BookMetadata> googleMeta = queryGoogleBooks(queryKey, isbn13, isbn10);
        if (googleMeta.isPresent()) {
            persistAndCache(googleMeta.get());
            return googleMeta;
        }

        // --- Tier 4: Online Provider 2 - Open Library API (3s timeout) ---
        Optional<BookMetadata> openLibMeta = queryOpenLibrary(queryKey, isbn13, isbn10);
        if (openLibMeta.isPresent()) {
            persistAndCache(openLibMeta.get());
            return openLibMeta;
        }

        // --- Tier 5: Offline / Not Found Fallback ---
        return Optional.empty();
    }

    /**
     * Queries Google Books API for bibliographic metadata.
     */
    Optional<BookMetadata> queryGoogleBooks(String queryIsbn, String expectedIsbn13, String expectedIsbn10) {
        String url = GOOGLE_BOOKS_URL + queryIsbn;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(DEFAULT_TIMEOUT)
                    .header("User-Agent", "BookstoreBilling/1.1")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                return Optional.empty();
            }

            return parseGoogleBooksResponse(response.body(), expectedIsbn13, expectedIsbn10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.warn("Google Books query interrupted for ISBN " + queryIsbn);
            return Optional.empty();
        } catch (IOException e) {
            Logger.info("Google Books query offline or unreachable for ISBN " + queryIsbn + ": " + e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            Logger.warn("Google Books query error for ISBN " + queryIsbn + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    Optional<BookMetadata> parseGoogleBooksResponse(String json, String expectedIsbn13, String expectedIsbn10) {
        try {
            Map<String, Object> root = SimpleJson.parseObject(json);
            if (root == null) {
                return Optional.empty();
            }

            Object totalItemsObj = root.get("totalItems");
            int totalItems = (totalItemsObj instanceof Number) ? ((Number) totalItemsObj).intValue() : 0;
            if (totalItems <= 0) {
                return Optional.empty();
            }

            Object itemsObj = root.get("items");
            if (!(itemsObj instanceof List) || ((List<?>) itemsObj).isEmpty()) {
                return Optional.empty();
            }

            Object firstItem = ((List<?>) itemsObj).get(0);
            if (!(firstItem instanceof Map)) {
                return Optional.empty();
            }

            Map<String, Object> itemMap = (Map<String, Object>) firstItem;
            Object volInfoObj = itemMap.get("volumeInfo");
            if (!(volInfoObj instanceof Map)) {
                return Optional.empty();
            }

            Map<String, Object> volumeInfo = (Map<String, Object>) volInfoObj;
            String title = (String) volumeInfo.get("title");
            if (title == null || title.isBlank()) {
                return Optional.empty();
            }

            BookMetadata meta = new BookMetadata();
            meta.setTitle(title.trim());
            meta.setSubtitle((String) volumeInfo.get("subtitle"));

            // Authors
            Object authorsObj = volumeInfo.get("authors");
            if (authorsObj instanceof List) {
                List<?> authorsList = (List<?>) authorsObj;
                String authorsJoined = authorsList.stream()
                        .map(Object::toString)
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.joining(", "));
                meta.setAuthors(authorsJoined);
            }

            meta.setPublisher((String) volumeInfo.get("publisher"));

            // Published Date / Year
            Object pubDateObj = volumeInfo.get("publishedDate");
            if (pubDateObj != null) {
                meta.setPublishedYear(extractYear(pubDateObj.toString()));
            }

            meta.setDescription((String) volumeInfo.get("description"));

            // Page count
            Object pageCountObj = volumeInfo.get("pageCount");
            if (pageCountObj instanceof Number) {
                meta.setPageCount(((Number) pageCountObj).intValue());
            }

            // Genre / Categories
            Object categoriesObj = volumeInfo.get("categories");
            if (categoriesObj instanceof List) {
                List<?> catList = (List<?>) categoriesObj;
                String genreJoined = catList.stream()
                        .map(Object::toString)
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.joining(", "));
                meta.setGenre(genreJoined);
            }

            // Cover Image URL
            Object imageLinksObj = volumeInfo.get("imageLinks");
            if (imageLinksObj instanceof Map) {
                Map<String, Object> imageLinks = (Map<String, Object>) imageLinksObj;
                String thumbnail = (String) imageLinks.get("thumbnail");
                if (thumbnail == null || thumbnail.isBlank()) {
                    thumbnail = (String) imageLinks.get("smallThumbnail");
                }
                if (thumbnail != null && !thumbnail.isBlank()) {
                    if (thumbnail.startsWith("http://")) {
                        thumbnail = "https://" + thumbnail.substring("http://".length());
                    }
                    meta.setCoverUrl(thumbnail);
                }
            }

            // ISBN extraction from industryIdentifiers
            String resolved13 = expectedIsbn13;
            String resolved10 = expectedIsbn10;
            Object idsObj = volumeInfo.get("industryIdentifiers");
            if (idsObj instanceof List) {
                for (Object idItem : (List<?>) idsObj) {
                    if (idItem instanceof Map) {
                        Map<?, ?> idMap = (Map<?, ?>) idItem;
                        String type = Objects.toString(idMap.get("type"), "");
                        String identifier = Objects.toString(idMap.get("identifier"), "");
                        if ("ISBN_13".equalsIgnoreCase(type) && IsbnValidator.isValidIsbn13(identifier)) {
                            resolved13 = IsbnValidator.clean(identifier);
                        } else if ("ISBN_10".equalsIgnoreCase(type) && IsbnValidator.isValidIsbn10(identifier)) {
                            resolved10 = IsbnValidator.clean(identifier);
                        }
                    }
                }
            }

            meta.setIsbn13(resolved13);
            meta.setIsbn10(resolved10);
            meta.setSource("GOOGLE_BOOKS");
            meta.setRawJson(json);
            meta.setCachedAt(new Timestamp(System.currentTimeMillis()));

            return Optional.of(meta);
        } catch (Exception e) {
            Logger.warn("Failed to parse Google Books response: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Queries Open Library API for bibliographic metadata.
     */
    Optional<BookMetadata> queryOpenLibrary(String queryIsbn, String expectedIsbn13, String expectedIsbn10) {
        String url = String.format(OPEN_LIBRARY_URL, queryIsbn);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(DEFAULT_TIMEOUT)
                    .header("User-Agent", "BookstoreBilling/1.1")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                return Optional.empty();
            }

            return parseOpenLibraryResponse(response.body(), queryIsbn, expectedIsbn13, expectedIsbn10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.warn("Open Library query interrupted for ISBN " + queryIsbn);
            return Optional.empty();
        } catch (IOException e) {
            Logger.info("Open Library query offline or unreachable for ISBN " + queryIsbn + ": " + e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            Logger.warn("Open Library query error for ISBN " + queryIsbn + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    Optional<BookMetadata> parseOpenLibraryResponse(String json, String queryIsbn, String expectedIsbn13, String expectedIsbn10) {
        try {
            Map<String, Object> root = SimpleJson.parseObject(json);
            if (root == null || root.isEmpty()) {
                return Optional.empty();
            }

            Map<String, Object> bookMap = null;
            Object direct = root.get("ISBN:" + queryIsbn);
            if (direct instanceof Map) {
                bookMap = (Map<String, Object>) direct;
            } else {
                for (Object v : root.values()) {
                    if (v instanceof Map) {
                        bookMap = (Map<String, Object>) v;
                        break;
                    }
                }
            }

            if (bookMap == null) {
                return Optional.empty();
            }

            String title = (String) bookMap.get("title");
            if (title == null || title.isBlank()) {
                return Optional.empty();
            }

            BookMetadata meta = new BookMetadata();
            meta.setTitle(title.trim());
            meta.setSubtitle((String) bookMap.get("subtitle"));

            // Authors: list of maps with "name"
            Object authorsObj = bookMap.get("authors");
            if (authorsObj instanceof List) {
                List<?> authorsList = (List<?>) authorsObj;
                List<String> names = new ArrayList<>();
                for (Object a : authorsList) {
                    if (a instanceof Map) {
                        Object nameVal = ((Map<?, ?>) a).get("name");
                        if (nameVal != null && !nameVal.toString().isBlank()) {
                            names.add(nameVal.toString().trim());
                        }
                    }
                }
                meta.setAuthors(String.join(", ", names));
            }

            // Publishers: list of maps with "name"
            Object publishersObj = bookMap.get("publishers");
            if (publishersObj instanceof List) {
                List<?> pubList = (List<?>) publishersObj;
                List<String> names = new ArrayList<>();
                for (Object p : pubList) {
                    if (p instanceof Map) {
                        Object nameVal = ((Map<?, ?>) p).get("name");
                        if (nameVal != null && !nameVal.toString().isBlank()) {
                            names.add(nameVal.toString().trim());
                        }
                    }
                }
                meta.setPublisher(String.join(", ", names));
            }

            // Publish Date / Year
            Object pubDateObj = bookMap.get("publish_date");
            if (pubDateObj != null) {
                meta.setPublishedYear(extractYear(pubDateObj.toString()));
            }

            // Number of pages
            Object numPagesObj = bookMap.get("number_of_pages");
            if (numPagesObj instanceof Number) {
                meta.setPageCount(((Number) numPagesObj).intValue());
            }

            // Cover Image URL
            Object coverObj = bookMap.get("cover");
            if (coverObj instanceof Map) {
                Map<String, Object> coverMap = (Map<String, Object>) coverObj;
                String coverUrl = (String) coverMap.get("medium");
                if (coverUrl == null || coverUrl.isBlank()) {
                    coverUrl = (String) coverMap.get("large");
                }
                if (coverUrl == null || coverUrl.isBlank()) {
                    coverUrl = (String) coverMap.get("small");
                }
                meta.setCoverUrl(coverUrl);
            }

            // Identifiers
            String resolved13 = expectedIsbn13;
            String resolved10 = expectedIsbn10;
            Object idsObj = bookMap.get("identifiers");
            if (idsObj instanceof Map) {
                Map<?, ?> idsMap = (Map<?, ?>) idsObj;
                Object isbn13List = idsMap.get("isbn_13");
                if (isbn13List instanceof List && !((List<?>) isbn13List).isEmpty()) {
                    String candidate13 = ((List<?>) isbn13List).get(0).toString();
                    if (IsbnValidator.isValidIsbn13(candidate13)) {
                        resolved13 = IsbnValidator.clean(candidate13);
                    }
                }
                Object isbn10List = idsMap.get("isbn_10");
                if (isbn10List instanceof List && !((List<?>) isbn10List).isEmpty()) {
                    String candidate10 = ((List<?>) isbn10List).get(0).toString();
                    if (IsbnValidator.isValidIsbn10(candidate10)) {
                        resolved10 = IsbnValidator.clean(candidate10);
                    }
                }
            }

            meta.setIsbn13(resolved13);
            meta.setIsbn10(resolved10);
            meta.setSource("OPEN_LIBRARY");
            meta.setRawJson(json);
            meta.setCachedAt(new Timestamp(System.currentTimeMillis()));

            return Optional.of(meta);
        } catch (Exception e) {
            Logger.warn("Failed to parse Open Library response: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Persists newly fetched metadata into L1 memory cache, L2 SQLite cache,
     * and asynchronously triggers cover art download.
     */
    void persistAndCache(BookMetadata meta) {
        if (meta == null) {
            return;
        }

        // Canonicalize ISBN fields to uppercase and clean format
        if (meta.getIsbn13() != null) {
            meta.setIsbn13(IsbnValidator.clean(meta.getIsbn13()));
        }
        if (meta.getIsbn10() != null) {
            meta.setIsbn10(IsbnValidator.clean(meta.getIsbn10()));
        }

        // Bidirectional auto-fill if one form is missing
        if ((meta.getIsbn13() == null || meta.getIsbn13().isBlank()) && meta.getIsbn10() != null && IsbnValidator.isValidIsbn10(meta.getIsbn10())) {
            meta.setIsbn13(IsbnValidator.toIsbn13(meta.getIsbn10()));
        }
        if ((meta.getIsbn10() == null || meta.getIsbn10().isBlank()) && meta.getIsbn13() != null && IsbnValidator.isValidIsbn13(meta.getIsbn13())) {
            IsbnValidator.toIsbn10(meta.getIsbn13()).ifPresent(meta::setIsbn10);
        }

        // 1. Put into L1 Memory Cache (dual-indexed)
        putIntoMemoryCache(meta);

        // 2. Put into L2 SQLite Cache
        if (cacheDao != null) {
            try {
                cacheDao.save(meta);
            } catch (Exception e) {
                Logger.warn("Failed to save BookMetadata to L2 SQLite cache: " + e.getMessage());
            }
        }

        // 3. Asynchronously download cover art
        String coverUrl = meta.getCoverImageUrl();
        if (coverUrl != null && !coverUrl.isBlank() && meta.getIsbn13() != null && !meta.getIsbn13().isBlank()) {
            downloadCoverAsync(meta.getIsbn13(), coverUrl, meta);
        }
    }

    /**
     * Dual-indexes metadata in L1 memory cache by ISBN-13 and ISBN-10.
     */
    private void putIntoMemoryCache(BookMetadata meta) {
        if (meta == null) return;
        if (meta.getIsbn13() != null && !meta.getIsbn13().isBlank()) {
            memoryCache.put(meta.getIsbn13(), meta);
        }
        if (meta.getIsbn10() != null && !meta.getIsbn10().isBlank()) {
            memoryCache.put(meta.getIsbn10(), meta);
        }
    }

    /**
     * Asynchronously downloads cover art into DatabaseManager.getCoversDirectory().
     */
    private void downloadCoverAsync(String isbn13, String coverUrl, BookMetadata meta) {
        CompletableFuture.runAsync(() -> {
            try {
                Path coversDir = DatabaseManager.getCoversDirectory();
                Path targetFile = coversDir.resolve(isbn13 + ".jpg");

                HttpRequest request = HttpRequest.newBuilder(URI.create(coverUrl))
                        .timeout(Duration.ofSeconds(5))
                        .header("User-Agent", "BookstoreBilling/1.1")
                        .GET()
                        .build();

                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200 && response.body() != null && response.body().length > 0) {
                    Files.write(targetFile, response.body());
                    meta.setCoverImagePath(targetFile.toAbsolutePath().toString());
                    if (cacheDao != null) {
                        try {
                            cacheDao.save(meta);
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                Logger.info("Asynchronous cover art download skipped/failed for ISBN " + isbn13 + ": " + e.getMessage());
            }
        }, executor);
    }

    private String extractYear(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        Matcher matcher = YEAR_PATTERN.matcher(dateStr);
        if (matcher.find()) {
            return matcher.group();
        }
        return dateStr.trim();
    }

    /**
     * Clears L1 in-memory cache (primarily for unit test isolation).
     */
    public void clearMemoryCache() {
        memoryCache.clear();
    }

    /**
     * Returns the number of items stored in L1 in-memory cache.
     */
    public int getMemoryCacheSize() {
        return memoryCache.size();
    }

    /**
     * Shuts down executor pool if needed.
     */
    public void shutdown() {
        executor.shutdown();
    }
}
