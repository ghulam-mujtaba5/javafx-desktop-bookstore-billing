## 2026-10-20T21:11:46Z
You are Worker 1.1-A (Infrastructure and Persistence Worker).
Your working directory is: e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_v1_1_m1a
Your parent orchestrator is conversation ID: 36ceb3be-97cf-46d3-97ed-e1b4ef0f155d

Read the authoritative user request at:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\ORIGINAL_REQUEST.md (specifically the section under ## 2026-10-20T19:51:37Z)
Also read:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\PROJECT.md
And the explorer handoff reports:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_v1_1_survey_1\handoff.md
e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_v1_1_survey_2\handoff.md
e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_v1_1_survey_3\handoff.md

## Scope & Objective
Implement Milestone 1.1-A: Baseline Fix, Schema Migration V2 & Persistence Infrastructure:
1. Fix `RegionalPreset.java`: In `RegionalPreset.GULF_UAE`, change the currency symbol argument from `"AED"` to `"د.إ"` so that the Arabic currency symbol expectation in `FinalAcceptanceCriteriaVerificationTest.java:528` passes cleanly. Verify all 398 existing tests pass!
2. In `src/main/java/module-info.java`: add `requires java.net.http;` so Java 21's standard HTTP client is available for subsequent metadata enrichment without compilation errors.
3. In `src/main/java/com/example/t/database/DatabaseManager.java`: add `public static Path getCoversDirectory()` resolving `%LOCALAPPDATA%\BookstoreBilling\data\covers` (using `ensureDirectory`).
4. In `src/main/resources/com/example/t/db/V2__v1_1_enhancements.sql`: create the V2 SQLite migration script adding:
   - `catalog_cache` table (isbn13 PRIMARY KEY, isbn10, title, subtitle, authors, publisher, published_year, edition, format, genre, description, page_count, cover_image_path, cover_url, source, cached_at, raw_json) + indexes on isbn10, title, and products(isbn).
   - `invoice_payments` table (payment_id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_id INTEGER NOT NULL REFERENCES invoices(invoice_id) ON DELETE CASCADE, payment_type TEXT NOT NULL, amount REAL NOT NULL, tendered_amount REAL DEFAULT 0.0, change_due REAL DEFAULT 0.0, reference_info TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP) + index on invoice_id.
   - `financial_z_reports` table (z_report_id INTEGER PRIMARY KEY AUTOINCREMENT, z_sequence_number INTEGER UNIQUE NOT NULL, report_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, period_start TIMESTAMP NOT NULL, period_end TIMESTAMP NOT NULL, opening_float REAL DEFAULT 0.0, total_cash_sales REAL NOT NULL, total_card_sales REAL NOT NULL, total_credit_sales REAL NOT NULL, gross_sales REAL NOT NULL, total_discount REAL NOT NULL, taxable_sales REAL NOT NULL, total_tax REAL NOT NULL, net_sales REAL NOT NULL, transaction_count INTEGER NOT NULL, expected_drawer_cash REAL NOT NULL, actual_drawer_cash REAL NOT NULL, cash_over_short REAL NOT NULL, cumulative_grand_total REAL NOT NULL, closed_by_user TEXT DEFAULT 'admin', notes TEXT).
   - column additions to `invoice_items` (`discount_percent REAL DEFAULT 0.0`, `discount_amount REAL DEFAULT 0.0`, `notes TEXT`) if not already present.
5. In `src/main/java/com/example/t/database/SchemaMigrationManager.java`: update `CURRENT_SCHEMA_VERSION = 2` and ensure migration V2 executes properly and is recorded in `schema_migrations`.
6. Implement Models and DAOs:
   - `com.example.t.model.BookMetadata`
   - `com.example.t.model.PaymentTender` (enum/class with type, amount, tendered, change)
   - `com.example.t.model.InvoicePayment`
   - `com.example.t.model.ZReport`
   - `com.example.t.dao.CatalogCacheDao` (save, findByIsbn, delete, getAll)
   - `com.example.t.dao.InvoicePaymentDao` (savePayments, getPaymentsByInvoiceId)
   - `com.example.t.dao.ZReportDao` (saveZReport, getLatestZReport, getZReportBySequence, getAllZReports, getNextSequenceNumber)
7. In `src/test/java/com/example/t/PackagingEmpiricalChallengeTest.java` (line 298): allow classpath matching for `t-1.1.0.jar` as well as `t-1.0-SNAPSHOT.jar`.
8. Create comprehensive unit tests: `src/test/java/com/example/t/database/SchemaMigrationV2Test.java` and DAO unit tests verifying that all tables, columns, indexes, and CRUD operations function properly.

## MANDATORY INTEGRITY WARNING
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Run `./mvnw.cmd test` and document build and test results.
Write your handoff report to:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_v1_1_m1a\handoff.md
Send a completion message back to parent (36ceb3be-97cf-46d3-97ed-e1b4ef0f155d).
