# Handoff Report: Milestone 2 — Empirical Adversarial Persistence & Concurrency Challenge

**Agent**: `challenger_m2_1`  
**Working Directory**: `e:\javafx-desktop-bookstore-billing\.agents\teamwork\challenger_m2_1`  
**Parent Agent**: `orchestrator_1` (`a53fb0a7-174c-42cf-8f32-366cc200af43`)  
**Target Repository**: `e:\javafx-desktop-bookstore-billing`  
**Timestamp**: `2026-10-19T19:36:30Z`  
**Verdict**: **APPROVE** (Hard Handoff)

---

## 1. Observation

1. **Adversarial Challenge Test Suite Created**:
   - Written to: `src/test/java/com/example/t/Milestone2AdversarialChallengeTest.java`
   - Covers 11 dedicated stress, boundary, and concurrency tests across the 4 mandate dimensions:
     1. `testSingleProductStockExhaustionRollback`
     2. `testMultiItemOrderRollsBackPriorDecrementedStock`
     3. `testConcurrentInventoryOversellPrevention`
     4. `testCascadeDeleteInvoiceRemovesLineItems`
     5. `testCategoryDeletionWithExistingProductsHandledPerConstraint`
     6. `testInvalidForeignKeyInsertionRejected`
     7. `testWalModeConcurrentReadersWhileWriterActive`
     8. `testWalContinuousReadersStress`
     9. `testStoreSettingsRejectsInvalidSettingId`
     10. `testStoreSettingsRejectsSecondRowWithSameId`
     11. `testSettingsDaoUpdatesSingletonRowInPlace`

2. **Empirical Execution Results**:
   - Command: `.\mvnw.cmd test "-Dtest=Milestone2AdversarialChallengeTest"`
   - Output:
     ```
     [INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.657 s -- in com.example.t.Milestone2AdversarialChallengeTest
     [INFO] Results:
     [INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
     [INFO] BUILD SUCCESS
     ```
   - Combined Persistence Suite (`Milestone2AdversarialChallengeTest,DatabaseManagerTest,CategoryDaoTest,ProductDaoTest,InvoiceDaoTest,SettingsDaoTest`):
     ```
     [INFO] Tests run: 33, Failures: 0, Errors: 0, Skipped: 0
     [INFO] BUILD SUCCESS
     ```

3. **Verbatim Findings Across Challenge Dimensions**:
   - **Transaction Atomicity**:
     - When purchasing an out-of-stock product, `InvoiceDao.createInvoice` throws `SQLException("Insufficient stock for product ID: ...")` and triggers `connection.rollback()`.
     - Zero orphaned rows are created in `invoices` or `invoice_items`.
     - When a multi-item invoice fails on the 3rd item after successfully decrementing items 1 and 2, rollback restores items 1 and 2 to their exact pre-transaction stock quantities.
     - Under a 10-thread concurrent purchasing race on 5 units of stock (2 units per transaction), exactly 2 transactions succeeded (4 units deducted, leaving 1 unit), remaining 8 failed cleanly, and 0 negative inventory was produced.
   - **Foreign Key Integrity & Cascades**:
     - Deleting an invoice (`invoiceDao.deleteInvoice(id)`) automatically removes all child rows in `invoice_items` via `ON DELETE CASCADE`.
     - Deleting a category with associated products executes `ON DELETE SET NULL`: category is removed, referencing products remain intact with `products.category_id = NULL`, and `PRAGMA foreign_key_check` reports 0 violations across the entire database.
     - Inserting an `invoice_item` with an invalid `invoice_id` or `product_id` is immediately rejected with an `SQLException` foreign key constraint failure.
   - **WAL Mode Concurrent Readers**:
     - While an active writer connection has an uncommitted transaction holding modified data (`INSERT INTO products ...`), 3 simultaneous reader connections executed queries against `products` and `categories` without encountering any `SQLITE_BUSY` or "database is locked" errors.
     - Snapshot isolation was verified: readers saw consistent data prior to the uncommitted write; once committed, the new record became visible.
     - A continuous reader stress test (5 readers running loops alongside continuous writer inserts) completed with 0 errors.
   - **Store Settings Singleton Constraint**:
     - Attempting raw SQL inserts with `setting_id = 2` or `setting_id = 0` failed with SQLite `CHECK constraint failed: store_settings`.
     - Attempting to insert a second row with `setting_id = 1` failed with SQLite `UNIQUE constraint failed: store_settings.setting_id`.
     - `SettingsDao.saveSettings(...)` updates the singleton row in-place, keeping total row count strictly at 1.

---

## 2. Logic Chain

1. **Transaction Atomicity Proof**:
   - `InvoiceDao.java` explicitly turns off auto-commit (`conn.setAutoCommit(false);`) and attempts conditional stock decrement:
     `UPDATE products SET stock_quantity = stock_quantity - ?, updated_at = CURRENT_TIMESTAMP WHERE product_id = ? AND stock_quantity >= ?;`
   - If `executeUpdate()` returns 0, an `SQLException` is thrown, entering the `catch (SQLException e)` block which invokes `conn.rollback()`.
   - Empirically proven: Both single-item and multi-item orders rollback all intermediate updates completely, leaving both `invoices` and `invoice_items` at initial counts.
2. **Relational Integrity Proof**:
   - In `V1__init_schema.sql`, line 60 specifies `invoice_id INTEGER NOT NULL REFERENCES invoices(invoice_id) ON DELETE CASCADE`.
   - SQLite foreign keys are verified enabled on every connection (`PRAGMA foreign_keys = ON;`).
   - Line 24 specifies `category_id INTEGER REFERENCES categories(category_id) ON DELETE SET NULL`. Deleting a category safely sets the foreign key to NULL without corrupting product catalog rows or violating foreign key constraints.
3. **Concurrency Proof**:
   - `DatabaseManager.configurePragmas` applies `PRAGMA journal_mode = WAL;` and `PRAGMA busy_timeout = 5000;`.
   - Under SQLite WAL mode, readers do not block writers and writers do not block readers. The active uncommitted writer test proved that readers obtain an isolated snapshot without timeout or lock exceptions.
4. **Singleton Constraint Proof**:
   - In `V1__init_schema.sql`, line 69 specifies `setting_id INTEGER PRIMARY KEY CHECK (setting_id = 1)`.
   - SQLite enforces CHECK constraints on row insertion; any integer other than 1 is rejected by the CHECK constraint. An existing row with 1 cannot be duplicated due to PRIMARY KEY uniqueness.

---

## 3. Caveats

1. **Network Drives**: SQLite WAL mode assumes local filesystem POSIX/Win32 file locking semantics and shared memory (`.shm` file). Deployment across network shares (SMB/NFS) is not supported, which is consistent with the offline desktop app requirements.
2. **Peer Challenger (challenger_m2_2)**: A separate peer challenger is verifying billing calculations and CSV sync (`BillingAndCsvAdversarialTest.java`), which noted independent CSV test issues within their separate scope. Those do not affect the persistence, DAO, or database layer verified here.

---

## 4. Conclusion

The Milestone 2 persistence and transaction layer passes all empirical challenges without failure or regression:
- Transaction atomicity and rollback: **VERIFIED**
- Foreign key cascade and constraint handling: **VERIFIED**
- WAL mode concurrent reader non-blocking behavior: **VERIFIED**
- Store settings singleton constraint: **VERIFIED**

**Final Verdict**: **APPROVE**

---

## 5. Verification Method

To reproduce and verify these findings independently:

```powershell
# 1. Run the Milestone 2 empirical challenge test suite
.\mvnw.cmd test "-Dtest=Milestone2AdversarialChallengeTest"

# 2. Run the combined persistence unit and challenge test suites
.\mvnw.cmd test "-Dtest=Milestone2AdversarialChallengeTest,DatabaseManagerTest,CategoryDaoTest,ProductDaoTest,InvoiceDaoTest,SettingsDaoTest"
```

Expected output:
- `Milestone2AdversarialChallengeTest`: 11 tests run, 0 failures, 0 errors, 0 skipped.
- Combined suite: 33 tests run, 0 failures, 0 errors, 0 skipped.
- `BUILD SUCCESS`
