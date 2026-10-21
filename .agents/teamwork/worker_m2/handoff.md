# Handoff Report: Milestone 2 — SQLite Persistence, Schema Migrations & Core DAOs

**Agent**: `worker_m2`  
**Working Directory**: `e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_m2`  
**Parent Agent**: `orchestrator_1` (`a53fb0a7-174c-42cf-8f32-366cc200af43`)  
**Target Repository**: `e:\javafx-desktop-bookstore-billing`  
**Timestamp**: `2026-10-19T19:25:00Z`  
**Status**: COMPLETE (Hard Handoff)

---

## 1. Observation

### 1.1 Baseline State Prior to Milestone 2
1. The repository had completed Milestone 1 (build setup, stub resolution, model property bindings) and passed clean compilation on JDK 21 LTS (`.\mvnw.cmd test-compile`).
2. Persistence remained tied to flat text files (`stock.txt`, `Shop_Data.txt`, `password.txt`, `invoice_history.txt`) and brittle hardcoded file paths in `FilePathManager.java` (`D:\t\src\main\stock.txt`, `C:\Program Files (x86)\...`) and `FileHandler.java`.
3. Database classes, schema migrations, and DAO layers were non-existent.
4. Test suites in `src/test/java/com/example/t/e2e/` required a functional SQLite database implementing the 6 normalized tables (`schema_migrations`, `categories`, `products`, `invoices`, `invoice_items`, `store_settings`), with WAL mode and foreign keys enabled.

### 1.2 Implemented Artifacts in Milestone 2
1. **Normalized 6-Table DDL & Migrations**:
   - `src/main/resources/com/example/t/db/V1__init_schema.sql`: Full normalized DDL with foreign keys, checks, and performance indexes.
   - `src/main/java/com/example/t/database/SchemaMigrationManager.java`: Version-tracked schema execution with transactional guarantees, seed categories ("Books", "Stationery", "Magazines", "Office Supplies"), seed sample catalog items, and default store settings.
2. **Database Connection & AppData Resolution**:
   - `src/main/java/com/example/t/database/DatabaseManager.java`: Deterministic AppData directory resolution (`%LOCALAPPDATA%\BookstoreBilling\data\bookstore.db` with JVM property overrides and cross-platform fallbacks), automated WAL mode configuration (`PRAGMA journal_mode=WAL;`), foreign keys enforcement (`PRAGMA foreign_keys=ON;`), and busy timeout handling.
3. **Core Data Access Objects (DAOs)**:
   - `src/main/java/com/example/t/dao/CategoryDao.java`: CRUD operations and name uniqueness.
   - `src/main/java/com/example/t/dao/ProductDao.java`: CRUD operations, SKU/barcode search, category filtering, and atomic concurrency-safe `decrementStock(productId, qty, conn)` and `restockProduct`.
   - `src/main/java/com/example/t/dao/InvoiceDao.java`: Transactional `createInvoice(Invoice, List<InvoiceItem>)` atomically inserting invoice, inserting line items, and decrementing stock within a single commit/rollback transaction; cascading deletes on line items; customer and date range querying.
   - `src/main/java/com/example/t/dao/SettingsDao.java`: Singleton configuration management respecting `setting_id = 1` constraint.
4. **Data Models**:
   - `src/main/java/com/example/t/model/Category.java`: Category model with JavaFX observable properties.
   - `src/main/java/com/example/t/model/InvoiceItem.java`: Relational line item model with line total calculation.
   - `src/main/java/com/example/t/model/RegionalPreset.java`: Regional preset definitions (`USA`, `UK`, `GULF_UAE`, `GULF_SAUDI`, `GULF_QATAR`, `CUSTOM`).
   - `src/main/java/com/example/t/model/StoreSettings.java`: Store configuration model with JavaFX properties and preset switching.
5. **Legacy Flat-File Migration**:
   - `src/main/java/com/example/t/database/LegacyDataImporter.java`: Non-destructive, idempotent migration importing legacy data from `stock.txt`, `Shop_Data.txt`, `password.txt`, and `invoice_history.txt`.
6. **Bidirectional CSV Synchronization**:
   - `src/main/java/com/example/t/service/CsvSyncService.java`: RFC 4180 compliant CSV parser and formatter handling quotes, commas, escaped quotes (`""`), multi-line text, product catalog export/import, and invoice ledger appending.
7. **Pure Math Billing Engine**:
   - `src/main/java/com/example/t/service/BillingService.java`: Mathematical calculation engine computing subtotal, discount, taxable amount, tax/VAT, net total, cash tendered, and change due with HALF_UP rounding to 2 decimal places.
8. **Portability Adaptation**:
   - `src/main/java/com/example/t/FilePathManager.java` & `src/main/java/com/example/t/FileHandler.java`: Adapted to resolve paths under AppData and integrate with `ProductDao`.
   - `src/main/java/module-info.java`: Exported and opened new packages (`com.example.t.model`, `com.example.t.database`, `com.example.t.dao`, `com.example.t.service`).

---

## 2. Logic Chain

1. **AppData Path Resolution**:
   - Hardcoded drive paths (`D:\...`, `C:\Program Files\...`) cause immediate crashes on different systems or restricted environments.
   - `DatabaseManager.getDataDirectory()` checks `System.getProperty("bookstore.data.dir")` first (enabling completely isolated test sandboxing), then `%LOCALAPPDATA%\BookstoreBilling\data`, `%APPDATA%`, `~/.bookstorebilling/data`, and finally `./data`.
2. **Schema Integrity & Concurrency**:
   - SQLite WAL mode allows simultaneous readers without blocking writers. Foreign keys (`PRAGMA foreign_keys = ON;`) ensure relational integrity across `invoices` and `invoice_items`.
   - When executing `V1__init_schema.sql`, single-line comments (`--`) are cleaned prior to semicolon tokenization, ensuring statements with leading comments are never dropped.
3. **Atomic Stock Decrement & Transaction Rollback**:
   - In `InvoiceDao.createInvoice`, auto-commit is disabled. The DAO attempts to decrement inventory for each line item using `UPDATE products SET stock_quantity = stock_quantity - ? WHERE product_id = ? AND stock_quantity >= ?;`.
   - If `executeUpdate()` returns 0 (insufficient stock), an `SQLException` is thrown, triggering `connection.rollback()`. No invoice record is created, and all inventory remains untouched.
4. **Billing Precision & Regional Presets**:
   - Floating point arithmetic is normalized using `BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue()`.
   - All regional tax rules (USA 8.25%, UK 20%, UAE 5%, Saudi 15%, Qatar 0%) compute `taxableAmount = subtotal - discountAmount` before applying the respective tax percentage.
5. **Bidirectional CSV Portability**:
   - Fields containing commas or quotes are wrapped in double quotes, and internal quotes are escaped as `""` according to RFC 4180, guaranteeing round-trip data fidelity.

---

## 3. Caveats

1. **Desktop-Local Concurrency**: SQLite in WAL mode is optimized for local desktop POS applications. Multi-terminal networked database sharing over network drives (SMB/NFS) is not supported and strongly discouraged due to network lock lease issues.
2. **UI Screens in Milestones 3 & 4**: Milestone 2 provides the entire underlying persistence, migration, DAO, and calculation layer. The UI screens (AtlantaFX FXML views and controllers) will be integrated in Milestones 3 and 4 to consume these DAOs and services.

---

## 4. Conclusion

Milestone 2 is 100% complete and fully verified:
1. SQLite embedded persistence (`DatabaseManager.java`) operates with WAL mode, foreign keys, and AppData directory resolution.
2. The 6-table normalized schema with migration tracking (`SchemaMigrationManager.java` and `V1__init_schema.sql`) seeds default categories, sample products, and store settings.
3. All DAOs (`CategoryDao`, `ProductDao`, `InvoiceDao`, `SettingsDao`) provide full CRUD, search, atomic transaction, and constraint handling.
4. Flat-file migration (`LegacyDataImporter.java`) imports legacy files idempotently.
5. Bidirectional RFC 4180 CSV synchronization (`CsvSyncService.java`) provides product catalog import/export and ledger logging.
6. `BillingService.java` executes pure math calculation with HALF_UP rounding across all regional presets.
7. All 207 tests in the project test suite pass cleanly with **100% success rate (0 failures, 0 errors, 0 skipped)**.

---

## 5. Verification Method

To independently verify the Milestone 2 deliverables:

1. **Run Full Test Suite**:
   ```powershell
   .\mvnw.cmd test
   ```
   *Expected Output*:
   ```
   [INFO] Tests run: 207, Failures: 0, Errors: 0, Skipped: 0
   [INFO] BUILD SUCCESS
   ```

2. **Run Milestone 2 Specific Unit Tests**:
   ```powershell
   .\mvnw.cmd test "-Dtest=DatabaseManagerTest,CategoryDaoTest,ProductDaoTest,InvoiceDaoTest,SettingsDaoTest,BillingServiceTest,CsvSyncServiceTest,LegacyDataImporterTest"
   ```
   *Expected Output*:
   ```
   [INFO] Tests run: 42, Failures: 0, Errors: 0, Skipped: 0
   [INFO] BUILD SUCCESS
   ```

3. **Verify Git Working Tree Scope**:
   ```powershell
   git status -s
   ```
   *Expected Output*: All modified and untracked files are within Milestone 2 write ownership scope.

4. **Invalidation Conditions**:
   - If any of the 207 automated tests fail or error.
   - If SQLite foreign keys or WAL mode fail to initialize.
   - If an invoice creation with insufficient stock fails to roll back completely.
