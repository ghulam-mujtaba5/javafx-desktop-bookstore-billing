# Comprehensive End-to-End Test Infrastructure (TEST_INFRA)

## 1. Test Philosophy

The Bookstore & Stationery POS Billing application follows a **Dual-Track Testing Architecture** where the E2E verification suite is designed and maintained concurrently with the system implementation milestones.

### Key Principles:
1. **Opaque-Box Verification**: The test suite evaluates system behavior from an external perspective, verifying observable state mutations in embedded SQLite tables (`bookstore.db`), RFC 4180 CSV ledgers, receipt documents, and pure mathematical calculation results.
2. **Deterministic Mathematical Oracle**: All expected values for pricing, discounts, multi-regional sales tax / VAT, and cash change are derived strictly from the formal equations specified in `PROJECT.md` and `ORIGINAL_REQUEST.md`:
   - $\text{subtotal} = \sum (\text{unit\_price} \times \text{quantity})$
   - $\text{discount\_amount} = \text{subtotal} \times \left(\frac{\text{discount\_percent}}{100.0}\right)$
   - $\text{taxable\_amount} = \text{subtotal} - \text{discount\_amount}$
   - $\text{tax\_amount} = \text{taxable\_amount} \times \left(\frac{\text{tax\_rate}}{100.0}\right)$
   - $\text{net\_total} = \text{taxable\_amount} + \text{tax\_amount}$
   - $\text{change\_due} = \text{cash\_tendered} - \text{net\_total}$
   - *Monetary Precision Rule*: All intermediate calculations and final currency amounts are rounded half-up to exactly 2 decimal places ($0.01$).
3. **Complete Environmental Isolation**: Tests execute in hermetic sandboxes using isolated temporary SQLite databases and temporary directories. No test mutates production `%LOCALAPPDATA%` paths or leaves persistent residue.
4. **Adversarial & Regression Defense**: Tests explicitly verify fixes for historical defects (such as the legacy semester project's double-discount calculation bug, duplicate stock ID insertions, and unescaped CSV parsing).

---

## 2. Feature Inventory

The test suite systematically verifies all 8 core functional areas defined in `PROJECT.md`:

| Feature # | Feature Name | Target Scope | Interface / Contract |
|---|---|---|---|
| **F1** | **Product & Catalog Management** | Add, update, delete, barcode/SKU lookup, Books vs Stationery classification | `ProductDao`, `Product`, `Category` |
| **F2** | **Stock Update & Inventory Visibility** | Restock, atomic stock decrement, low-stock threshold warning, out-of-stock guards | `ProductDao.decrementStock`, stock guards |
| **F3** | **POS Billing Calculations** | Multi-item subtotal, tax math, tendered cash validation, change calculation | `BillingService.calculateOrder` |
| **F4** | **Discounting Engine** | 0%–100% discount, single-application guarantee (eliminating double-discount bug) | `BillingService`, `CalculationResult` |
| **F5** | **Multi-Regional Tax & Currency Switching** | Presets for USA (USD / Sales Tax), UK (GBP / VAT), UAE (AED / 5% VAT), Saudi (SAR / 15% VAT), Qatar (QAR / 0% VAT) | `StoreSettings`, `RegionalPreset` |
| **F6** | **Invoicing & Ledger History** | Transaction recording, line item persistence, invoice ID lookup, date/customer search | `InvoiceDao`, `Invoice`, `InvoiceItem` |
| **F7** | **SQLite Persistence & Schema Migrations** | 6-table normalized schema, WAL mode, foreign keys, transaction rollbacks | `DatabaseManager`, SQLite DDL |
| **F8** | **Bidirectional CSV Synchronization** | RFC 4180 compliant export/import, quote/comma escaping, round-trip fidelity | `CsvSyncService` |

---

## 3. Architecture & Test Suite Hierarchy

The test suite is structured into four progressive tiers implemented under `src/test/java/com/example/t/e2e/`:

```
src/test/java/com/example/t/e2e/
├── harness/
│   ├── E2ETestHarness.java              # Sandbox environment, temp DB/CSV manager
│   ├── CalculationOracle.java           # Independent high-precision mathematical oracle
│   ├── SqliteTestDatabase.java          # SQLite schema runner & assertion engine
│   └── CsvTestHelper.java               # RFC 4180 CSV parser and validation utility
├── tier1/                               # Feature Coverage (>=5 test cases per feature)
│   ├── ProductManagementE2ETest.java    # F1: 6 test cases
│   ├── StockUpdateE2ETest.java          # F2: 6 test cases
│   ├── PosBillingE2ETest.java           # F3: 6 test cases
│   ├── DiscountCalculationE2ETest.java  # F4: 6 test cases
│   ├── MultiRegionalTaxE2ETest.java     # F5: 6 test cases
│   ├── InvoiceHistoryE2ETest.java       # F6: 6 test cases
│   ├── SqlitePersistenceE2ETest.java    # F7: 6 test cases
│   └── CsvSyncE2ETest.java              # F8: 6 test cases
├── tier2/                               # Boundary & Corner Cases (>=5 test cases per feature)
│   ├── ProductBoundariesE2ETest.java    # Boundary tests for catalog
│   ├── StockBoundariesE2ETest.java      # Zero, max, negative decrement boundaries
│   ├── BillingBoundariesE2ETest.java    # Empty cart, single unit, exact cash change
│   ├── DiscountBoundariesE2ETest.java   # 0%, 100%, negative, >100% rejection
│   ├── RegionalTaxBoundariesE2ETest.java# 0%, 5%, 15%, 20% boundaries, rounding
│   ├── InvoiceBoundariesE2ETest.java    # Non-existent invoice, walk-in customer, max items
│   ├── SqliteBoundariesE2ETest.java     # Constraint violations, rollback, SQL injection
│   └── CsvBoundariesE2ETest.java        # Embedded commas, double quotes, newlines, empty
├── tier3/                               # Cross-Feature Combinations (Pairwise & Multi-way)
│   └── CrossFeaturePairwiseE2ETest.java # 15 pairwise interaction tests
└── tier4/                               # Real-World Application Scenarios
    ├── UsaBookstoreScenarioE2ETest.java # University bookstore rush (USD, 8.25% Sales Tax)
    ├── UkBookshopScenarioE2ETest.java   # High-street bookshop (GBP, 20% VAT stationery, 0% books)
    ├── GulfUaeStationeryScenarioE2ETest.java # Dubai Mall store (AED, 5% VAT, bilingual)
    ├── GulfSaudiEducationalScenarioE2ETest.java # Riyadh educational supply (SAR, 15% VAT)
    └── EndOfDayReconciliationScenarioE2ETest.java # Multi-transaction reconciliation & inventory audit
```

---

## 4. Test Scenarios

### Tier 1: Feature Coverage (48 Test Cases)
- **F1 (Product Management)**: Create book, create stationery, retrieve by SKU/barcode, update price, delete product, list products by category.
- **F2 (Stock Management)**: Restock inventory, decrement inventory, check low-stock threshold (threshold = 5), trigger out-of-stock flag, multi-product decrement, rollback on failure.
- **F3 (POS Billing)**: Single item calculation, multi-item basket, tax computation, cash change computation, subtotal verification, cash tendered equal to total.
- **F4 (Discounting)**: Zero discount, 10% standard discount, 25% bulk discount, 50% clearance, 100% promotional giveaway, verify single-application (no double discount).
- **F5 (Multi-Regional)**: USA preset (USD / Sales Tax), UK preset (GBP / 20% VAT), UAE preset (AED / 5% VAT), Saudi preset (SAR / 15% VAT), Qatar preset (QAR / 0% VAT), currency symbol formatting.
- **F6 (Invoicing)**: Create invoice with 3 items, query by ID, query by date range, query by customer name, calculate total revenue across invoices, line items foreign key integrity.
- **F7 (SQLite)**: DDL migration execution, table count verification, WAL mode confirmation, foreign key activation, transaction commit, transaction rollback.
- **F8 (CSV Sync)**: Catalog CSV export, catalog CSV import, round-trip equality, invoice history CSV append, RFC 4180 delimiter handling.

### Tier 2: Boundary & Corner Cases (48 Test Cases)
- **Catalog Boundaries**: Empty SKU validation, null/empty product name rejection, maximum length name (500 chars), duplicate SKU rejection, Unicode name handling ("كتاب الحاسوب", "War & Peace").
- **Stock Boundaries**: Stock quantity = 0, restock with max integer ($2^{31}-1$), decrementing more than available stock (rejected), decrementing to exactly 0, negative restock quantity rejection.
- **Billing Boundaries**: Empty cart ($0.00 subtotal, $0.00 tax, $0.00 total), single item with quantity = 1, price with fractional cents ($19.999), tendered cash less than total (negative change / error), exact change ($0.00 change due).
- **Discount Boundaries**: 0% boundary, 100% boundary ($0.00 net total), negative discount rejected, discount > 100% rejected, discount on free items.
- **Tax Boundaries**: 0.0% tax rate (tax = 0.00), 5.0% tax rate, 15.0% tax rate, 20.0% tax rate, 99.99% high tax stress, tax rounding check ($1.005$ rounds to $1.01$).
- **Invoice Boundaries**: Non-existent ID returns empty, empty customer name defaults to "Walk-in Customer", single-item invoice, 100-item bulk invoice, long customer name handling.
- **Persistence Boundaries**: SQLite unique constraint violation, foreign key orphan prevention, transaction rollback leaves 0 residue, special characters/SQL injection in strings (`O'Reilly'; DROP TABLE products;--`).
- **CSV Boundaries**: Values with internal commas (`"Smith, John"`), internal quotes (`"12\" Ruler"`), multiline notes, empty CSV file, CSV with trailing empty lines.

### Tier 3: Cross-Feature Combinations (15 Pairwise Scenarios)
1. **Multi-Currency (GBP) + 10% Discount + 20% VAT + Stock Decrement**: UK store purchase verifying full mathematical chain and database stock count deduction.
2. **UAE Dirhams (AED) + 5% VAT + Bilingual Invoice + SQLite Ledger**: Verifying Gulf preset persistence and receipt bilingual fields.
3. **Saudi Riyals (SAR) + 15% VAT + 25% Discount + Cash Change**: Calculating high VAT with discount and cash tendered in SAR.
4. **USA (USD) + 8.25% Sales Tax + Stock Decrement + CSV Sync**: Complete flow updating both SQLite and RFC 4180 CSV export.
5. **Zero-Tax Region (QAR) + Promotional Discount (15%) + Mixed Items**: Validating that zero tax computes exactly $0.00 tax with valid discount.
6. **Bulk Order (50 line items) + Multi-Regional Tax Switching**: Switching store settings from USA to UK and recalculating identical basket.
7. **Failed Transaction Rollback**: Insufficient stock on 3rd item of a 4-item cart must roll back stock decrements for items 1 and 2 and write no invoice.
8. **Restock after Order**: Decrementing stock during POS sale, followed by restock operation, verifying net inventory.
9. **CSV Export -> Truncate DB -> CSV Import**: Full round-trip catalog restoration.
10. **Invoice Persistence + CSV History Reconciliation**: Cross-checking SQLite `invoices` table against `invoice_history.csv` totals.
11. **Extreme Monetary Precision (Sub-cent items)**: Purchasing 10 items at $0.33 each with 5% VAT, ensuring exact half-up rounding.
12. **Concurrent POS Shopping Sessions**: Simulating 5 parallel checkout transactions with isolated database connections.
13. **Customer Search across Multi-Currency Invoices**: Filtering ledger by customer name with varying currency symbols.
14. **Category Stock Valuation**: Total stock value calculation by category (Books vs Stationery).
15. **Regional Preset Dynamic Switch**: Dynamically changing settings from USA to UK to UAE and verifying POS calculation adaptability.

### Tier 4: Real-World Application Scenarios (5 Realistic Sessions)
1. **USA University Bookstore Rush**: A student buys 1 Calculus Textbook ($120.00), 2 Spiral Notebooks ($4.50 ea), and 1 Scientific Calculator ($25.00). Applies 10% student discount. Store applies 8.25% Texas sales tax. Tendered $150.00 cash. Verifies invoice, inventory, and change due ($17.15).
2. **UK High-Street Bookshop & Stationery**: Customer buys 2 Fiction Novels (£8.99 ea) and 1 Fountain Pen (£22.50). In UK, books are 0% VAT, stationery is 20% VAT. Verifies split-VAT or standard VAT calculation, change from £50.00 note.
3. **UAE Dubai Mall Modern Stationery**: Luxury purchase of 1 Leather Journal (AED 85.00), 2 Calligraphy Pens (AED 45.00 ea), 1 Stationery Gift Box (AED 120.00). 5% UAE VAT applied. Arabic/English bilingual invoice generated.
4. **Saudi Educational Supply Center**: Bulk school supplies: 20 Math Workbooks (SAR 35.00 ea), 50 Ballpoint Pens (SAR 3.00 ea), 10 Geometry Sets (SAR 15.00 ea). Subtotal SAR 1,000.00. 15% Saudi VAT (SAR 150.00). Total SAR 1,150.00. Exported to CSV.
5. **End-of-Day Store Reconciliation**: 10 transactions processed throughout the day. System aggregates gross revenue, total discounts awarded, total tax collected by regional type, and verifies stock level consistency between database and catalog.

---

## 5. Thresholds & Acceptance Criteria

| Metric | Target Threshold | Strict / Warning |
|---|---|---|
| **Test Pass Rate** | **100% (0 failures, 0 errors)** | Strict |
| **Total Test Count** | **$\ge 110$ tests** across Tiers 1–4 | Strict |
| **Monetary Math Precision** | **Exact to 2 decimal places ($0.01$)** using `BigDecimal` HALF_UP | Strict |
| **SQLite WAL Mode** | `journal_mode = wal`, `foreign_keys = 1` | Strict |
| **CSV Format Compliance** | Full RFC 4180 compliance (double quote escaping, delimiter preservation) | Strict |
| **Test Execution Duration** | $< 30$ seconds for full test suite execution | Warning |
| **Test Isolation** | Zero cross-test state leakage; independent sandbox per test | Strict |
