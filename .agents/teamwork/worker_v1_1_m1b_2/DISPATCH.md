## 2026-10-21T06:06:43Z
You are Worker 1.1-B (ISBN and Metadata Worker).
Your working directory is: e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_v1_1_m1b_2
Your parent orchestrator is conversation ID: 36ceb3be-97cf-46d3-97ed-e1b4ef0f155d

Read:
- e:\javafx-desktop-bookstore-billing\.agents\teamwork\ORIGINAL_REQUEST.md (specifically the section under ## 2026-10-20T19:51:37Z)
- e:\javafx-desktop-bookstore-billing\.agents\teamwork\PROJECT.md
- Explorer 1 Report: e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_v1_1_survey_1\handoff.md
- Downstream Contract Report: e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_v1_1_m1a_fix_3\handoff.md

## Scope & Objective
Implement Milestone 1.1-B (Requirement R1: Intelligent Online/Offline ISBN Metadata Enrichment Engine):
1. Implement `src/main/java/com/example/t/service/isbn/IsbnValidator.java`:
   - `clean(String raw)`: strips whitespace, hyphens, and optional ISBN prefixes (`(?i)^ISBN(?:-?1[03])?[:\s]+`), uppers check digit 'X', and retains only `[0-9X]`.
   - `isValidIsbn10(String raw)`: Modulo 11 check with 'X' support.
   - `isValidIsbn13(String raw)`: Modulo 10 check with alternating weights 1 and 3.
   - `isValid(String raw)`: validates either ISBN-10 or ISBN-13.
   - `toIsbn13(String isbn10Raw)`: converts 10-digit ISBN to 13-digit with "978" prefix and new Modulo 10 check digit.
   - `toIsbn10(String isbn13Raw)`: converts "978..." 13-digit ISBN to 10-digit with Modulo 11 check digit; returns `Optional.empty()` for "979..." prefixes.
2. Implement `src/main/java/com/example/t/service/isbn/MetadataService.java`:
   - Uses Java 21's standard `java.net.http.HttpClient` (HTTP/2, async support).
   - Multi-tier fallback chain:
     1. L1 in-memory `ConcurrentHashMap<String, BookMetadata>` (dual-indexed by clean ISBN-13 and uppercase clean ISBN-10).
     2. L2 local SQLite database cache via `CatalogCacheDao`.
     3. Online query to Google Books API (`https://www.googleapis.com/books/v1/volumes?q=isbn:{isbn}`) with 3s timeout.
     4. Online fallback to Open Library API (`https://openlibrary.org/api/books?bibkeys=ISBN:{isbn}&format=json&jscmd=data`) with 3s timeout.
     5. On online success: save to L1, save to L2 (`CatalogCacheDao.save()`), and asynchronously download cover art to `%LOCALAPPDATA%\BookstoreBilling\data\covers\{isbn13}.jpg` via `DatabaseManager.getCoversDirectory()`.
     6. Offline fallback: graceful return of `Optional.empty()` without blocking UI or throwing unhandled exceptions.
   - Provide non-blocking `CompletableFuture<Optional<BookMetadata>> lookupAsync(String rawIsbn)` executed on a daemon thread executor (`Executors.newFixedThreadPool(4, ...)`), plus `lookupSync(String rawIsbn)`.
   - Lightweight JSON parsing: either parse Google Books / Open Library JSON responses using a robust self-contained tokenizer or lightweight extraction (no new unapproved Maven dependencies needed).
3. Integrate into UI:
   - In `src/main/java/com/example/t/controller/StockManagementController.java`:
     - In product dialog, provide ISBN field (or support ISBN entry in SKU/Edition).
     - When a valid ISBN is entered or scanned, trigger `metadataService.lookupAsync(...)` and auto-populate title, author, publisher, category ("Books"), and display cover preview.
   - In `src/main/java/com/example/t/controller/PosBillingController.java`:
     - Support direct search/scan of ISBNs via `productDao.findBySkuOrIsbn` or fallback to catalog cache.
4. Comprehensive Unit Tests:
   - `src/test/java/com/example/t/service/isbn/IsbnValidatorTest.java` (valid ISBN-10, valid ISBN-13, invalid checksums, bidirectional conversion, 979 prefix handling, clean edge cases).
   - `src/test/java/com/example/t/service/isbn/MetadataServiceTest.java` (L1 cache, L2 SQLite cache, offline fallback in <50ms without exceptions, async completion).
5. Verification:
   - Run `.\mvnw.cmd test` and ensure all 436+ tests pass with 0 failures, 0 errors.


## 2026-10-21T06:28:08Z
**Context**: Milestone 1.1-B Implementation Status Check
**Content**: Worker 1.1-B_2, you have passed 465/466 tests and are currently resolving `PosBillingEmpiricalChallengeTest.testBarcodeLookupSqlInjectionSafe`. Please complete your fix, verify with `.\mvnw.cmd test`, write `handoff.md`, and report completion.
**Action**: Finalize test verification and send completion message.
