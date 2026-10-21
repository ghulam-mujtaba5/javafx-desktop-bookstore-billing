# Handoff Report: Milestone 1.1-A (Schema Migration V2 & Persistence Infrastructure Adversarial Challenge)

**Challenger**: Challenger 1 (Migration & Schema Robustness Challenger)  
**Verdict**: `APPROVE`

---

## 1. Observation

1. **Empirical Adversarial Test Suite Execution**:
   - Implemented an adversarial challenge suite at `src/test/java/com/example/t/database/SchemaMigrationV2AdversarialTest.java` containing 10 stress tests targeting schema upgrade, idempotency, foreign key enforcement, uniqueness, query planner index usage, and column preservation.
   - Executed via Maven:
     ```powershell
     .\mvnw.cmd test "-Dtest=SchemaMigrationV2AdversarialTest"
     ```
   - Verbatim test output:
     ```
     [INFO] Running com.example.t.database.SchemaMigrationV2AdversarialTest
     [INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.736 s -- in com.example.t.database.SchemaMigrationV2AdversarialTest
     [INFO] 
     [INFO] Results:
     [INFO] 
     [INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
     [INFO] ------------------------------------------------------------------------
     [INFO] BUILD SUCCESS
     [INFO] Total time:  11.348 s
     ```

2. **Fresh Database Migration (v0 -> v2)**:
   - Evaluated in `SchemaMigrationV2AdversarialTest.testFreshMigrationRecordsBothVersionsAndSeedsCatalog`:
     - Starting from an uninitialized database (version 0), `SchemaMigrationManager.migrate(conn)` executed both V1 and V2 migrations.
     - Table `schema_migrations` contains both version 1 (`"Initial 6-table normalized schema and default seed catalog"`) and version 2 (`"V2 enhancements: catalog_cache, invoice_payments, financial_z_reports, and invoice_items additions"`).
     - Standard categories (4 categories: Books, Stationery, Magazines, Office Supplies) and 6 sample products are properly seeded.
     - Tables `catalog_cache`, `invoice_payments`, `financial_z_reports`, and modified `invoice_items` are created.

3. **Upgrade Migration from V1 with Realistic Existing Data**:
   - Evaluated in `SchemaMigrationV2AdversarialTest.testUpgradePreservesExistingDataAndCustomSettings`:
     - Pre-populated a V1 database with 100 products, 50 invoices, 100 invoice items, and custom store settings (`"Custom London Books"`, `GBP`, `£`, `VAT 20.0%`).
     - Upgraded from version 1 to version 2 via `SchemaMigrationManager.migrate(conn)`.
     - Output confirmed:
       - 100/100 products preserved.
       - 50/50 invoices preserved.
       - 100/100 invoice items preserved with their original columns (`item_id`, `invoice_id`, `product_id`, `quantity`, `unit_price`, `line_total`) intact, and new columns (`discount_percent`, `discount_amount`, `notes`) populated with default values (`0.0`, `0.0`, `null`).
       - Custom store settings (`"Custom London Books"`, `£`, `20.0%`) were NOT overwritten by seed logic.

4. **Idempotency Across 10 Repeated Executions**:
   - Evaluated in `SchemaMigrationV2AdversarialTest.testMigrationHighRepetitionIdempotency`:
     - Repeated `SchemaMigrationManager.migrate(conn)` across 10 consecutive iterations.
     - Iterations 2 through 10 returned `false` (no-op) without error.
     - `schema_migrations` maintained exactly 2 version entries without duplicate key errors.

5. **Table Schema Constraints & Foreign Key Cascading**:
   - Evaluated in `SchemaMigrationV2AdversarialTest.testInvoicePaymentForeignKeyCascadeDelete`:
     - Inserting an `invoice_payments` row referencing nonexistent `invoice_id = 9999` threw `SQLException: [SQLITE_CONSTRAINT_FOREIGNKEY] FOREIGN KEY constraint failed`.
     - Deleting parent invoice `INV-CASCADE-01` automatically cascaded and deleted all 3 child payments from `invoice_payments`.
   - Evaluated in `SchemaMigrationV2AdversarialTest.testFinancialZReportsConstraints`:
     - Inserting a duplicate `z_sequence_number = 1001` into `financial_z_reports` threw `SQLException: [SQLITE_CONSTRAINT_UNIQUE] UNIQUE constraint failed: financial_z_reports.z_sequence_number`.
     - Inserting a `NULL` `z_sequence_number` threw `SQLException: [SQLITE_CONSTRAINT_NOTNULL] NOT NULL constraint failed: financial_z_reports.z_sequence_number`.
   - Evaluated in `SchemaMigrationV2AdversarialTest.testCatalogCacheConstraints`:
     - Inserting duplicate primary key `isbn13 = '978-0132350884'` threw `SQLException: [SQLITE_CONSTRAINT_PRIMARYKEY] UNIQUE constraint failed: catalog_cache.isbn13`.
     - Inserting `title = NULL` threw `SQLException: [SQLITE_CONSTRAINT_NOTNULL] NOT NULL constraint failed: catalog_cache.title`.
     - Saving null or blank `isbn13` via `CatalogCacheDao` threw `IllegalArgumentException`.

6. **Query Planner Index Utilization**:
   - Evaluated in `SchemaMigrationV2AdversarialTest.testIndexesAreUtilizedByQueryPlanner`:
     - `EXPLAIN QUERY PLAN SELECT * FROM catalog_cache WHERE isbn10 = ?;` confirmed utilization of `idx_catalog_cache_isbn10`.
     - `EXPLAIN QUERY PLAN SELECT * FROM invoice_payments WHERE invoice_id = ?;` confirmed utilization of `idx_invoice_payments_invoice`.
     - `EXPLAIN QUERY PLAN SELECT * FROM financial_z_reports WHERE z_sequence_number = ?;` confirmed utilization of `idx_financial_z_reports_seq`.

7. **Invoice Items Column Preservation**:
   - Evaluated in `SchemaMigrationV2AdversarialTest.testInvoiceItemsColumnsIntegrity`:
     - Existing columns (`item_id`, `invoice_id`, `product_id`, `quantity`, `unit_price`, `line_total`) were preserved.
     - Both legacy-style inserts (without discount/notes) and V2-style inserts (with `discount_percent`, `discount_amount`, `notes`) succeeded and mapped data cleanly.

8. **Hardening Observations**:
   - `SchemaMigrationManager.java:77`: `getCurrentVersion(Connection)` executes `SELECT MAX(version) FROM schema_migrations;` without checking if `schema_migrations` exists. Calling `getCurrentVersion()` directly on a raw database before `migrate()` throws `SQLiteException: no such table: schema_migrations`.
   - `V2__v1_1_enhancements.sql:4`: `isbn13 TEXT PRIMARY KEY` in SQLite allows `NULL` unless declared `NOT NULL` (a well-known SQLite quirk); `CatalogCacheDao` protects this at application level, but future DDL should declare `TEXT PRIMARY KEY NOT NULL`.
   - `V2__v1_1_enhancements.sql:5`: `isbn10 TEXT` uses SQLite `BINARY` collation. As discovered by Challenger 2, case-insensitive comparison (`COLLATE NOCASE`) in DDL or upper-casing in DAO is required for ISBN-10 check digits ('x' vs 'X').

---

## 2. Logic Chain

1. **Migration Execution Logic**:
   - `SchemaMigrationManager.migrate(conn)` checks `getCurrentVersion(conn)` against `CURRENT_SCHEMA_VERSION = 2`.
   - For fresh databases ($V < 1$), it applies V1 schema, seeds default catalog data, and records version 1. Then it proceeds to $V < 2$, executes V2 DDL, and records version 2.
   - For existing V1 databases ($V = 1$), it skips V1 and seed logic entirely, directly applying V2 DDL without overwriting existing products or custom store configurations.
   - Both pathways commit in a single transaction with rollback protection.

2. **Idempotency & Column Addition Safety**:
   - Table and index creations utilize `IF NOT EXISTS`.
   - For `ALTER TABLE invoice_items ADD COLUMN`, `SchemaMigrationManager.handleAlterTableInvoiceItems` interrogates `PRAGMA table_info(invoice_items)`. If a column already exists (e.g. from partial prior attempts), it skips the `ALTER TABLE` statement, preventing SQLite `duplicate column name` exceptions.

3. **Relational Constraints & Performance**:
   - Foreign key cascading is guaranteed by `DatabaseManager.configurePragmas` executing `PRAGMA foreign_keys = ON;`.
   - Child records in `invoice_payments` are deleted when the parent invoice is removed, preventing orphaned payment records.
   - Unique constraints on `z_sequence_number` ensure daily register audit logs cannot be overwritten or duplicated.
   - Explicit indexes on foreign keys and search keys (`isbn10`, `title`, `products(isbn)`, `invoice_id`, `z_sequence_number`) are actively selected by the SQLite query planner.

---

## 3. Caveats

- **Cross-Component Finding**:
  - Challenger 2 (`challenger_v1_1_m1a_2`) identified an issue in `CatalogCacheDao.findByIsbn` and `delete` regarding case-sensitive matching of ISBN-10 check digit `'x'` vs `'X'`.
  - While this is an application DAO layer defect rather than a DDL migration failure, future schema updates should consider specifying `COLLATE NOCASE` on `isbn10` and `isbn13` columns as defense-in-depth.

---

## 4. Conclusion

- **Verdict**: `APPROVE`
- The Schema Migration V2 implementation is robust, transactional, and fully idempotent.
- Fresh database initialization (v0 -> v2) and upgrade path (v1 -> v2) function flawlessly without corrupting existing data.
- Foreign keys, cascading deletes, unique constraints, and indexes operate as specified.
- All 10 empirical tests in `SchemaMigrationV2AdversarialTest` pass cleanly.
- Minor recommendations noted for `getCurrentVersion()` defensive handling and `COLLATE NOCASE` in future schema versions.

---

## 5. Verification Method

To independently verify all findings and assertions:

1. **Execute the Empirical Adversarial Challenge Test Suite**:
   ```powershell
   .\mvnw.cmd test "-Dtest=SchemaMigrationV2AdversarialTest"
   ```
   *Expected Result*: `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.

2. **Execute Full Migration & Persistence Test Suite**:
   ```powershell
   .\mvnw.cmd test "-Dtest=SchemaMigrationV2Test,SchemaMigrationV2AdversarialTest,InvoicePaymentDaoTest,ZReportDaoTest"
   ```
   *Expected Result*: All 21 tests pass with 0 failures and 0 errors.

3. **Inspect the Adversarial Test Class**:
   - File: `src/test/java/com/example/t/database/SchemaMigrationV2AdversarialTest.java`
