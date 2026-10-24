# Original User Request

## 2026-10-19T18:20:38Z

Modern offline JavaFX Desktop Bookstore & Stationery Billing & Inventory application upgraded to JDK 21 LTS with AtlantaFX modern UI, embedded SQLite persistence, multi-regional tax/currency support (USA, UK, and Middle East / Gulf countries), receipt/PDF printing, and Windows packaging (jpackage/MSIX) ready for free distribution and Microsoft Store.

Working directory: e:\javafx-desktop-bookstore-billing
Integrity mode: development

## Requirements

### R1. Upgrade Build & Core Dependencies to JDK 21 LTS
Upgrade pom.xml from Java 20 to Java 21 LTS with compatible JavaFX (21+), ControlsFX, AtlantaFX (modern Fluent theme library), SQLite JDBC, and PDF generation (e.g., OpenPDF or Apache PDFBox). Resolve all incomplete stub files (NotificationUtil.java, Logger.java, PasswordUtil.java, MenuController.java) and missing property bindings in Invoice.java and screen classes so the project compiles and builds cleanly without warnings or errors.

### R2. Modernized Windows 11 Fluent UI with Complete POS Workflows
Preserve and elevate all original semester project workflows with modern desktop POS standards:
- Product & Stock Management: Add Stock, Update Stock, View/Filter Stock, low-stock warnings, barcode/SKU input, categorization (Books vs Stationery items).
- Order & Billing POS Screen: Quick search and add items by name or barcode, quantity adjustments, real-time subtotal, discount, tax calculations, and cash change computation.
- Invoicing & History: View invoice history, filter by date/invoice number/customer, invoice re-print, and export to CSV/PDF.
- Settings & Store Configuration: Multi-regional presets for USA (USD / Sales Tax), UK (GBP / VAT), and Middle East / Gulf countries (AED, SAR, QAR / 5%-15% VAT / bilingual Arabic/English invoice template support), customizable store name, address, receipt headers/footers, and admin password protection.
Style the UI cleanly with AtlantaFX / modern CSS stylesheets for a polished, pixel-perfect, native desktop experience.

### R3. Robust Embedded Offline Persistence (SQLite + CSV Sync)
Replace fragile flat text file parsing (stock.txt, Shop_Data.txt, etc.) with a robust embedded local SQLite database (bookstore.db) placed in the local app data directory with automatic schema migration. Maintain backward-compatible import from the legacy text files and one-click export to CSV for data portability.

### R4. Standalone Windows Packaging Configuration
Provide a tested build script and Maven profile using jpackage to bundle a self-contained, zero-dependency Windows executable (.exe / MSI installer) that packages the runtime JRE so end-users can run the application completely offline without installing Java.

## Acceptance Criteria

### Build & Verification
- [ ] ./mvnw clean test-compile compiles cleanly without errors on JDK 21.
- [ ] Automated unit tests pass for core billing calculations (subtotal, tax, discount, inventory decrement) and SQLite database DAO operations.

### Offline POS & Persistence
- [ ] Launching the application initializes bookstore.db automatically and loads sample bookstore/stationery catalog items.
- [ ] Creating an order decrements stock counts in real time and records the invoice in the database and CSV export.
- [ ] Stock editing updates item details and reflects immediately across all screens.
- [ ] Multi-regional currency and tax calculation switches correctly according to store settings.

### Packaging Readiness
- [ ] A dedicated Maven build profile or packaging script executes jpackage and produces a standalone Windows distribution artifact.


## 2026-10-20T19:51:37Z

Comprehensive competitive feature enhancement and functional superiority upgrade for JavaFX Desktop Bookstore & Stationery Billing & Inventory against industry-leading bookstore POS solutions (BookManager, Basil, Lightspeed Retail, Square for Retail, Shopify POS, and Loyverse), implementing winning features systematically, maintaining semantic versioning, and running in dev mode.

Working directory: e:\javafx-desktop-bookstore-billing
Integrity mode: development

## Background & Competitive Superiority Strategy
Competitor benchmarking reveals that legacy bookstore systems (BookManager, Basil) have deep book metadata but archaic 1990s UIs and steep learning curves. Modern cloud systems (Lightspeed, Square, Shopify) have modern UIs but treat books like generic apparel, lack native automated ISBN metadata enrichment, suffer from web latency during rapid barcode scanning, fail completely during internet outages, and lock bookshops into expensive SaaS subscriptions ($89–$350+/mo).

Our winning edge:
1. **Specialized Book Intelligence**: Native ISBN-10/13 checksum validation, asynchronous multi-provider bibliographic metadata retrieval (Google Books + Open Library API with local disk caching).
2. **Sub-Millisecond Keyboard-First POS**: F1–F12 quick action shortcuts, continuous barcode scanner wedge buffering, split tender payments (Cash, Card, QR, Store Credit), change calculation, and cart hold/resume.
3. **Hardware & Receipt Superiority**: Direct binary ESC/POS thermal printing (80mm/58mm), automatic cash drawer kick, vector branded PDF invoices, and multi-regional tax engine (USA sales tax, UK/EU VAT, Gulf 5–15% bilingual Arabic/English).
4. **Inventory & Margin Intelligence**: Low-stock dynamic safety alerts, Dead Stock Radar (30/60/90/180-day non-moving titles for distributor returns), batch stocktaking mode, and custom barcode/spine label generation.
5. **Zero Recurring Fees & True Offline Sovereignty**: Single `.exe` Windows distribution with embedded SQLite WAL mode, zero cloud latency, zero downtime, and complete data privacy.

## Requirements

### R1. Intelligent Online/Offline ISBN Metadata Enrichment Engine
Implement an asynchronous bibliographic metadata retrieval service (`MetadataService`):
- When an ISBN-10 or ISBN-13 is scanned/entered in Stock Management or POS:
  - Validate ISBN checksums (Mod 11 and Mod 10) with instant bidirectional conversion.
  - Check local SQLite catalog cache first.
  - If not found, asynchronously query Google Books API and Open Library API in the background.
  - Automatically populate Title, Subtitle, Author(s), Publisher, Publication Year, Edition, Format, Genre/BISAC, and fetch/cache cover art.
  - Graceful fallback for offline mode without blocking UI threads.

### R2. Keyboard-First POS Checkout & Split Tender Payments
Enhance `PosBillingController` and `BillingService`:
- Keyboard hotkeys (`F1`–`F12`) for zero-touch mouse operation during rapid checkout.
- Barcode scanner wedge input support with continuous rapid scanning.
- Split payment tender capabilities: Split across Cash (with automated change calculation), Card, and Store Credit.
- Park and resume multiple active carts (hold order).
- Line-item notes, discounts (item % or cart %), and gift receipt generation.

### R3. Thermal ESC/POS & Label Printing Engine
Implement direct thermal printer hardware support alongside existing OpenPDF generation:
- Raw ESC/POS bytecode streamer over USB / Serial / LAN port for 80mm and 58mm thermal receipt printers.
- Instant sub-200ms printing, paper cut command, and cash drawer kick pulse (`0x1B, 0x70, 0x00, 0x19, 0xFA`).
- Shelf and spine barcode label generator (Code-128 / EAN-13) formatted for standard thermal label rolls (e.g. 50x30mm).

### R4. Inventory Intelligence, Dead Stock Radar & Financial Z-Reports
Enhance inventory management and analytics:
- **Dead Stock Radar**: Identify and export non-moving inventory (60/90/180 days without sales) grouped by publisher for return-to-vendor processing.
- **Dynamic Reorder Assistant**: Safety stock warnings based on sales velocity and minimum thresholds.
- **Daily Financial Z-Report**: End-of-day register closure, expected vs actual cash reconciliation, tax summaries, and non-resettable audit ledger.

### R5. Semantic Versioning & Continuous Dev-Mode Verification
- Bump semantic version from `1.0-SNAPSHOT` to `1.1.0` in `pom.xml`, application title bar, and system metadata.
- Ensure all 391+ tests pass with zero regressions.
- Keep the application runnable in development mode (`mvnw.cmd javafx:run`) during continuous iteration.

## Acceptance Criteria

### ISBN & Catalog Enrichment
- [ ] Scanning valid ISBN-10 or ISBN-13 auto-populates book metadata from Google Books / Open Library within 1.5 seconds when online.
- [ ] Invalid ISBN checksums are flagged immediately with user-friendly corrective guidance.
- [ ] Catalog works completely offline with zero UI freezes or crashes.

### POS Checkout & Billing
- [ ] POS supports full keyboard checkout workflow using F-keys without requiring mouse interaction.
- [ ] Split tender transactions (e.g., $20 Cash + remaining on Card) compute and record accurately in SQLite `invoice_items` and `invoices`.
- [ ] Multiple shopping carts can be parked and resumed without data loss.

### Printing & Hardware
- [ ] Thermal ESC/POS byte sequence generates valid receipt formatting and cash drawer kick.
- [ ] OpenPDF multi-regional invoices preserve branding, QR codes, and bilingual Arabic/English layouts.

### Versioning & Verification
- [ ] `pom.xml` reflects updated version `1.1.0`.
- [ ] 100% of unit and E2E test suites pass cleanly on JDK 21 LTS (`mvnw.cmd test`).


## 2026-10-23T04:36:04Z

Comprehensive competitive feature enhancement and real commercial software visual "Look & Feel" upgrade for JavaFX Desktop Bookstore & Stationery Billing & Inventory against industry-leading POS systems (Square for Retail, Loyverse, Lightspeed Retail, BookManager, and Shopify POS). Implements winning features systematically, elevates the UI to premium dark/light commercial terminal styling with visual book cards and dual-mode POS, maintains semantic versioning (v1.2.0), and keeps dev mode active.

Working directory: e:\javafx-desktop-bookstore-billing
Integrity mode: development

## Requirements

### R1. Competitor Gap Analysis & Feature Superiority Matrix
Analyze top commercial POS platforms (Square for Retail, Loyverse, Lightspeed Retail, BookManager, Basil):
- Compare core bookstore & retail capabilities: catalog visual presentation, quick action chips, cart hold/resume, split tender calculations, multi-regional tax receipts (bilingual ZATCA QR, UK VAT, USA Sales Tax), and offline persistence.
- Document and implement clear winning differentiators: zero monthly cloud subscription fees, zero internet latency, local embedded SQLite WAL persistence, and direct binary ESC/POS thermal printing with cash drawer kick.

### R2. Real Commercial Software Visual "Look & Feel" (Dark/Light Fluent UI)
Transform the POS interface to match high-end modern retail terminals:
- **Visual Product Card Grid**: Dynamic tile view displaying book cover thumbnails / category icons, title, author, price, and instant stock badge (Green = In Stock, Amber = Low Stock, Red = Out of Stock).
- **Category Filter Pills**: Quick-filter chips (All, Fiction, Non-Fiction, Academic, Stationery, Magazines, Best Sellers) for one-tap catalog browsing.
- **Dark & Light Commercial Themes**: One-click toggle between AtlantaFX Primer Light and Primer Dark with persistent user theme preference in SQLite store settings.
- **Premium Order Summary Card**: Styled cashier cart sidebar with elevation shadow, line-item badges, quick discount chips (-5%, -10%, -20%), and large high-contrast total readout.

### R3. Dual-Mode POS Layout (Touch-Grid vs. Turbo-Keyboard Mode)
Provide seamless switching between two industry-standard POS operational modes:
- **Visual Touch Grid Mode**: Large card buttons optimized for touchscreen terminals, mouse navigation, and counter sales.
- **Turbo Keyboard Mode**: Dense, rapid keyboard-only workflow (`F1`–`F12`, barcode wedge auto-scan, instant split tender modal, cash change computation) for high-traffic peak retail hours.

### R4. Semantic Versioning, Dev-Mode Execution & Continuous Verification
- Bump application semantic version from `v1.1.0` to `v1.2.0` across `pom.xml`, Windows packaging metadata, and application title bar.
- Keep the application running in live dev mode (`mvnw.cmd javafx:run`) during iterative development.
- Maintain 100% test pass rate across the full suite of 544+ unit and integration tests with zero regressions.

## Acceptance Criteria

### Look & Feel and Visual Cards
- [ ] Catalog screen renders responsive visual product cards with cover images/icons, price badges, and stock level indicators.
- [ ] Category filter pills filter the active grid instantly without UI lag.
- [ ] Theme switcher toggles between Primer Light and Primer Dark dynamically and persists across app restarts.
- [ ] Cart summary sidebar displays large high-contrast subtotal, discount, tax, and total figures with clear tender buttons.

### Dual-Mode Workflow & POS Usability
- [ ] Cashier can toggle between Touch Tile Grid and Turbo Keyboard mode with a single click or keyboard shortcut.
- [ ] Clicking a product card adds it to the active cart with audio/visual feedback; repeated clicks increment quantity.
- [ ] Keyboard shortcuts (`F1`–`F12`), barcode scanner wedge, parked carts, and split tenders operate seamlessly across both modes.

### Verification & Versioning
- [ ] Application title bar and `pom.xml` reflect `v1.2.0`.
- [ ] Full automated test suite passes cleanly on JDK 21 LTS (`mvnw.cmd test`).
- [ ] Application launches cleanly in dev mode (`mvnw.cmd javafx:run`) on Windows with zero external network dependencies.
