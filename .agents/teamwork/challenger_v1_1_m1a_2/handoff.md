# Handoff Report: Milestone 1.1-A (DAO Stress & Edge Case Adversarial Challenge)

**Challenger**: Challenger 2 (DAO Stress & Edge Case Challenger)  
**Verdict**: `REQUEST_CHANGES`

---

## 1. Observation

1. **Empirical Test Suite Execution**:
   - Test class created at `src/test/java/com/example/t/dao/DaoStressAndEdgeCaseEmpiricalTest.java` containing 10 stress/edge-case tests covering `CatalogCacheDao`, `InvoicePaymentDao`, and `ZReportDao`.
   - Command executed:
     ```powershell
     .\mvnw.cmd test "-Dtest=DaoStressAndEdgeCaseEmpiricalTest"
     ```
   - Verbatim test output and failure traces:
     ```
     [ERROR] Failures: 
     [ERROR]   DaoStressAndEdgeCaseEmpiricalTest.testCatalogCacheIsbnLookupVariations:94 Lookup with lowercase 'x' check digit should find record saved with uppercase 'X' ==> expected: <true> but was: <false>
     [ERROR]   DaoStressAndEdgeCaseEmpiricalTest.testCatalogCacheSavedWithLowercaseX:114 Uppercase 'X' lookup should match record saved with lowercase 'x' ==> expected: <true> but was: <false>
     [INFO] 
     [ERROR] Tests run: 10, Failures: 2, Errors: 0, Skipped: 0
     [INFO] BUILD FAILURE
     ```

2. **Source Code Inspection - `CatalogCacheDao.java`**:
   - In `src/main/java/com/example/t/dao/CatalogCacheDao.java:71-88`:
     ```java
     71:         String clean = rawIsbn.replaceAll("[^0-9X-x]", "").trim();
     72:         String sql = "SELECT * FROM catalog_cache WHERE " +
     73:                 "isbn13 = ? OR isbn10 = ? OR " +
     74:                 "REPLACE(isbn13, '-', '') = ? OR REPLACE(isbn10, '-', '') = ? " +
     75:                 "LIMIT 1;";
     76: 
     77:         try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
     78:             pstmt.setString(1, rawIsbn.trim());
     79:             pstmt.setString(2, rawIsbn.trim());
     80:             pstmt.setString(3, clean);
     81:             pstmt.setString(4, clean);
     ```
   - In `src/main/java/com/example/t/dao/CatalogCacheDao.java:103-114` (`delete`):
     ```java
     103:         String clean = isbn.replaceAll("[^0-9X-x]", "").trim();
     104:         String sql = "DELETE FROM catalog_cache WHERE " +
     105:                 "isbn13 = ? OR isbn10 = ? OR " +
     106:                 "REPLACE(isbn13, '-', '') = ? OR REPLACE(isbn10, '-', '') = ?;";
     ```
   - In `src/main/resources/com/example/t/db/V2__v1_1_enhancements.sql:4-6`:
     ```sql
     CREATE TABLE IF NOT EXISTS catalog_cache (
         isbn13 TEXT PRIMARY KEY,
         isbn10 TEXT,
     ...
     ```

3. **Passing Stress & Edge Cases**:
   - `testCatalogCacheSaveNullAndEmptyFields`: Saving `BookMetadata` with null `subtitle`, null `authors`, null `coverUrl`, null `coverImagePath`, null `source`, null `cachedAt` succeeds without `NullPointerException`; empty strings `""` are retained properly; null or blank `isbn13` throws `IllegalArgumentException`.
   - `testCatalogCacheUpsertExistingIsbn`: Upserting an existing `isbn13` overwrites title, page count, and cover URL while maintaining exactly 1 record in `catalog_cache`.
   - `testSplitTenderMultiPaymentSaveAndTotals`: Batch payment saving across multiple tenders (`CASH`, `CARD`, `STORE_CREDIT`) records each tender correctly with tender-specific attributes (`amount`, `tenderedAmount`, `changeDue`, `referenceInfo`).
   - `testGetTotalPaidForEmptyOrNonexistentInvoice`: `getTotalPaidForInvoice` correctly aggregates $\sum \text{amount}$ (e.g. $100.0 + 150.0 + 50.0 = 300.0$), returns `0.0` for unpaid invoices, and returns `0.0` for nonexistent invoice IDs without error. Null or empty batch lists no-op safely.
   - `testTransactionRollbackOnPaymentFailure`: `InvoicePaymentDao.savePayments` wraps the batch in `conn.setAutoCommit(false)` / `conn.rollback()`. A foreign key violation on nonexistent invoice ID triggers a rollback and leaves 0 partial/orphaned payment records.
   - `testZReportEmptyTableBehavior`: On an empty `financial_z_reports` table, `getNextSequenceNumber()` returns `1`, `getLatestCumulativeGrandTotal()` returns `0.0`, `getLatestZReport()` returns `Optional.empty()`, and `getAllZReports()` returns an empty list.
   - `testZReportSequenceNumberGenerationAndUniqueness`: When `zSequenceNumber <= 0`, reports automatically receive consecutive sequences $1, 2, 3 \dots$. Jump sequences (e.g. seq 10) correctly advance `getNextSequenceNumber()` to $11$. Duplicate sequence numbers throw `SQLException` due to the `UNIQUE` constraint.
   - `testCumulativeGrandTotalMath`: Across 5 consecutive register reports, cumulative grand total math $CGT_K = CGT_{K-1} + \text{net\_sales}$ tracks accurately and orders descending by sequence.

---

## 2. Logic Chain

1. **Defect in `CatalogCacheDao` Check Digit Normalization**:
   - In ISBN-10, the check digit represents Modulo 11, where a value of 10 is denoted by the numeral `'X'` or `'x'` (ISO 2108).
   - Barcode scanners and cashier keyboards frequently emit lowercase `'x'` (e.g., `"080442957x"` or `"0-8044-2957-x"`).
   - In SQLite, the column `isbn10 TEXT` uses default `BINARY` collation (case-sensitive byte comparison).
   - In `CatalogCacheDao.findByIsbn`:
     - The regex `[^0-9X-x]` contains the ASCII range `X-x` (ASCII 88 to 120), which unintentionally matches lowercase letters and punctuation instead of just the characters `'X'` and `'x'`.
     - `clean` is not converted to uppercase (`clean.toUpperCase()`).
     - Query parameters are passed as-is (`pstmt.setString(3, clean)`).
     - Because `'080442957X' = '080442957x'` evaluates to `FALSE` under SQLite `BINARY` collation, searching with lowercase `'x'` fails to find an uppercase `'X'` record, and vice versa.
   - The same defect exists in `CatalogCacheDao.delete()`.
   - When downstream `MetadataService` or POS barcode scanning attempts an offline cache lookup for any ISBN-10 ending in 'x'/'X', a silent cache miss occurs, forcing an unnecessary external HTTP request or failing outright in offline mode.

2. **Resolution Required**:
   - In `CatalogCacheDao.java`:
     - Canonicalize clean ISBN inputs to uppercase:
       ```java
       String clean = rawIsbn.replaceAll("[^0-9Xx]", "").trim().toUpperCase();
       ```
     - In `save()`, canonicalize `isbn10` if present:
       ```java
       pstmt.setString(2, metadata.getIsbn10() != null ? metadata.getIsbn10().trim().toUpperCase() : null);
       ```
     - In `findByIsbn()` and `delete()`, either pass uppercase values to parameters or use `COLLATE NOCASE` / `UPPER()` in the SQL statement:
       ```sql
       SELECT * FROM catalog_cache WHERE 
           isbn13 = ? OR isbn10 = ? COLLATE NOCASE OR 
           REPLACE(isbn13, '-', '') = ? OR REPLACE(isbn10, '-', '') = ? COLLATE NOCASE 
       LIMIT 1;
       ```

---

## 3. Caveats

- **No caveats.** The defect was empirically produced and confirmed using standard JUnit 5 tests running under JDK 21 on the project's exact SQLite database schema.

---

## 4. Conclusion

- **Verdict**: `REQUEST_CHANGES`
- `InvoicePaymentDao` and `ZReportDao` are fully robust and pass all adversarial stress tests (split tenders, total calculation, transaction rollback, empty table handling, sequence generation, cumulative grand total arithmetic).
- `CatalogCacheDao` has a confirmed defect in ISBN-10 check digit case sensitivity (`findByIsbn` and `delete`), along with a character class range flaw (`[^0-9X-x]`).
- The worker must fix `CatalogCacheDao.java` so that all 10 tests in `DaoStressAndEdgeCaseEmpiricalTest.java` pass cleanly.

---

## 5. Verification Method

To verify the defect and validate the subsequent fix:

1. **Execute the Empirical DAO Stress Test Suite**:
   ```powershell
   .\mvnw.cmd test "-Dtest=DaoStressAndEdgeCaseEmpiricalTest"
   ```
   - **Current Result**: 2 failures in `testCatalogCacheIsbnLookupVariations` and `testCatalogCacheSavedWithLowercaseX`.
   - **Expected Result After Worker Fix**: `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.

2. **Execute Full Combined DAO & Migration Suite**:
   ```powershell
   .\mvnw.cmd test "-Dtest=DaoStressAndEdgeCaseEmpiricalTest,CatalogCacheDaoTest,InvoicePaymentDaoTest,ZReportDaoTest,SchemaMigrationV2Test"
   ```
   - **Expected Result After Worker Fix**: All 24 tests pass with 0 failures and 0 errors.
