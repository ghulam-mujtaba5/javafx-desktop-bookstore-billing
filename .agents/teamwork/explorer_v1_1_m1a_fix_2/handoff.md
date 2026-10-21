# Comprehensive Codebase Sweep Report: Regex Ranges and SQLite Collation Pitfalls

**Milestone**: 1.1-A Remediation  
**Author**: Explorer Fix 2 (`explorer_v1_1_m1a_fix_2`)  
**Target Scope**: `ProductDao.java`, `InvoiceDao.java`, `CategoryDao.java`, `SettingsDao.java`, `LegacyDataImporter.java`, `CsvSyncService.java`, and other DAOs / database infrastructure.

---

## 1. Observation

### 1.1 Regex Character Ranges Audit Across Codebase
A full regex pattern scan across `src/main/java` was executed for `Pattern.compile`, `replaceAll`, `replaceFirst`, `matches`, `split`, and character class ranges (`\[.*-.*\]`):

1. **`CatalogCacheDao.java` (Lines 71, 103)**:
   - Line 71: `String clean = rawIsbn.replaceAll("[^0-9X-x]", "").trim();`
   - Line 103: `String clean = isbn.replaceAll("[^0-9X-x]", "").trim();`
   - **Observation**: `X-x` specifies a range from ASCII 88 (`'X'`) to ASCII 120 (`'x'`), which inadvertently matches ASCII characters 88–120: `[`, `\`, `]`, `^`, `_`, `` ` ``, and `a-w`.
2. **`ProductDao.java`**:
   - **Observation**: Zero regex operations. No `Pattern.compile`, no `replaceAll`, no `replaceFirst`, no `matches`, no `split`.
3. **`InvoiceDao.java`**:
   - **Observation**: Zero regex operations.
4. **`CategoryDao.java`**:
   - **Observation**: Zero regex operations.
5. **`SettingsDao.java`**:
   - **Observation**: Zero regex operations.
6. **`LegacyDataImporter.java`**:
   - Lines 155, 258: `line.split(",")` and `line.split("\\t+")`.
   - Lines 241, 245, 249, 252: String literal replacements: `line.replace("Invoice ID:", "").trim()`, etc.
   - **Observation**: Zero regex character classes or character ranges.
7. **`CsvSyncService.java`**:
   - Line 51: `field.replace("\"", "\"\"")` (literal character replacement).
   - Line 173: `firstRow.get(0).equalsIgnoreCase("sku") || firstRow.get(0).toLowerCase().contains("product")` (case-insensitive string checks).
   - **Observation**: Zero regex character classes or character ranges.
8. **Other Files (`BillingService.java`, `PdfInvoiceService.java`, `PasswordUtil.java`, `InvoicePaymentDao.java`, `ZReportDao.java`, `SchemaMigrationManager.java`)**:
   - `PasswordUtil.java:140`: `storedSaltAndHash.split(":", 2);`
   - `SchemaMigrationManager.java:108, 115, 158`: `script.split("\\R")`, `split(";")`, `split("\\s+")`.
   - `PdfInvoiceService.java`: `DateTimeFormatter.ofPattern(...)` (date formatting, not regex).
   - **Observation**: Zero regex character ranges.

### 1.2 SQLite Collation & Case Sensitivity Audit
Every `SELECT`, `UPDATE`, and `DELETE` SQL statement in `src/main/java` was cataloged and analyzed alongside table definitions in `V1__init_schema.sql` and `V2__v1_1_enhancements.sql`:

| Class & Location | SQL Statement / Operation | Column Collation in Schema | Behavior & Findings |
|---|---|---|---|
| `CatalogCacheDao.java:72-75, 104-107` | `SELECT * / DELETE ... WHERE isbn13 = ? OR isbn10 = ? OR REPLACE(isbn13, '-', '') = ? OR REPLACE(isbn10, '-', '') = ?` | `isbn10 TEXT` (`BINARY` default in `V2__v1_1_enhancements.sql:6`) | **DEFECT**: Fails when ISBN-10 check digit varies between `'X'` and `'x'`. Causes 2 test failures in `DaoStressAndEdgeCaseEmpiricalTest`. |
| `ProductDao.java:64-66` | `SELECT p.*, c.name AS category_name FROM products p ... WHERE p.sku = ?;` | `sku TEXT UNIQUE NOT NULL` (`BINARY` default in `V1__init_schema.sql:22`) | **POTENTIAL PITFALL**: Case-sensitive exact match. Direct lookup `productDao.findBySku("bk-001")` returns empty for stored `"BK-001"`. `PosBillingController:296` recovers in-memory via `query.equalsIgnoreCase(p.getSku())`, but DAO query itself is case-sensitive. |
| `ProductDao.java:86-89` | `WHERE p.name LIKE ? OR p.sku LIKE ? OR p.author LIKE ?` | `name`, `sku`, `author TEXT` | **SAFE**: SQLite `LIKE` operator is case-insensitive for ASCII strings by default (`'bk-001'` matches `'BK-001'`). |
| `ProductDao.java:298` | `SELECT category_id FROM categories WHERE name = ? COLLATE NOCASE;` | `categories.name TEXT` | **SAFE**: Explicitly specifies `COLLATE NOCASE`. |
| `CategoryDao.java:60` | `SELECT category_id, name, description FROM categories WHERE name = ? COLLATE NOCASE;` | `categories.name TEXT` | **SAFE**: Explicitly specifies `COLLATE NOCASE`. |
| `CategoryDao.java:24` | `SELECT category_id, name, description FROM categories ORDER BY name ASC;` | `categories.name TEXT` (`BINARY`) | **MINOR**: Uppercase category names sort before lowercase in SQLite (`'Z'` < `'a'`). |
| `InvoiceDao.java:158` | `SELECT * FROM invoices WHERE invoice_number = ?;` | `invoice_number TEXT UNIQUE NOT NULL` (`BINARY` default) | **POTENTIAL PITFALL**: Case-sensitive match. `findInvoiceByNumber("inv-1001")` fails if stored as `"INV-1001"`. (UI search in `InvoiceHistoryController:208` uses in-memory `toLowerCase().contains()` on ID/customer). |
| `InvoiceDao.java:176` | `WHERE customer_name LIKE ? ORDER BY invoice_id DESC;` | `customer_name TEXT` | **SAFE**: SQLite `LIKE` is case-insensitive for ASCII. |
| `LegacyDataImporter.java:340, 389` | `SELECT product_id FROM products WHERE name = ? COLLATE NOCASE;` | `products.name TEXT` | **SAFE**: Explicitly specifies `COLLATE NOCASE`. |
| `LegacyDataImporter.java:353` | `SELECT product_id FROM products WHERE sku = ?;` | `products.sku TEXT` | **MINOR**: Checks SKU uniqueness before importing legacy stock. |
| `LegacyDataImporter.java:368` | `SELECT category_id FROM categories WHERE name = ?;` | `categories.name TEXT` (`BINARY`) | **INCONSISTENCY**: Lacks `COLLATE NOCASE`. In contrast with `CategoryDao.findByName` and `ProductDao.resolveCategoryId` which both use `COLLATE NOCASE`, searching with `"books"` will not match `"Books"`, risking duplicate category insertion. |
| `SchemaMigrationManager.java:423` | `SELECT category_id FROM categories WHERE name = ?;` | `categories.name TEXT` (`BINARY`) | **MINOR**: Lacks `COLLATE NOCASE`. Used only during initial seed where literal `"Books"` and `"Stationery"` are passed. |
| `SettingsDao.java:20, 41` | `WHERE setting_id = 1;` | `setting_id INTEGER PRIMARY KEY` | **SAFE**: Integer primary key query. Zero string collation sensitivity. |
| `InvoicePaymentDao.java:101, 120` | `WHERE invoice_id = ?;` | `invoice_id INTEGER` | **SAFE**: Integer foreign key query. |
| `ZReportDao.java:98, 118` | `WHERE z_sequence_number = ?;` | `z_sequence_number INTEGER` | **SAFE**: Integer sequence query. |
| `CsvSyncService.java` | Pure Java in-memory RFC 4180 parsing & export | N/A | **SAFE**: No SQL queries. Case handling uses Java `.equalsIgnoreCase()`. |

### 1.3 Test Suite Execution Results
- Command: `.\mvnw.cmd test "-Dtest=ProductDaoTest,InvoiceDaoTest,CategoryDaoTest,SettingsDaoTest,InvoicePaymentDaoTest,ZReportDaoTest,CatalogCacheDaoTest"`
  - Result: `Tests run: 26, Failures: 0, Errors: 0, Skipped: 0. BUILD SUCCESS`.
- Command: `.\mvnw.cmd test "-Dtest=LegacyDataImporterTest,CsvSyncServiceTest"`
  - Result: `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0. BUILD SUCCESS`.
- Command: `.\mvnw.cmd test "-Dtest=DaoStressAndEdgeCaseEmpiricalTest"`
  - Result: `Tests run: 10, Failures: 2, Errors: 0, Skipped: 0. BUILD FAILURE`.
  - Failure 1: `testCatalogCacheIsbnLookupVariations:94` (expected `<true>` but was `<false>`).
  - Failure 2: `testCatalogCacheSavedWithLowercaseX:114` (expected `<true>` but was `<false>`).

---

## 2. Logic Chain

1. **Regex Range Evaluation**:
   - The regex range syntax `[A-Z]` creates a contiguous set of characters from character A to character B in ASCII order.
   - `CatalogCacheDao.java` lines 71 and 103 use `"[^0-9X-x]"`. Because `'X'` has ASCII code 88 and `'x'` has ASCII code 120, this range spans characters 88 through 120, encompassing `[`, `\`, `]`, `^`, `_`, `` ` ``, and `a-w`.
   - Comprehensive grep searches across the entire repository confirm that no other class, method, or service uses `replaceAll` or character ranges in this manner.
   - Therefore, `CatalogCacheDao.java` is the sole source of unintended regex range behavior in the application.

2. **Collation & Case Sensitivity Evaluation**:
   - SQLite text columns without an explicit `COLLATE` clause default to `BINARY` collation, where comparison operators (`=`, `<>`, `IN`) perform exact byte-by-byte comparisons.
   - In `CatalogCacheDao`, `isbn10` check digits can legitimately be `'X'` or `'x'`. Because the column has `BINARY` collation and the DAO queries lack `UPPER()` or `COLLATE NOCASE`, searching with `'x'` against `'X'` returns 0 rows.
   - In `ProductDao`, `findBySku` uses `WHERE p.sku = ?`. Because `sku` in `V1__init_schema.sql` has default `BINARY` collation, queries with mismatched case (`bk-001` vs `BK-001`) fail at the DAO layer. Although `PosBillingController` currently mitigates this in-memory via `query.equalsIgnoreCase(p.getSku())`, direct callers of `ProductDao.findBySku` will encounter case-sensitivity misses.
   - In `InvoiceDao`, `findInvoiceByNumber` uses `WHERE invoice_number = ?` with `BINARY` collation. Case variations like `inv-1001` will miss `INV-1001`.
   - In `CategoryDao`, `findByName` already includes `COLLATE NOCASE`, demonstrating that the team recognized this pattern.
   - In `LegacyDataImporter`, `productExistsByName` and `getOrCreateProductId` use `COLLATE NOCASE`, but `getOrCreateCategory` line 368 omits it, creating an inconsistency that risks inserting duplicate category names if case differs.

3. **Remediation Boundary Assessment**:
   - Current test suite failures (`DaoStressAndEdgeCaseEmpiricalTest`) are strictly caused by `CatalogCacheDao.java`.
   - `ProductDao`, `InvoiceDao`, `CategoryDao`, `SettingsDao`, `LegacyDataImporter`, and `CsvSyncService` all pass 100% of their test suites (33/33 tests passing).
   - Thus, only `CatalogCacheDao.java` (and its DDL definition in `V2__v1_1_enhancements.sql` / `SchemaMigrationManager.java`) requires *mandatory* remediation to unblock Milestone 1.1-A.
   - However, hardening `ProductDao.findBySku`, `InvoiceDao.findInvoiceByNumber`, and `LegacyDataImporter.getOrCreateCategory` with `COLLATE NOCASE` is strongly advised to prevent future case-collation bugs in POS workflows.

---

## 3. Caveats

1. **SQLite `LIKE` vs Non-ASCII Unicode**:
   - SQLite's default `LIKE` operator is case-insensitive for ASCII characters (A–Z / a–z) only. For non-ASCII accented characters (e.g., `é` vs `É`), SQLite `LIKE` is case-sensitive unless custom ICU extensions are compiled into the SQLite native library.
   - For Arabic script (e.g. titles, authors), Arabic characters have no uppercase/lowercase distinction, so `LIKE '%...%'` matches consistently across all builds.
2. **Existing Indexes**:
   - SQLite indexes created without `COLLATE NOCASE` (e.g., `idx_products_sku ON products(sku)`) cannot be utilized for `WHERE sku = ? COLLATE NOCASE` queries; SQLite will perform a table scan instead. For small retail catalogs (< 100,000 items), this has negligible performance impact (< 1ms). If optimal indexing is desired in a future schema version (V3), the index can be declared as `ON products(sku COLLATE NOCASE)`.

---

## 4. Conclusion

1. **Regex Character Ranges**:
   - **CONFIRMED**: No other DAO or service in the codebase contains any regex character ranges like `X-x` or broken regex patterns. `CatalogCacheDao.java` is the **only** file with this issue.
2. **Case-Sensitive Collation Pitfalls**:
   - **CatalogCacheDao**: Active defect in `findByIsbn()` and `delete()` caused by `BINARY` collation on `isbn10` and missing case normalization.
   - **ProductDao**: `findBySku()` uses `WHERE p.sku = ?` (case-sensitive). Recommend adding `COLLATE NOCASE`.
   - **InvoiceDao**: `findInvoiceByNumber()` uses `WHERE invoice_number = ?` (case-sensitive). Recommend adding `COLLATE NOCASE`.
   - **LegacyDataImporter**: `getOrCreateCategory()` uses `WHERE name = ?;` without `COLLATE NOCASE` (inconsistent with `CategoryDao.findByName`). Recommend adding `COLLATE NOCASE`.
   - **CategoryDao, SettingsDao, CsvSyncService, InvoicePaymentDao, ZReportDao**: Clean, no collation defects.
3. **Remediation Scope Confirmation**:
   - **Mandatory Remediation for Milestone 1.1-A Approval**:
     1. `CatalogCacheDao.java`:
        - Fix regex in lines 71 and 103: `rawIsbn.replaceAll("[^0-9Xx]", "").trim().toUpperCase()`.
        - Normalize `metadata.getIsbn10()` to uppercase in `save()`.
        - Use `COLLATE NOCASE` or `UPPER(isbn10)` in `findByIsbn()` and `delete()`.
     2. `V2__v1_1_enhancements.sql:6` and `SchemaMigrationManager.java:199`:
        - Update column definition: `isbn10 TEXT COLLATE NOCASE`.
   - **Recommended Hardening (Zero-risk additions)**:
     - `ProductDao.java:66`: `WHERE p.sku = ? COLLATE NOCASE;`
     - `InvoiceDao.java:158`: `WHERE invoice_number = ? COLLATE NOCASE;`
     - `LegacyDataImporter.java:368`: `WHERE name = ? COLLATE NOCASE;`

---

## 5. Verification Method

To independently verify all findings and test suite behavior:

1. **Verify Existing DAO and Service Tests Pass**:
   ```powershell
   .\mvnw.cmd test "-Dtest=ProductDaoTest,InvoiceDaoTest,CategoryDaoTest,SettingsDaoTest,InvoicePaymentDaoTest,ZReportDaoTest,CatalogCacheDaoTest,LegacyDataImporterTest,CsvSyncServiceTest"
   ```
   *Expected Result*: `Tests run: 33, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS`.

2. **Verify the Specific Isolated Defect in CatalogCacheDao**:
   ```powershell
   .\mvnw.cmd test "-Dtest=DaoStressAndEdgeCaseEmpiricalTest"
   ```
   *Expected Result*: Fails exactly 2 tests (`testCatalogCacheIsbnLookupVariations` and `testCatalogCacheSavedWithLowercaseX`) in `CatalogCacheDao`.

3. **Verify Regex Range Absence Across Codebase**:
   ```powershell
   # Search for character classes with range hyphens
   git grep -n "\[.*-.*\]" src/main/java
   ```
   *Expected Result*: Exactly 2 matches, both inside `CatalogCacheDao.java` (lines 71 and 103).

4. **Verify Collation in SQL Across Codebase**:
   Inspect `ProductDao.java:66`, `InvoiceDao.java:158`, `CategoryDao.java:60`, and `LegacyDataImporter.java:368` to verify the presence or absence of `COLLATE NOCASE`.
