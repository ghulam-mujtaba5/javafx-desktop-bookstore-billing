## 2026-10-19T19:06:41Z

You are worker_m2, implementing Milestone 2 (SQLite Persistence, Schema Migrations & Core DAOs) for e:\javafx-desktop-bookstore-billing.
Your working directory is: e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_m2
Your parent is: orchestrator_1 (conversation ID: a53fb0a7-174c-42cf-8f32-366cc200af43)

MANDATORY FIRST STEP: Read the authoritative requirements at:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\ORIGINAL_REQUEST.md
and the project architecture at:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\PROJECT.md
and survey reports at:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_survey_3\handoff.md
e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_m1\handoff.md

Your exclusive write ownership:
- src/main/java/com/example/t/database/DatabaseManager.java
- src/main/java/com/example/t/database/SchemaMigrationManager.java
- src/main/java/com/example/t/database/LegacyDataImporter.java
- src/main/resources/com/example/t/db/V1__init_schema.sql (and any migration scripts)
- src/main/java/com/example/t/dao/CategoryDao.java
- src/main/java/com/example/t/dao/ProductDao.java
- src/main/java/com/example/t/dao/InvoiceDao.java
- src/main/java/com/example/t/dao/SettingsDao.java
- src/main/java/com/example/t/model/Category.java
- src/main/java/com/example/t/model/InvoiceItem.java
- src/main/java/com/example/t/model/StoreSettings.java
- src/main/java/com/example/t/model/RegionalPreset.java
- src/main/java/com/example/t/service/BillingService.java
- src/main/java/com/example/t/service/CsvSyncService.java
- src/main/java/com/example/t/FilePathManager.java and FileHandler.java (adapt to use AppData resolution and DatabaseManager rather than hardcoded D:\ and C:\ paths)
- Automated unit tests in src/test/java/com/example/t/dao/ and src/test/java/com/example/t/service/

Specific Milestone 2 deliverables:
1. SQLite Connection & AppData Resolution (`DatabaseManager.java`):
   - Resolve database directory using %LOCALAPPDATA%\BookstoreBilling\data\bookstore.db on Windows (with graceful fallbacks for dev/test).
   - Configure SQLite connection with WAL mode (`PRAGMA journal_mode=WAL;`), foreign keys enabled (`PRAGMA foreign_keys=ON;`), and busy timeout.
2. 6-Table Normalized SQLite Schema & Migrations (`SchemaMigrationManager.java`):
   - Tables: `schema_migrations`, `categories`, `products`, `invoices`, `invoice_items`, `store_settings`.
   - Seed default categories: Books, Stationery, Magazines, Office Supplies.
   - Seed sample catalog items with Books (author, publisher, edition, ISBN) and Stationery.
   - Seed default store settings (USA / USD / Sales tax preset).
3. Core Data Access Objects (DAOs):
   - `ProductDao`: CRUD operations, search by SKU/barcode, search by name/category, atomic `decrementStock(productId, qty, conn)`.
   - `CategoryDao`: CRUD, list all categories.
   - `InvoiceDao`: Atomic `createInvoice(Invoice, List<InvoiceItem>)` inserting invoice and items within a single transaction, fetching invoices by ID, date range, or customer.
   - `SettingsDao`: Singleton store settings getter and updater.
4. Legacy Flat-File Migration (`LegacyDataImporter.java`):
   - Idempotent import from legacy files (`stock.txt`, `Shop_Data.txt`, `password.txt`, `invoice_history.txt`).
   - Graceful detection: if bookstore.db is newly created, automatically import legacy data if files exist.
5. Bidirectional CSV Sync (`CsvSyncService.java`):
   - Export products to RFC 4180 CSV, import products from CSV with quote/comma escaping.
   - Export invoice history to CSV.
6. Core Calculation Engine (`BillingService.java`):
   - Pure math engine: calculate order subtotal, item discount, taxable amount, multi-regional tax/VAT (USA sales tax, UK 20% VAT, Gulf 5%-15% VAT), final net total, tendered cash validation, and change due. Half-up rounding to 2 decimal places.
7. Automated Unit Tests:
   - Automated unit tests covering all DAOs (CRUD, foreign key cascades, transaction rollbacks).
   - Automated unit tests covering BillingService math across all regional presets.
8. Verify Build:
   - Run `.\mvnw.cmd test` to ensure both M1 and M2 unit tests and E2E test suites compile and pass cleanly!
