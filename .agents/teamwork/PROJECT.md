# Project: Modern Offline JavaFX Desktop Bookstore & Stationery Billing & Inventory (v1.1.0)

## Architecture
- **Runtime & Target**: OpenJDK 21 LTS, JavaFX 21.0.6, ControlsFX 11.2.1, AtlantaFX 2.0.1 (Windows 11 Fluent theme).
- **UI Architecture**: Single-stage Master Navigation Shell (Sidebar + Center Content Viewport) built with FXML + AtlantaFX CSS styling.
- **Persistence Architecture**: Embedded SQLite 3 (`bookstore.db`) under `%LOCALAPPDATA%\BookstoreBilling\data\` with WAL mode, foreign keys, automated schema migrations (`schema_migrations` table), sample catalog seeding, backward-compatible flat-file migration, and RFC 4180 CSV export sync.
- **Document / PDF Engine**: OpenPDF 1.3.40 generating professional, branded invoices with multi-regional support (USA sales tax, UK VAT, Gulf 5-15% VAT and bilingual Arabic/English layout) and 50x30mm Code-128 / EAN-13 shelf/spine labels.
- **Hardware Printing Engine**: Raw ESC/POS bytecode streamer (80mm / 58mm), paper cut command, and cash drawer kick pulse.
- **Metadata Engine**: Asynchronous bibliographic metadata retrieval (`MetadataService`) with ISBN-10/13 checksum validation, SQLite local cache, Google Books & Open Library fallback, and AppData cover art caching.
- **Packaging Architecture**: Standalone zero-dependency Windows distribution (`BookstoreBilling.exe`) created via hybrid `jlink` runtime + `jpackage --type app-image` classpath bundling.

## Feature Inventory (v1.1.0)
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Baseline 100% Pass & Region Fix | Fix `RegionalPreset.GULF_UAE` currency symbol to Arabic "د.إ" to restore 100% pass (398/398) | M1.1-A | Explorer 3 / R5 (DONE) |
| 2 | HTTP & Module Configuration | Add `requires java.net.http;` to `module-info.java` | M1.1-A | Explorer 1 / R1 (DONE) |
| 3 | SQLite Schema Migration V2 | Add `catalog_cache`, `invoice_payments`, `financial_z_reports` tables & `invoice_items` columns | M1.1-A | Explorer 1, 2, 3 (DONE) |
| 4 | Data DAOs & Models V2 | Add `CatalogCacheDao`, `InvoicePaymentDao`, `ZReportDao`, `BookMetadata`, and AppData covers dir | M1.1-A | Explorer 1, 2, 3 (DONE) |
| 5 | ISBN Checksum & Conversion | Implement `IsbnValidator` with Mod 11, Mod 10, and bidirectional 978 conversion | M1.1-B | Explorer 1 / R1 |
| 6 | Asynchronous MetadataService | Google Books + Open Library query with local SQLite cache and cover art download | M1.1-B | Explorer 1 / R1 |
| 7 | Stock Catalog ISBN Auto-enrichment | Auto-populate title, author, publisher, edition, cover preview on ISBN entry in Stock UI | M1.1-B | Explorer 1 / R1 |
| 8 | Keyboard Shortcuts (F1–F12) | Zero-touch keyboard navigation in POS billing coordinated with MainShell | M1.1-C | Explorer 2 / R2 |
| 9 | Scanner Wedge Buffer | Buffer rapid hardware keystrokes (40ms threshold) terminated by Enter without corrupting text fields | M1.1-C | Explorer 2 / R2 |
| 10 | Split Tender Payments | Split order total across Cash (with change math), Card, Store Credit into `invoice_payments` | M1.1-C | Explorer 2 / R2 |
| 11 | Multi-Cart Park and Resume | In-memory `ParkedCart` state management (Hold F6 / Resume F7) with visual tab/switcher | M1.1-C | Explorer 2 / R2 |
| 12 | Line Notes & Discounts | Support item-level discount %, cart discount %, line notes, and gift receipt generation | M1.1-C | Explorer 2 / R2 |
| 13 | Thermal ESC/POS Byte Streamer | 80mm/58mm bytecode builder, paper cut, drawer kick pulse (`0x1B, 0x70, 0x00, 0x19, 0xFA`) | M1.1-D | Explorer 3 / R3 |
| 14 | Shelf & Spine Barcode Labels | 50x30mm thermal label generation (Code-128 / EAN-13) using OpenPDF vector barcode classes | M1.1-D | Explorer 3 / R3 |
| 15 | Dead Stock Radar Engine | Identify 60/90/180-day non-moving stock grouped by publisher with capital tied up & CSV export | M1.1-E | Explorer 3 / R4 |
| 16 | Dynamic Reorder Assistant | Daily sales velocity ($V_{30}$) and lead time ($L$) safety stock reorder thresholds | M1.1-E | Explorer 3 / R4 |
| 17 | Daily Financial Z-Report & Audit | End-of-day register closure, expected vs actual cash reconciliation, non-resettable grand totals | M1.1-E | Explorer 3 / R4 |
| 18 | Semantic Versioning & Full Test Suite | Verify version 1.1.0 across artifacts and achieve 100% clean test suite execution | M1.1-E | Explorer 3 / R5 |

## Milestones (v1.1.0)
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1.1-A | Baseline Fix, Migration V2 & Infrastructure | Fix UAE currency symbol, module-info http dependency, Migration V2 (catalog_cache, invoice_payments, z_reports), DAOs | none | DONE |
| M1.1-B | Intelligent Online/Offline ISBN Engine (R1) | IsbnValidator (Mod 11/10 & conversion), MetadataService (Google Books/OpenLibrary fallback), cover caching, Stock UI integration | M1.1-A | PLANNED |
| M1.1-C | Keyboard-First POS & Split Tender (R2) | F1-F12 shortcuts, barcode scanner buffer, split tender math & DAO, Park/Resume carts, item notes, gift receipt | M1.1-A | PLANNED |
| M1.1-D | Thermal ESC/POS & Label Printing (R3) | EscPosBuilder (80mm/58mm, cut, drawer kick), pluggable transport, 50x30mm shelf/spine labels via OpenPDF | M1.1-A | PLANNED |
| M1.1-E | Inventory Intelligence, Z-Reports & Full Pass (R4, R5) | Dead Stock Radar by publisher, dynamic safety stock, Z-Report reconciliation & audit ledger, 100% test suite pass | M1.1-B, M1.1-C, M1.1-D | PLANNED |

## Interface Contracts (v1.1.0)

### 1. ISBN Validation & Metadata Layer (`com.example.t.service.isbn`)
- `IsbnValidator`:
  - `boolean isValid(String raw)`
  - `boolean isValidIsbn10(String raw)`
  - `boolean isValidIsbn13(String raw)`
  - `String toIsbn13(String isbn10Raw)`
  - `Optional<String> toIsbn10(String isbn13Raw)`
  - `String clean(String raw)`
- `MetadataService`:
  - `CompletableFuture<Optional<BookMetadata>> lookupAsync(String rawIsbn)`
  - `Optional<BookMetadata> lookupSync(String rawIsbn)`
- `BookMetadata`:
  - Attributes: `isbn13`, `isbn10`, `title`, `subtitle`, `authors`, `publisher`, `publishedYear`, `edition`, `format`, `genre`, `description`, `pageCount`, `coverImageUrl`, `coverLocalPath`, `source`.

### 2. POS Keyboard, Scanner & Split Tender (`com.example.t.service` / `com.example.t.controller`)
- `BarcodeScannerBuffer`:
  - `void handleKeyEvent(KeyEvent event, Consumer<String> onBarcodeDetected)`
  - Inter-keystroke threshold: 40ms, minimum length: 4 digits, terminator: `\r`, `\n`, `ENTER`.
- `BillingService` Split Tender additions:
  - `CalculationResult calculateSplitTender(double netTotal, List<PaymentTender> tenders)`
  - Change due calculation: $c = \max(0, \sum \text{tendered}_\text{cash} - \text{applied}_\text{cash})$
- `InvoicePayment`:
  - Attributes: `paymentId`, `invoiceId`, `paymentType` (`CASH`, `CARD`, `STORE_CREDIT`), `amount`, `tenderedAmount`, `changeDue`, `referenceInfo`.
- `ParkedCart`:
  - Attributes: `cartId`, `displayName`, `parkedAt`, `customerName`, `items`, `discountPercent`, `notes`, `giftReceipt`.

### 3. Hardware Thermal Printing & Labels (`com.example.t.service.printer`)
- `EscPosBuilder`:
  - `EscPosBuilder initialize()`
  - `EscPosBuilder text(String text)`
  - `EscPosBuilder align(Alignment alignment)`
  - `EscPosBuilder bold(boolean on)`
  - `EscPosBuilder lineFeed(int lines)`
  - `EscPosBuilder cutPaper(boolean fullCut)`
  - `EscPosBuilder kickDrawer()` (emits `0x1B, 0x70, 0x00, 0x19, 0xFA`)
  - `byte[] getBytes()`
- `ShelfLabelService`:
  - `byte[] generatePdfLabels(List<Product> products, LabelConfig config)` (50x30mm Code-128 / EAN-13).

### 4. Inventory Intelligence & Financial Z-Reports (`com.example.t.service.analytics`)
- `DeadStockReport`:
  - Query non-moving titles over $D \in \{60, 90, 180\}$ days grouped by publisher, capital tied up ($Q \times P_\text{purchase}$).
  - Export to RFC 4180 CSV for return-to-vendor credit.
- `DynamicReorderService`:
  - Daily velocity $V_{30} = \sum \text{units}_{30\text{d}} / 30.0$.
  - Reorder point $ROP = \max(\text{min\_stock}, \lceil V_{30} \times L \rceil)$.
- `ZReportService`:
  - `ZReport closeRegister(LocalDate date, double openingFloat, double actualCashCount, String closedBy)`
  - Cumulative grand total: $CGT_K = CGT_{K-1} + \text{net\_sales}_K$.

## Code Layout (v1.1.0 Additions)
```
src/main/java/com/example/t/
├── model/
│   ├── BookMetadata.java
│   ├── PaymentTender.java
│   ├── InvoicePayment.java
│   ├── ParkedCart.java
│   └── ZReport.java
├── service/
│   ├── isbn/
│   │   ├── IsbnValidator.java
│   │   └── MetadataService.java
│   ├── printer/
│   │   ├── EscPosBuilder.java
│   │   ├── EscPosPrinterService.java
│   │   └── ShelfLabelService.java
│   └── analytics/
│       ├── DeadStockService.java
│       ├── DynamicReorderService.java
│       └── ZReportService.java
├── dao/
│   ├── CatalogCacheDao.java
│   ├── InvoicePaymentDao.java
│   └── ZReportDao.java
└── util/
    └── BarcodeScannerBuffer.java
```
