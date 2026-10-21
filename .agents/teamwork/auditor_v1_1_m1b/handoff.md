# Forensic Audit Report — Milestone 1.1-B (ISBN Metadata Enrichment Engine)

**Work Product**: Milestone 1.1-B (ISBN Validation, SimpleJson, MetadataService, CatalogCacheDao, ProductDao, Controller Integrations, and Tests)
**Profile**: General Project (Development Integrity Mode per ORIGINAL_REQUEST.md)
**Verdict**: **CLEAN**

---

## 1. Observation

A complete, line-by-line inspection of all source and test files touched for Milestone 1.1-B was performed:

1. **`src/main/java/com/example/t/service/isbn/IsbnValidator.java`**:
   - `clean(String raw)`: Strips common prefixes (`ISBN:`, `ISBN-10:`, `ISBN-13:`), normalizes whitespace, converts lowercase `x` to uppercase `X`, and eliminates all non-digit and non-`X` characters via `replaceFirst` and `replaceAll`.
   - `isValidIsbn10(String raw)`: Implements the exact standard Modulo 11 algorithm $\sum_{i=0}^9 d_i \times (10 - i) \equiv 0 \pmod{11}$. Digits $0..8$ are strictly digits; character $9$ handles both numeric digits and `'X'` (value 10).
   - `isValidIsbn13(String raw)`: Implements the standard Modulo 10 algorithm with alternating weights $1$ and $3$: $\sum_{i=0}^{12} d_i \times (i \pmod 2 == 0 ? 1 : 3) \equiv 0 \pmod{10}$.
   - `toIsbn13(String isbn10Raw)`: Validates input, prefixes `"978"`, computes the Mod 10 check digit via $(10 - (\sum \pmod{10})) \pmod{10}$, and returns the 13-character string.
   - `toIsbn10(String isbn13Raw)`: Validates input, verifies the `"978"` prefix, extracts the 9-digit core, calculates Mod 11 check digit $(11 - (\sum \pmod{11})) \pmod{11}$, handles check digit 10 as `'X'`, and correctly returns `Optional.empty()` for `"979"` prefixes.
   - **Zero hardcoded lookups, zero test-matching hash maps, and zero stub values.**

2. **`src/main/java/com/example/t/service/isbn/SimpleJson.java`**:
   - Zero-dependency recursive-descent parser and tokenizer.
   - Parses objects (`LinkedHashMap`), arrays (`ArrayList`), strings with full escape handling (`\"`, `\\`, `\/`, `\b`, `\f`, `\n`, `\r`, `\t`, `\uXXXX` 4-digit hex unicode decoding), numbers (integers, longs, doubles, exponential scientific notation), booleans (`true`/`false`), and `null`.
   - **Zero dummy facade objects, zero Jackson/Gson dependency, and zero pre-canned responses.**

3. **`src/main/java/com/example/t/service/isbn/MetadataService.java`**:
   - Built on standard Java 21 `java.net.http.HttpClient` with HTTP/2, 3-second connect and request timeouts, and a 4-thread daemon executor pool (`metadata-worker`).
   - Implements multi-tier fallback architecture:
     - Tier 1: L1 In-Memory Cache (`ConcurrentHashMap<String, BookMetadata>`) dual-indexed by clean ISBN-13 and clean ISBN-10 (<0.1ms).
     - Tier 2: L2 SQLite Cache (`CatalogCacheDao.findByIsbn`) query (<2ms); hits repopulate L1.
     - Tier 3: Online Google Books Volumes API query (`https://www.googleapis.com/books/v1/volumes?q=isbn:`).
     - Tier 4: Online Open Library Books API query (`https://openlibrary.org/api/books?bibkeys=ISBN:%s&format=json&jscmd=data`).
     - Tier 5: Graceful offline fallback returning `Optional.empty()` without UI thread hanging or unhandled exceptions.
   - Asynchronous cover art downloader: Downloads thumbnail images via `CompletableFuture.runAsync` and writes binary JPEG streams to `%LOCALAPPDATA%\BookstoreBilling\data\covers\{isbn13}.jpg`, updating the local cache database record with the file path.

4. **`src/main/java/com/example/t/dao/CatalogCacheDao.java` & `src/main/java/com/example/t/dao/ProductDao.java`**:
   - `CatalogCacheDao`: Complete CRUD operations against the SQLite `catalog_cache` table with parameterized `PreparedStatement` queries, safe normalization with `IsbnValidator.clean`, and full object mapping.
   - `ProductDao.findBySkuOrIsbn(String query)`: Employs two-stage lookup: (1) exact SKU or exact non-empty ISBN match, and (2) normalized ISBN match guarded strictly by `IsbnValidator.isValid(query)` to prevent false positive matches on empty or non-ISBN strings.

5. **`src/main/java/com/example/t/controller/StockManagementController.java` & `PosBillingController.java`**:
   - `StockManagementController`: Input listener on edition/SKU fields validates ISBN in real time; triggers asynchronous `getMetadataService().lookupAsync(...)` on a daemon thread, updating UI labels, text fields, and cover image preview on `Platform.runLater` without freezing the JavaFX application thread.
   - `PosBillingController`: Enter key action calls `productDao.findBySkuOrIsbn(query)` to instantly add items to the cart by barcode or ISBN, falling back to local catalog cache lookup to notify the user if an unscanned catalog title exists.

6. **Pre-populated Artifact Check**:
   - Searched workspace for pre-populated logs, result files, or fabricated test dumps (`Get-ChildItem -Recurse -File -Include *.log,*result*,*output*`). Found 0 pre-populated logs or fabricated artifacts.

7. **Empirical Build & Test Verification**:
   - `.\mvnw.cmd clean test-compile`: SUCCESS (54 main source files, 71 test source files compiled cleanly on OpenJDK 21 LTS).
   - `.\mvnw.cmd test "-Dtest=com.example.t.service.isbn.*Test"`: 67 tests executed, 0 failures, 0 errors, 0 skipped.
   - `.\mvnw.cmd test`: 504 tests executed across entire project, 0 failures, 0 errors, 0 skipped.

---

## 2. Logic Chain

1. **Authenticity of Logic**:
   - In `IsbnValidator`, both Modulo 11 and Modulo 10 algorithms were verified by checking the mathematical formula in the code against the ISO 2108 / EAN-13 standards. The code calculates sums using weighted loops (`(10 - i)` for ISBN-10, `(i % 2 == 0 ? 1 : 3)` for ISBN-13) and derives check digits dynamically. No static mappings or lookup tables exist.
   - In `SimpleJson`, the parser utilizes character position tracking, recursive descent for composite types, character escape translation, and unicode hex decoding. It does not return hardcoded values.
   - In `MetadataService`, the fallback chain transitions from L1 to L2, to Google Books, to Open Library, to `Optional.empty()`. Each stage is protected by proper timeout configurations and exception handling.

2. **Absence of Shortcuts & Facades**:
   - No mock bypasses exist in production code (`src/main/`).
   - Mock HTTP clients (`TestMockHttpClient`, `AdversarialMockHttpClient`) exist strictly within unit tests (`src/test/`) for deterministic, offline test isolation as required by standard testing practices.
   - Production constructors use standard system classes: `HttpClient.newBuilder()`, `new CatalogCacheDao()`, `Executors.newFixedThreadPool(4)`.

3. **Behavioral Integrity**:
   - The test suites do not use tautological assertions (e.g. `assertTrue(true)`). They verify specific properties: ISBN clean idempotence, check digit calculation, conversion reflexivity ($10 \to 13 \to 10$), JSON nested key extraction, HTTP failure handling, 50-thread concurrent lookup stress, cover file byte verification on disk, and SQLite database persistence.
   - All 504 tests pass cleanly, proving zero regressions in existing POS billing, tax calculation, and persistence modules.

---

## 3. Caveats

- Upstream online APIs (Google Books and Open Library) are external cloud services subject to external rate limiting and network availability. As verified in `MetadataServiceEmpiricalChallengeTest`, network dropouts and timeouts (<50ms) are handled gracefully by returning `Optional.empty()` without crashing or hanging the desktop UI.
- No other caveats.

---

## 4. Conclusion

Milestone 1.1-B (ISBN Metadata Enrichment Engine) work product is **100% genuine, authentic, and defect-free**. It contains no hardcoded test shortcuts, no facade implementations, no fabricated outputs, and introduces zero regressions.

**Final Verdict**: **CLEAN**

---

## 5. Verification Method

To independently reproduce the forensic verification results:

```powershell
# 1. Clean test compilation
.\mvnw.cmd clean test-compile

# 2. Run all ISBN engine unit & empirical challenge tests (67 tests)
.\mvnw.cmd test "-Dtest=com.example.t.service.isbn.*Test"

# 3. Run full application test suite (504 tests)
.\mvnw.cmd test
```

Expected result: `BUILD SUCCESS` with 0 failures, 0 errors, 0 skipped.
