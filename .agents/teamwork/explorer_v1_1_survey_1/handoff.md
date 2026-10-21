# Technical Survey Report: Intelligent Online/Offline ISBN Metadata Enrichment Engine (Requirement R1)

## 1. Observation

### 1.1 Existing Dependencies & Modular Configuration
- **File**: `e:\javafx-desktop-bookstore-billing\pom.xml` (Lines 23–67)
  - Dependencies present: `javafx-controls` (21.0.6), `javafx-fxml` (21.0.6), `controlsfx` (11.2.1), `atlantafx-base` (2.0.1), `sqlite-jdbc` (3.46.1.3), `openpdf` (1.3.40), `junit-jupiter` (5.10.2).
  - **No HTTP Client dependency** is declared. (Java SE 21 provides standard `java.net.http.HttpClient` in module `java.net.http`).
  - **No JSON parsing library** (e.g. Jackson, Gson, org.json) is currently declared in `pom.xml`.
- **File**: `e:\javafx-desktop-bookstore-billing\src\main\java\module-info.java` (Lines 1–20)
  ```java
  module com.example.t {
      requires javafx.controls;
      requires javafx.fxml;
      requires org.controlsfx.controls;
      requires atlantafx.base;
      requires java.sql;
      requires java.desktop;
      requires com.github.librepdf.openpdf;
      ...
  ```
  - `requires java.net.http;` is currently absent from `module-info.java`. Calling `java.net.http.HttpClient` will cause compile-time module resolution errors unless added.

### 1.2 Data Models (`Product.java`, `Category.java`)
- **File**: `e:\javafx-desktop-bookstore-billing\src\main\java\com\example\t\Product.java` (Lines 11–25)
  - Observable fields present: `productId` (IntegerProperty), `productName` (StringProperty), `category` (StringProperty), `sku` (StringProperty), `author` (StringProperty), `publisher` (StringProperty), `edition` (StringProperty), `purchasePrice` (DoubleProperty), `price` (DoubleProperty), `quantity` (IntegerProperty), `minStockThreshold` (IntegerProperty), `discount` (DoubleProperty), `status` (BooleanProperty), `discountedPrice` (DoubleProperty).
  - **Absent fields**: `isbn`, `subtitle`, `genre`, `publishedYear` / `publicationYear`, `format`, `coverImagePath`, `description`, `pageCount`.
  - Constructors present: 0-arg default (lines 29–31), 5-arg legacy (lines 36–51), 11-arg comprehensive (lines 56–73).
- **File**: `e:\javafx-desktop-bookstore-billing\src\main\java\com\example\t\model\Category.java` (Lines 13–27)
  - Fields: `categoryId` (int), `name` (String), `description` (String).
  - Seeded default categories in database: `Books`, `Stationery`, `Magazines`, `Office Supplies`.

### 1.3 Database Schema, Migrations, and DAOs
- **File**: `e:\javafx-desktop-bookstore-billing\src\main\resources\com\example\t\db\V1__init_schema.sql` (Lines 20–35) & `SchemaMigrationManager.java` (Lines 124–139)
  - `products` table schema:
    ```sql
    CREATE TABLE IF NOT EXISTS products (
        product_id INTEGER PRIMARY KEY AUTOINCREMENT,
        sku TEXT UNIQUE NOT NULL,
        name TEXT NOT NULL,
        category_id INTEGER REFERENCES categories(category_id) ON DELETE SET NULL,
        price REAL NOT NULL,
        purchase_price REAL DEFAULT 0.0,
        stock_quantity INTEGER NOT NULL DEFAULT 0,
        min_stock_threshold INTEGER DEFAULT 5,
        author TEXT,
        publisher TEXT,
        isbn TEXT,
        status TEXT DEFAULT 'ACTIVE',
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
    ```
  - An `isbn` column already exists in the SQLite table definition.
- **File**: `e:\javafx-desktop-bookstore-billing\src\main\java\com\example\t\dao\ProductDao.java`
  - In `save(...)` (Line 156) and `update(...)` (Line 194):
    `pstmt.setString(10, product.getEdition()); // Stored in isbn column`
  - In `mapRow(...)` (Line 281):
    `String edition = rs.getString("isbn");`
  - The `edition` property in `Product` is currently overloaded to read and write to the SQLite `isbn` column.
- **File**: `e:\javafx-desktop-bookstore-billing\src\main\java\com\example\t\database\DatabaseManager.java`
  - Lines 36–63: Resolves data directory via `getDataDirectory()` to `%LOCALAPPDATA%\BookstoreBilling\data\`.
  - Lines 80–91: Helper methods exist for `getExportsDirectory()` (`.../exports`) and `getReceiptsDirectory()` (`.../receipts`). No cover art directory helper (`getCoversDirectory()`) currently exists.
  - Lines 141–150: SQLite PRAGMAs configured: `PRAGMA foreign_keys = ON;`, `PRAGMA busy_timeout = 5000;`, `PRAGMA journal_mode = WAL;`.
- **File**: `e:\javafx-desktop-bookstore-billing\src\main\java\com\example\t\database\SchemaMigrationManager.java`
  - Line 18: `CURRENT_SCHEMA_VERSION = 1;`
  - Applies version 1 migration via `V1__init_schema.sql` and tracks it in `schema_migrations`.

### 1.4 UI Controllers & Views
- **File**: `e:\javafx-desktop-bookstore-billing\src\main\java\com\example\t\controller\StockManagementController.java`
  - Lines 394–554: `showProductDialog(Product existing)` creates a modal `Dialog<Product>` with a `GridPane` containing `categoryCombo`, `skuField`, `nameField`, `authorField`, `publisherField`, `editionField`, `purchasePriceField`, `priceField`, `quantityField`, and `minStockField`.
  - Lines 432–441: Listens to category change: if "Books", enables `authorField` and prompts for "Edition or ISBN".
  - Currently has no automated background lookup, no checksum validation feedback, and no thumbnail preview.
- **File**: `e:\javafx-desktop-bookstore-billing\src\main\java\com\example\t\controller\PosBillingController.java`
  - Lines 274–316: `handleSearchEnter(ActionEvent event)` searches exact SKU match via `productDao.findBySku(query)` or filters catalog.
  - If a barcode scanned is an ISBN that is not yet saved under `sku`, lookup fails.

### 1.5 Baseline Build and Test Status
- Command: `./mvnw.cmd test-compile` exited with code 0 in 1.700s. Clean compilation on JDK 21 LTS.
- Command: `./mvnw.cmd test` ran 398 tests across 30 test classes. 397 tests passed, 1 test failed:
  `FinalAcceptanceCriteriaVerificationTest.testAcceptanceCriterion6_MultiRegionalCurrencyAndTaxCalculationSwitching:528 Header must display Arabic currency symbol ==> expected: <true> but was: <false>`.
  All 12 `ProductDaoTest` and 8 `ProductTest` passed with 0 failures.

---

## 2. Logic Chain

1. **HTTP & Networking Strategy**:
   - Standard Java SE 21 provides `java.net.http.HttpClient` with non-blocking async capabilities (`sendAsync`), HTTP/2, connection timeout configuration, and response streaming.
   - Using `java.net.http.HttpClient` introduces zero external third-party JARs, eliminating classpath conflicts and ensuring reliable `jpackage` bundling.
   - Therefore, the only requirement is declaring `requires java.net.http;` in `src/main/java/module-info.java`.

2. **JSON Strategy**:
   - Both Google Books API (`https://www.googleapis.com/books/v1/volumes?q=isbn:{isbn}`) and Open Library API (`https://openlibrary.org/api/books?bibkeys=ISBN:{isbn}&format=json&jscmd=data`) return JSON structures with nested arrays (`authors`, `categories`) and objects (`imageLinks`, `cover`).
   - Adding `com.google.code.gson:gson:2.11.0` (~290 KB) to `pom.xml` and `requires com.google.gson;` to `module-info.java` provides standardized, robust parsing.
   - Alternatively, a self-contained ~150-line recursive descent JSON tokenizer/parser (`SimpleJson`) can be placed in `com.example.t.service.isbn` to achieve 100% offline compilation without needing Maven repository downloads.

3. **ISBN Checksum & Bidirectional Conversion Specification**:
   - **ISBN-10 Checksum (Mod 11)**:
     - Normalization: Remove dashes and whitespace. Ensure length is 10. Characters 0–8 must be digits '0'–'9'. Character 9 can be '0'–'9' or 'X' / 'x' (value 10).
     - Validation: Checksum $S = \sum_{i=0}^{9} d_i \times (10 - i)$. Valid iff $S \equiv 0 \pmod{11}$.
     - Check digit computation for first 9 digits: $C = (11 - (\sum_{i=0}^{8} d_i \times (10 - i) \pmod{11})) \pmod{11}$. If $C == 10$, check digit is `'X'`.
   - **ISBN-13 Checksum (Mod 10)**:
     - Normalization: Remove dashes and whitespace. Ensure length is 13; all characters must be digits '0'–'9'.
     - Validation: Checksum $S = \sum_{i=0}^{12} d_i \times (i \pmod 2 == 0 ? 1 : 3)$. Valid iff $S \equiv 0 \pmod{10}$.
     - Check digit computation for first 12 digits: $C = (10 - (\sum_{i=0}^{11} d_i \times (i \pmod 2 == 0 ? 1 : 3) \pmod{10})) \pmod{10}$.
   - **Bidirectional Conversion**:
     - *ISBN-10 $\rightarrow$ ISBN-13*: Prepend `"978"` to the first 9 digits of ISBN-10, then compute and append the Mod 10 check digit. Example: `0132350882` $\rightarrow$ `978013235088` + check digit `4` $\rightarrow$ `9780132350884`.
     - *ISBN-13 $\rightarrow$ ISBN-10*: Valid only if prefix is `"978"`. Extract digits 3 through 11 (the 9 bookland digits), then compute and append the Mod 11 check digit. (If prefix is `"979"`, conversion to ISBN-10 is impossible; return empty).

4. **Multi-Provider Fallback & Resiliency Architecture**:
   - Multi-tier resolution chain:
     1. **L1 In-Memory Cache**: `ConcurrentHashMap<String, BookMetadata>` (instant, <0.1 ms).
     2. **L2 Local SQLite Database Cache**: `catalog_cache` table queried via `CatalogCacheDao` (<2 ms).
     3. **Online Provider 1 (Google Books API)**: Query `https://www.googleapis.com/books/v1/volumes?q=isbn:{isbn}` with 3-second timeout. If `totalItems > 0`, extract metadata.
     4. **Online Provider 2 (Open Library API)**: Fallback query `https://openlibrary.org/api/books?bibkeys=ISBN:{isbn}&format=json&jscmd=data` with 3-second timeout.
     5. **Local Persistence**: Whenever an online lookup succeeds, save metadata to SQLite `catalog_cache` and asynchronously download cover art to `%LOCALAPPDATA%\BookstoreBilling\data\covers\{isbn13}.jpg`.
     6. **Offline / Network Failure Graceful Degradation**: If network is unavailable (`ConnectException`, timeout, HTTP 4xx/5xx), return empty `Optional` immediately without throwing unhandled exceptions or stalling the UI.

5. **Threading & UI Responsiveness**:
   - JavaFX thread safety rule: Network calls and disk I/O must execute off the JavaFX Application Thread.
   - Use a dedicated daemon thread executor: `Executors.newFixedThreadPool(4, new NamedThreadFactory("metadata-worker"))`.
   - All async methods return `CompletableFuture<Optional<BookMetadata>>`.
   - UI handlers attach `.thenAccept(opt -> Platform.runLater(() -> updateUI(opt)))`.
   - This guarantees zero UI freeze during barcode scanning or manual entry, even on high-latency or disconnected connections.

6. **Schema Evolution (Version 2 Migration)**:
   - Current schema is Version 1. To preserve existing data and tests:
   - Introduce `V2__catalog_cache_and_isbn_enrichment.sql` in `SchemaMigrationManager.java`:
     - Creates table `catalog_cache` (`isbn13` PRIMARY KEY, `isbn10`, `title`, `subtitle`, `authors`, `publisher`, `published_year`, `edition`, `format`, `genre`, `description`, `page_count`, `cover_image_path`, `cover_url`, `source`, `cached_at`).
     - Creates index on `catalog_cache(isbn10)` and `products(isbn)`.
     - Adds missing metadata columns to `products` table via `ALTER TABLE products ADD COLUMN ...` or maps them via the cache table.

7. **UI Integration Points**:
   - In `StockManagementController`:
     - Add ISBN field with real-time Mod 11/Mod 10 validation indicator.
     - When a valid ISBN is entered or scanned, trigger async metadata fetch.
     - Auto-fill `Product Name`, `Author`, `Publisher`, `Category` ("Books"), `Edition`, `SKU`, and show cover image preview.
   - In `PosBillingController`:
     - In `handleSearchEnter`, check `productDao.findBySkuOrIsbn(query)`.
     - If not in inventory, check `MetadataService` and offer quick-add/draft-sale dialog.

---

## 3. Caveats

1. **Pre-existing Test Failure**:
   - `FinalAcceptanceCriteriaVerificationTest.testAcceptanceCriterion6_MultiRegionalCurrencyAndTaxCalculationSwitching` failed in the baseline test run due to Arabic currency symbol verification. This is unrelated to R1 (Catalog/ISBN/Metadata) but should be kept in mind during regression testing.
2. **Open Library / Google Books Rate Limiting & Availability**:
   - Public APIs do not require API keys for low-volume desktop lookups, but can return HTTP 429 if flooded. Setting reasonable timeouts (2–3s) and caching all responses locally in SQLite permanently prevents repeated queries for the same ISBN.
3. **979 Prefix ISBN-13 Conversion**:
   - ISBN-13s starting with `979` cannot be converted to ISBN-10 because the `979` range is not mapped into the historical 10-digit system. The converter must return `Optional.empty()` for `979...` numbers.
4. **No Direct Code Changes in Investigation Turn**:
   - As an explorer subagent, no source code files were altered. Complete proposed architectural designs, class structures, DDL, and snippets are documented below.

---

## 4. Conclusion & Proposed Architecture

Requirement R1 is completely feasible within the existing architecture without breaking changes. Below is the actionable engineering specification for implementation.

### 4.1 Dependency & Module Plan
- In `pom.xml`: Add Gson dependency (or adopt zero-dependency `SimpleJson`):
  ```xml
  <dependency>
      <groupId>com.google.code.gson</groupId>
      <artifactId>gson</artifactId>
      <version>2.11.0</version>
  </dependency>
  ```
- In `src/main/java/module-info.java`:
  ```java
  requires java.net.http;
  requires com.google.gson; // if using Gson
  ```

### 4.2 Data Model: `com.example.t.model.BookMetadata`
```java
package com.example.t.model;

import java.util.List;

public class BookMetadata {
    private String isbn13;
    private String isbn10;
    private String title;
    private String subtitle;
    private List<String> authors;
    private String publisher;
    private String publishedYear;
    private String edition;
    private String format;
    private String genre;
    private String description;
    private int pageCount;
    private String coverImageUrl;
    private String coverLocalPath;
    private String source; // "GOOGLE_BOOKS", "OPEN_LIBRARY", "LOCAL_CACHE"

    // Constructors, Getters, Setters, and helper:
    public String getAuthorsJoined() {
        return (authors == null || authors.isEmpty()) ? "" : String.join(", ", authors);
    }

    public String getFullTitle() {
        if (subtitle != null && !subtitle.isBlank()) {
            return title + ": " + subtitle;
        }
        return title != null ? title : "";
    }
}
```

### 4.3 ISBN Validator & Bidirectional Converter: `com.example.t.service.isbn.IsbnValidator`
```java
package com.example.t.service.isbn;

import java.util.Optional;

public class IsbnValidator {

    public static String clean(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[^0-9Xx]", "").toUpperCase();
    }

    public static boolean isValidIsbn10(String raw) {
        String s = clean(raw);
        if (s.length() != 10) return false;
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            char c = s.charAt(i);
            if (!Character.isDigit(c)) return false;
            sum += (c - '0') * (10 - i);
        }
        char last = s.charAt(9);
        if (last == 'X') {
            sum += 10;
        } else if (Character.isDigit(last)) {
            sum += (last - '0');
        } else {
            return false;
        }
        return sum % 11 == 0;
    }

    public static boolean isValidIsbn13(String raw) {
        String s = clean(raw);
        if (s.length() != 13) return false;
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            char c = s.charAt(i);
            if (!Character.isDigit(c)) return false;
            int weight = (i % 2 == 0) ? 1 : 3;
            sum += (c - '0') * weight;
        }
        return sum % 10 == 0;
    }

    public static boolean isValid(String raw) {
        String s = clean(raw);
        if (s.length() == 10) return isValidIsbn10(s);
        if (s.length() == 13) return isValidIsbn13(s);
        return false;
    }

    public static String toIsbn13(String isbn10Raw) {
        if (!isValidIsbn10(isbn10Raw)) {
            throw new IllegalArgumentException("Invalid ISBN-10: " + isbn10Raw);
        }
        String s = clean(isbn10Raw);
        String prefix12 = "978" + s.substring(0, 9);
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int weight = (i % 2 == 0) ? 1 : 3;
            sum += (prefix12.charAt(i) - '0') * weight;
        }
        int check = (10 - (sum % 10)) % 10;
        return prefix12 + check;
    }

    public static Optional<String> toIsbn10(String isbn13Raw) {
        if (!isValidIsbn13(isbn13Raw)) return Optional.empty();
        String s = clean(isbn13Raw);
        if (!s.startsWith("978")) {
            return Optional.empty(); // 979 prefixes cannot be converted to ISBN-10
        }
        String core9 = s.substring(3, 12);
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (core9.charAt(i) - '0') * (10 - i);
        }
        int rem = sum % 11;
        int check = (11 - rem) % 11;
        char checkChar = (check == 10) ? 'X' : (char) ('0' + check);
        return Optional.of(core9 + checkChar);
    }
}
```

### 4.4 SQLite Cache Table Schema (Migration V2)
In `src/main/resources/com/example/t/db/V2__catalog_cache_and_isbn_enrichment.sql`:
```sql
-- V2: Catalog Cache and ISBN Enrichment Engine
CREATE TABLE IF NOT EXISTS catalog_cache (
    isbn13 TEXT PRIMARY KEY,
    isbn10 TEXT,
    title TEXT NOT NULL,
    subtitle TEXT,
    authors TEXT,
    publisher TEXT,
    published_year TEXT,
    edition TEXT,
    format TEXT,
    genre TEXT,
    description TEXT,
    page_count INTEGER,
    cover_image_path TEXT,
    cover_url TEXT,
    source TEXT,
    cached_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    raw_json TEXT
);

CREATE INDEX IF NOT EXISTS idx_catalog_cache_isbn10 ON catalog_cache(isbn10);
CREATE INDEX IF NOT EXISTS idx_catalog_cache_title ON catalog_cache(title);
CREATE INDEX IF NOT EXISTS idx_products_isbn ON products(isbn);
```

### 4.5 AppData Cover Directory in `DatabaseManager.java`
```java
public static Path getCoversDirectory() {
    return ensureDirectory(getDataDirectory().resolve("covers"));
}
```

### 4.6 Asynchronous `MetadataService.java`
```java
package com.example.t.service.isbn;

import com.example.t.Logger;
import com.example.t.dao.CatalogCacheDao;
import com.example.t.database.DatabaseManager;
import com.example.t.model.BookMetadata;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.*;

public class MetadataService {

    private final HttpClient httpClient;
    private final CatalogCacheDao cacheDao;
    private final ExecutorService executor;
    private final ConcurrentMap<String, BookMetadata> memoryCache = new ConcurrentHashMap<>();

    public MetadataService() {
        this(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(3))
                .build(),
             new CatalogCacheDao(),
             Executors.newFixedThreadPool(4, r -> {
                 Thread t = new Thread(r, "metadata-worker");
                 t.setDaemon(true);
                 return t;
             }));
    }

    public MetadataService(HttpClient httpClient, CatalogCacheDao cacheDao, ExecutorService executor) {
        this.httpClient = httpClient;
        this.cacheDao = cacheDao;
        this.executor = executor;
    }

    public CompletableFuture<Optional<BookMetadata>> lookupAsync(String rawIsbn) {
        return CompletableFuture.supplyAsync(() -> lookupSync(rawIsbn), executor);
    }

    public Optional<BookMetadata> lookupSync(String rawIsbn) {
        if (!IsbnValidator.isValid(rawIsbn)) {
            return Optional.empty();
        }
        String clean = IsbnValidator.clean(rawIsbn);
        String isbn13 = clean.length() == 13 ? clean : IsbnValidator.toIsbn13(clean);

        // 1. Memory Cache
        if (memoryCache.containsKey(isbn13)) {
            return Optional.of(memoryCache.get(isbn13));
        }

        // 2. Local SQLite Cache
        try {
            Optional<BookMetadata> cached = cacheDao.findByIsbn(isbn13);
            if (cached.isPresent()) {
                memoryCache.put(isbn13, cached.get());
                return cached;
            }
        } catch (Exception e) {
            Logger.warn("Local catalog cache query failed: " + e.getMessage());
        }

        // 3. Google Books API
        Optional<BookMetadata> googleMeta = queryGoogleBooks(isbn13);
        if (googleMeta.isPresent()) {
            persistAndCache(googleMeta.get());
            return googleMeta;
        }

        // 4. Fallback: Open Library API
        Optional<BookMetadata> openLibMeta = queryOpenLibrary(isbn13);
        if (openLibMeta.isPresent()) {
            persistAndCache(openLibMeta.get());
            return openLibMeta;
        }

        return Optional.empty();
    }

    private void persistAndCache(BookMetadata meta) {
        memoryCache.put(meta.getIsbn13(), meta);
        // Download cover image to AppData covers folder
        if (meta.getCoverImageUrl() != null && !meta.getCoverImageUrl().isBlank()) {
            try {
                Path coverFile = DatabaseManager.getCoversDirectory().resolve(meta.getIsbn13() + ".jpg");
                HttpRequest req = HttpRequest.newBuilder(URI.create(meta.getCoverImageUrl()))
                        .timeout(Duration.ofSeconds(4))
                        .header("User-Agent", "BookstoreBilling/1.1")
                        .GET().build();
                HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() == 200 && resp.body().length > 0) {
                    Files.write(coverFile, resp.body());
                    meta.setCoverLocalPath(coverFile.toAbsolutePath().toString());
                }
            } catch (Exception e) {
                Logger.warn("Failed to download cover image: " + e.getMessage());
            }
        }
        try {
            cacheDao.save(meta);
        } catch (Exception e) {
            Logger.warn("Failed to save metadata to cache database: " + e.getMessage());
        }
    }
}
```

### 4.7 Stock Management Dialog Integration
- In `StockManagementController.java`:
  - When `editionField` (or dedicated `isbnField`) loses focus or Enter is pressed:
    - Validate with `IsbnValidator.isValid(text)`.
    - If valid, execute `metadataService.lookupAsync(text).thenAccept(opt -> Platform.runLater(() -> { ... }))`.
    - Auto-fill `nameField`, `authorField`, `publisherField`, `categoryCombo.setValue("Books")`.
    - If thumbnail exists, display image preview.

---

## 5. Verification Method

To independently verify this survey and the subsequent implementation:

1. **Clean Compilation Check**:
   ```powershell
   ./mvnw.cmd clean test-compile
   ```
   Must succeed with 0 errors on JDK 21 LTS.

2. **ISBN Checksum & Conversion Unit Tests**:
   Create and execute `com.example.t.service.isbn.IsbnValidatorTest`:
   - Valid ISBN-10: `"0-13-235088-2"`, `"0-451-52493-4"`, `"0-201-63361-2"`.
   - Valid ISBN-13: `"978-0-13-235088-4"`, `"978-0-451-52493-5"`, `"978-0-201-63361-0"`.
   - Invalid checksums (off by 1) must return `false`.
   - Bidirectional conversion `toIsbn13` and `toIsbn10` must be reflexive and mathematically exact.
   - Non-convertible prefix (`979...`) must return `Optional.empty()`.

3. **MetadataService Mock / Offline Tests**:
   Create and execute `com.example.t.service.isbn.MetadataServiceTest`:
   - Offline verification: Disable network (or mock HTTP 503/timeout), verify service returns `Optional.empty()` in $< 50\text{ ms}$ from cache or fallback without throwing exceptions or freezing JavaFX thread.
   - SQLite Cache verification: Verify metadata written to `catalog_cache` table is returned on second lookup with 0 network calls.
   - AppData Cover verification: Verify cover JPEG is stored in `%LOCALAPPDATA%\BookstoreBilling\data\covers\{isbn13}.jpg`.

4. **Full Test Suite Execution**:
   ```powershell
   ./mvnw.cmd test
   ```
   Must run all unit and regression tests cleanly.
