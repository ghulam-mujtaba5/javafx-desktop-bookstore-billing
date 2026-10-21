# Explorer 3 Survey Report: Hardware ESC/POS & Label Printing, Inventory Intelligence & Baseline Verification (R3, R4, R5)

**Target Milestone**: Survey & Design for v1.1.0 Upgrade  
**Author**: Explorer 3 (Hardware, Inventory Intelligence & Baseline Verification)  
**Date**: 2026-10-20  

---

## 1. Observation

### R3: Thermal ESC/POS & Label Printing Engine
1. **Existing PDF Generation**:
   - `src/main/java/com/example/t/service/PdfInvoiceService.java` (lines 8–10, 139–159, 233–279) generates A4 invoices and 80mm receipt PDFs using OpenPDF 1.3.40 (`com.lowagie.text.*`, `com.lowagie.text.pdf.*`).
   - Line 155 specifies thermal dimensions in PDF points: `new Rectangle(226.77f, 600f)`.
   - There is **no raw binary ESC/POS stream generator**, no paper cut command, no cash drawer kick sequence, and no barcode label generator currently in the codebase.
2. **Printer Interface & Protocols**:
   - `module-info.java` (line 7) declares `requires java.desktop;`, which provides access to `javax.print.*` (`PrintServiceLookup`, `DocFlavor.BYTE_ARRAY.AUTOSENSE`, `DocPrintJob`, `SimpleDoc`).
   - Windows thermal receipt printers (Epson TM-T20/TM-T88, Star Micronics, Rongta, Xprinter) accept raw ESC/POS binary commands directly via `javax.print` or raw TCP sockets on port 9100.
3. **Barcode Label Generation**:
   - Standard 50x30mm thermal label rolls require Code-128 and EAN-13 barcodes.
   - OpenPDF includes built-in barcode classes: `com.lowagie.text.pdf.Barcode128`, `com.lowagie.text.pdf.BarcodeEAN`, and `com.lowagie.text.pdf.Barcode39`. They provide `createAwtImage(Color, Color)` and direct vector rendering onto `PdfContentByte` without requiring any external barcode library.
   - Alternatively, Java2D `java.awt.image.BufferedImage` (400x240 px at 203 DPI) can be rendered via `Graphics2D` and sent directly to label printers or exported as PNG/PDF.

### R4: Inventory Intelligence, Dead Stock Radar & Financial Z-Reports
1. **Existing SQLite Schema**:
   - `src/main/java/com/example/t/database/SchemaMigrationManager.java` (lines 118–183) defines 6 tables: `categories`, `products`, `invoices`, `invoice_items`, `store_settings`, and `schema_migrations`.
   - `products` table columns: `product_id`, `sku`, `name`, `category_id`, `price`, `purchase_price`, `stock_quantity`, `min_stock_threshold`, `author`, `publisher`, `isbn`, `status`, `created_at`, `updated_at`.
   - `invoices` table columns: `invoice_id`, `invoice_number`, `customer_name`, `customer_phone`, `invoice_date`, `subtotal`, `discount_percent`, `discount_amount`, `tax_rate`, `tax_amount`, `net_total`, `cash_tendered`, `change_due`, `payment_method`, `currency_code`, `region`.
   - `invoice_items` table columns: `item_id`, `invoice_id`, `product_id`, `quantity`, `unit_price`, `line_total`.
2. **Current Stock Threshold Logic**:
   - `src/main/java/com/example/t/controller/StockManagementController.java` (lines 167–176) evaluates low stock purely as a static comparison: `p.getQuantity() <= p.getMinStockThreshold()`. There is no dynamic velocity calculation or lead-time safety stock modeling.
3. **End-of-Day Reconciliation & Audit**:
   - `src/test/java/com/example/t/e2e/tier4/EndOfDayReconciliationScenarioE2ETest.java` (lines 118–143) verifies multi-transaction daily revenue aggregation via ad-hoc queries over `invoices`.
   - There is currently **no dedicated `z_reports` table**, no cash drawer reconciliation tracking (opening float vs actual drawer count vs expected), and no non-resettable cumulative grand total audit ledger.

### R5: Semantic Versioning and Baseline Test Suite
1. **Version Strings Across Artifacts**:
   - `pom.xml`: line 9 defines `<version>1.1.0</version>`, and line 112 defines `<app.version>1.1.0</app.version>`.
   - `src/main/java/com/example/t/Main.java`: line 85 sets `primaryStage.setTitle("Bookstore & Stationery Billing & Inventory v1.1.0");`.
   - `src/main/resources/com/example/t/fxml/MainShell.fxml`: line 46 displays `<Label text="Version 1.1.0 • Offline Ready" />`.
   - **Residual Version Mismatches Discovered**:
     - `package.ps1` line 142 and `package.bat` line 147 display `t-1.0-SNAPSHOT.jar` in dry-run output.
     - `src/test/java/com/example/t/PackagingEmpiricalChallengeTest.java` line 298 asserts: `assertTrue(cfg.contains("app.classpath=$APPDIR\\t-1.0-SNAPSHOT.jar"))`. When packaged under `1.1.0`, this will produce `t-1.1.0.jar` and fail this test if not updated or parameterized.
2. **Baseline Test Suite Execution**:
   - Command `./mvnw.cmd test` executed across all test files.
   - Total Tests Executed: **398**.
   - Passed: **397**.
   - Failures: **1**.
   - Failing Test: `FinalAcceptanceCriteriaVerificationTest.testAcceptanceCriterion6_MultiRegionalCurrencyAndTaxCalculationSwitching:528`.
     - Assertion failure: `Header must display Arabic currency symbol ==> expected: <true> but was: <false>`.
     - Cause: `RegionalPreset.GULF_UAE` in `src/main/java/com/example/t/model/RegionalPreset.java` (line 9) defines currency symbol as `"AED"` rather than `"د.إ"`. When the preset is loaded, the shell header shows `"GULF_UAE (AED AED)"` instead of `"GULF_UAE (AED د.إ)"`.

---

## 2. Logic Chain

### 1. Thermal ESC/POS Byte Generator & Cash Drawer Architecture (R3)
- **Protocol Requirements**:
  - The ESC/POS standard operates on raw binary byte commands.
  - To support 80mm (48 characters/line Font A) and 58mm (32 characters/line Font A) printers, the formatter must compute tabular columns dynamically with word-wrapping or truncation.
- **Specific Byte Sequences**:
  - **Printer Reset / Initialize**: `0x1B, 0x40` (`ESC @`)
  - **Text Alignment**:
    - Left: `0x1B, 0x61, 0x00`
    - Center: `0x1B, 0x61, 0x01`
    - Right: `0x1B, 0x61, 0x02`
  - **Character Emphasis / Sizing**:
    - Bold ON: `0x1B, 0x45, 0x01`; Bold OFF: `0x1B, 0x45, 0x00`
    - Double Width & Height (Headers): `0x1D, 0x21, 0x11`; Normal: `0x1D, 0x21, 0x00`
  - **Cash Drawer Kick Pulse**:
    - Verbatim sequence: `0x1B, 0x70, 0x00, 0x19, 0xFA` (`ESC p m t1 t2` pulse to drawer pin 2 for 50ms ON, 500ms OFF).
  - **Paper Cut**:
    - Partial cut with 3-line feed: `0x1D, 0x56, 0x42, 0x03` (`GS V 'B' 3`).
    - Full cut: `0x1D, 0x56, 0x00` (`GS V 0`).
  - **Barcode Command (Code-128)**:
    - Set height (64 dots): `0x1D, 0x68, 0x40`
    - Set width: `0x1D, 0x77, 0x02`
    - Set HRI below barcode: `0x1D, 0x48, 0x02`
    - Print Code-128 data: `0x1D, 0x6B, 0x49, (byte)len, ...bytes`
- **Transmission Transports**:
  - Windows Driver / USB: `javax.print.PrintServiceLookup.lookupPrintServices(DocFlavor.BYTE_ARRAY.AUTOSENSE, null)` sends raw byte payloads directly to Windows print queues.
  - Network / Ethernet: Raw TCP socket connection to `host:9100`.
  - Virtual / File: Writes `.bin` / `.prn` files for simulation, testing, and file archiving.

### 2. Shelf & Spine Barcode Label Generator (50x30mm) (R3)
- **Geometry**:
  - Standard retail shelf and spine label size: 50mm wide x 30mm high.
  - In PDF typographic points (72 DPI): $50\text{ mm} \approx 141.73\text{ pt}$, $30\text{ mm} \approx 85.04\text{ pt}$.
  - In thermal printhead pixels (203 DPI): $\approx 400 \times 240\text{ px}$.
- **Zero-Dependency Vector Generation**:
  - OpenPDF already provides `Barcode128` and `BarcodeEAN`.
  - Can construct a single-page or multi-page label PDF document with `new Rectangle(141.73f, 85.04f)` and zero margins.
  - Generates:
    - Top line: Store Name / Category (bold 7pt).
    - Middle: Book / Item Title (bold 9pt, truncated at 26 chars) + Author/Publisher (6pt).
    - Center: Vector Code-128 or EAN-13 barcode with text below.
    - Bottom Right: Price in prominent bold (`$35.00` / `AED 45.00`).
- **Java2D Alternative**:
  - `BufferedImage` (400x240, `TYPE_BYTE_BINARY`), drawn using `Graphics2D` and OpenPDF's `Barcode128.createAwtImage(...)`, exportable as PNG or directly printable via Java Print Service.

### 3. Dead Stock Radar Engine (R4)
- **Business Need**: Retail bookstores must identify slow/non-moving titles within 60, 90, or 180 days to process return-to-vendor (RTV) credits with distributors (Ingram, Baker & Taylor, Penguin Random House) before returns windows expire.
- **SQL Query Specification**:
  ```sql
  SELECT 
      p.product_id,
      p.sku,
      p.name,
      COALESCE(p.publisher, 'Independent / Unknown') AS publisher,
      p.author,
      p.stock_quantity,
      p.purchase_price,
      p.price,
      (p.stock_quantity * p.purchase_price) AS capital_tied_up,
      MAX(i.invoice_date) AS last_sale_date,
      CAST(JULIANDAY('now') - JULIANDAY(COALESCE(MAX(i.invoice_date), p.created_at)) AS INTEGER) AS days_dormant
  FROM products p
  LEFT JOIN invoice_items ii ON p.product_id = ii.product_id
  LEFT JOIN invoices i ON ii.invoice_id = i.invoice_id
  WHERE p.stock_quantity > 0 
    AND p.status = 'ACTIVE'
  GROUP BY p.product_id
  HAVING (MAX(i.invoice_date) IS NULL AND p.created_at <= datetime('now', '-' || ? || ' days'))
      OR (MAX(i.invoice_date) <= datetime('now', '-' || ? || ' days'))
  ORDER BY publisher ASC, capital_tied_up DESC;
  ```
- **Publisher Aggregation Summary**:
  ```sql
  SELECT 
      COALESCE(p.publisher, 'Independent / Unknown') AS publisher,
      COUNT(DISTINCT p.product_id) AS dormant_titles_count,
      SUM(p.stock_quantity) AS total_unsold_units,
      ROUND(SUM(p.stock_quantity * p.purchase_price), 2) AS total_capital_tied_up
  FROM products p
  LEFT JOIN invoice_items ii ON p.product_id = ii.product_id
  LEFT JOIN invoices i ON ii.invoice_id = i.invoice_id
  WHERE p.stock_quantity > 0 
    AND p.status = 'ACTIVE'
  GROUP BY COALESCE(p.publisher, 'Independent / Unknown')
  HAVING (MAX(i.invoice_date) IS NULL AND p.created_at <= datetime('now', '-' || ? || ' days'))
      OR (MAX(i.invoice_date) <= datetime('now', '-' || ? || ' days'))
  ORDER BY total_capital_tied_up DESC;
  ```

### 4. Dynamic Safety Stock & Reorder Assistant (R4)
- **Velocity Formula**:
  - Daily Velocity $V_{30} = \frac{\text{Units Sold in Last 30 Days}}{30.0}$.
  - Estimated Supplier Lead Time $L = 7\text{ days}$ (configurable default).
  - Dynamic Safety Reorder Point $ROP = \max(p.\text{min\_stock\_threshold}, \lceil V_{30} \times L \rceil)$.
  - Suggested Reorder Quantity $ROQ = \max(0, (2 \times ROP) - p.\text{stock\_quantity})$.
- **SQL Implementation**:
  ```sql
  SELECT 
      p.product_id,
      p.sku,
      p.name,
      p.stock_quantity,
      p.min_stock_threshold,
      COALESCE(SUM(ii.quantity), 0) AS units_sold_30d,
      ROUND(CAST(COALESCE(SUM(ii.quantity), 0) AS REAL) / 30.0, 2) AS daily_velocity,
      MAX(p.min_stock_threshold, CAST(ROUND((CAST(COALESCE(SUM(ii.quantity), 0) AS REAL) / 30.0) * 7) AS INTEGER)) AS dynamic_reorder_point,
      CASE 
          WHEN p.stock_quantity <= 0 THEN 'OUT_OF_STOCK'
          WHEN p.stock_quantity <= MAX(p.min_stock_threshold, CAST(ROUND((CAST(COALESCE(SUM(ii.quantity), 0) AS REAL) / 30.0) * 7) AS INTEGER)) THEN 'REORDER_NOW'
          ELSE 'OPTIMAL'
      END AS reorder_status
  FROM products p
  LEFT JOIN invoice_items ii ON p.product_id = ii.product_id
  LEFT JOIN invoices i ON ii.invoice_id = i.invoice_id AND i.invoice_date >= datetime('now', '-30 days')
  WHERE p.status = 'ACTIVE'
  GROUP BY p.product_id;
  ```

### 5. Daily Financial Z-Report & Audit Ledger (R4)
- **Schema Migration Version 2** (`schema_migrations` table):
  ```sql
  CREATE TABLE IF NOT EXISTS financial_z_reports (
      z_report_id INTEGER PRIMARY KEY AUTOINCREMENT,
      z_sequence_number INTEGER UNIQUE NOT NULL,
      report_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      period_start TIMESTAMP NOT NULL,
      period_end TIMESTAMP NOT NULL,
      opening_float REAL DEFAULT 0.0,
      total_cash_sales REAL NOT NULL,
      total_card_sales REAL NOT NULL,
      total_credit_sales REAL NOT NULL,
      gross_sales REAL NOT NULL,
      total_discount REAL NOT NULL,
      taxable_sales REAL NOT NULL,
      total_tax REAL NOT NULL,
      net_sales REAL NOT NULL,
      transaction_count INTEGER NOT NULL,
      expected_drawer_cash REAL NOT NULL,
      actual_drawer_cash REAL NOT NULL,
      cash_over_short REAL NOT NULL,
      cumulative_grand_total REAL NOT NULL,
      closed_by_user TEXT DEFAULT 'admin',
      notes TEXT
  );
  ```
- **Cumulative Grand Total (Non-Resettable Ledger)**:
  - When closing register for Z-Report $K$:
    $$\text{cumulative\_grand\_total}_K = \text{cumulative\_grand\_total}_{K-1} + \text{net\_sales}_K$$
  - Provides tamper-evident fiscal audit tracking even across store years.
- **Cash Reconciliation Formula**:
  $$\text{expected\_drawer\_cash} = \text{opening\_float} + \text{total\_cash\_sales} - \text{cash\_payouts}$$
  $$\text{cash\_over\_short} = \text{actual\_drawer\_cash} - \text{expected\_drawer\_cash}$$

### 6. Baseline Verification & Root Cause Analysis (R5)
- **Failure in `FinalAcceptanceCriteriaVerificationTest.java:528`**:
  - Line 528 asserts: `assertTrue(shellController.getRegionPresetLabel().getText().contains("د.إ"))`.
  - Line 9 in `RegionalPreset.java`:
    `GULF_UAE("UAE", "AED", "AED", "VAT", 5.00, "BILINGUAL_AR_EN")`
  - The third argument is `currencySymbol`. It was set to `"AED"` rather than `"د.إ"`.
  - Fixing `RegionalPreset.java` (setting symbol to `"د.إ"` for UAE, `"ر.س"` for Saudi, and `"ر.ق"` for Qatar) will restore 100% (398/398) pass rate.
- **Packaging Hardcoding**:
  - `PackagingEmpiricalChallengeTest.java:298` verifies `t-1.0-SNAPSHOT.jar`. With `pom.xml` at `1.1.0`, any rebuild with packaging will produce `t-1.1.0.jar`. The test should assert matching `t-(1\.0-SNAPSHOT|1\.1\.0)\.jar` or dynamically read the version from `pom.xml`.

---

## 3. Caveats

1. **Hardware Device Availability in CI / Dev Environment**:
   - Physical ESC/POS thermal printers and cash drawers cannot be attached in standard automated CI environments.
   - Therefore, the implementation must feature a **pluggable transport design**: an `EscPosDispatcher` interface with implementations for `UsbPrintDispatcher`, `NetworkSocketDispatcher`, and `MockFilePrintDispatcher`. Unit tests can verify binary bytecode generation against golden byte arrays without requiring physical hardware.
2. **Arabic Character Encoding on Thermal Hardware**:
   - Many standard thermal printers do not support raw UTF-8 Arabic text directly on basic ROM code pages (they require `CP864` or `Windows-1256`, or graphics raster mode `GS v 0` / ESC `*` if printer lacks Arabic ROM fonts).
   - For English/Latin text, standard ASCII/CP437 works universally.
   - For bilingual receipts (R3/R4), the system should offer both text-mode ESC/POS for standard receipts and bitmap raster ESC/POS mode or OpenPDF 80mm PDF rendering as a robust fallback.
3. **Database Date Handling**:
   - SQLite stores timestamps as text (e.g. `2026-10-20 18:20:38`).
   - SQLite date queries must use standard SQLite date formatting functions (`datetime('now', '-90 days')`, `JULIANDAY(...)`) to ensure cross-platform compatibility across Windows and Linux.
4. **Scope Boundaries**:
   - As an explorer, no changes to source code or tests were implemented. All proposed code, queries, and fixes are documented in this report.

---

## 4. Conclusion

1. **R3 (Thermal ESC/POS & Label Engine)**:
   - Feasible with **zero new external dependencies**.
   - Build a lightweight `EscPosBuilder` class producing exact byte sequences for initialization (`0x1B, 0x40`), alignment, line-feeds, paper cut (`0x1D, 0x56, 0x42, 0x03`), and cash drawer pulse (`0x1B, 0x70, 0x00, 0x19, 0xFA`).
   - Use OpenPDF's built-in `Barcode128` and `BarcodeEAN` to generate 50x30mm shelf/spine labels formatted for standard thermal label printers.
2. **R4 (Inventory Intelligence, Dead Stock Radar & Financial Z-Reports)**:
   - High-performance SQL queries can immediately provide 60/90/180-day non-moving stock grouped by publisher, capital tied up calculations, and 30-day velocity-based dynamic reorder thresholds.
   - Introduce a clean `SchemaMigrationManager` v2 migration adding the `financial_z_reports` table to provide immutable, non-resettable cumulative sales ledgers and cash drawer reconciliation.
3. **R5 (Semantic Versioning & Baseline Verification)**:
   - Baseline test execution establishes **397 out of 398 tests passing cleanly** on OpenJDK 21 LTS.
   - Exactly one minor fix is required to achieve 398/398 (100%): update `RegionalPreset.GULF_UAE` currency symbol to `"د.إ"` in `RegionalPreset.java`.
   - Update `PackagingEmpiricalChallengeTest.java:298` to support `t-1.1.0.jar`.

---

## 5. Verification Method

To independently verify the observations and conclusions in this report:

1. **Verify Baseline Test Count and Single Failure**:
   ```powershell
   .\mvnw.cmd test
   ```
   *Expected Output*: Tests run: 398, Failures: 1, Errors: 0, Skipped: 0.  
   Failing test: `FinalAcceptanceCriteriaVerificationTest.testAcceptanceCriterion6_MultiRegionalCurrencyAndTaxCalculationSwitching:528` (`expected: <true> but was: <false>`).

2. **Verify pom.xml and Main Versioning**:
   ```powershell
   Select-String -Path pom.xml -Pattern "<version>1.1.0</version>"
   Select-String -Path src\main\java\com\example\t\Main.java -Pattern "v1.1.0"
   Select-String -Path src\main\resources\com\example\t\fxml\MainShell.fxml -Pattern "Version 1.1.0"
   ```

3. **Verify OpenPDF Barcode Availability**:
   Inspect classpath imports in OpenPDF 1.3.40:
   - `com.lowagie.text.pdf.Barcode128`
   - `com.lowagie.text.pdf.BarcodeEAN`
   - `com.lowagie.text.Rectangle`

4. **Verify Residual SNAPSHOT References**:
   ```powershell
   Select-String -Path src\test\java\com\example\t\PackagingEmpiricalChallengeTest.java -Pattern "t-1.0-SNAPSHOT"
   Select-String -Path package.ps1 -Pattern "t-1.0-SNAPSHOT"
   ```
