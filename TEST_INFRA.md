# Comprehensive End-to-End Test Infrastructure (TEST_INFRA) — v1.2.0

## 1. Test Philosophy & Dual-Track Verification

The JavaFX Desktop Bookstore & Stationery Billing & Inventory system follows an uncompromising **Dual-Track Testing Architecture**, where end-to-end verification and quality assurance run concurrently with active feature development.

### Core Principles
1. **Opaque-Box & Requirement-Driven Verification**:
   Tests evaluate observable external contracts: SQLite database tables and schema migrations (`bookstore.db`), RFC 4180 CSV export/import ledgers, vector PDF invoices, raw ESC/POS bytecode streams, and UI state models without depending on internal implementation details.
2. **Deterministic Mathematical Oracle**:
   All financial, tax, discount, split-tender, and cash change calculations are derived strictly from authoritative mathematical formulas using `BigDecimal` with `RoundingMode.HALF_UP` to exactly 2 decimal places ($0.01$):
   - $\text{subtotal} = \sum (\text{unit\_price} \times \text{quantity})$
   - $\text{discount\_amount} = \text{subtotal} \times \left(\frac{\text{discount\_percent}}{100.0}\right)$
   - $\text{taxable\_amount} = \text{subtotal} - \text{discount\_amount}$
   - $\text{tax\_amount} = \text{taxable\_amount} \times \left(\frac{\text{tax\_rate}}{100.0}\right)$
   - $\text{net\_total} = \text{taxable\_amount} + \text{tax\_amount}$
   - $\text{split\_tender\_balance} = \text{net\_total} - \sum \text{tendered\_amounts}$
   - $\text{cash\_change\_due} = \text{cash\_tendered} - (\text{net\_total} - \sum \text{non\_cash\_tenders})$
3. **Headless Execution Safety & Complete Isolation**:
   Tests must run hermetically on both developer workstations and headless CI environments (Windows, Linux, macOS). JavaFX platform initialization occurs once via `Platform.startup(() -> {})`, audio synthesis is verified safely in daemon threads without blocking UI or requiring physical soundcards, and all persistence operations use temporary SQLite databases with automated teardown.
4. **Progressive Testability & Zero Regressions**:
   New v1.2.0 test classes strictly maintain 100% backward compatibility with all 544 pre-existing tests across core services, DAOs, controllers, and v1.0/v1.1 E2E tiers.

---

## 2. Feature Inventory (v1.2.0)

| Requirement | Feature ID | Feature Name | Target Scope | Key Contracts & Interfaces |
|---|---|---|---|---|
| **R1** | **F1.1** | **Competitor Benchmark Matrix** | Comprehensive 25-criteria benchmark against Square for Retail, Loyverse, Lightspeed Retail, BookManager, and Basil | `docs/COMPETITOR_MATRIX.md` |
| **R1** | **F1.2** | **In-App About & Superiority Modal** | Modal dialog displaying version, offline architecture, and competitor differentiators | `MainShellController.handleAboutDialog`, `AboutDialog.fxml` |
| **R1** | **F1.3** | **Core Differentiator Validation** | Validation of $0 SaaS lifetime fees, <1ms local SQLite WAL latency, 100% offline sovereignty, and direct ESC/POS hardware control | `DatabaseManager`, `EscPosPrintService`, system architecture |
| **R2** | **F2.1** | **Dark & Light Commercial Themes** | Runtime switching between AtlantaFX `PrimerLight` and `PrimerDark` with CSS root variable remapping | `ThemeManager`, `modern-fluent.css` |
| **R2** | **F2.2** | **Theme Preference Persistence** | Schema Migration V3 adding `ui_theme` column to `store_settings` and DAO synchronization | `SchemaMigrationManager`, `SettingsDao`, `StoreSettings` |
| **R2** | **F2.3** | **Visual Product Card Grid** | 155x195px responsive cards with cover art thumbnails, category glyphs, title, author, price, and 3-state stock badges | `PosBillingController.renderProductTiles`, `Product` |
| **R2** | **F2.4** | **Category Filter Pills** | One-tap catalog chips (`All Items`, `Fiction`, `Non-Fiction`, `Academic`, `Stationery`, `Magazines`, `Best Sellers`) | `PosBillingController.setupCategoryChips`, `FilteredList` |
| **R2** | **F2.5** | **Premium Order Summary Card** | Elevation drop shadow, line-item badges, -5%/-10%/-20% quick discount chips, and bold 28px total readout | `PosBilling.fxml`, `modern-fluent.css` |
| **R3** | **F3.1** | **Dual-Mode POS Architecture** | Seamless toggle between `TOUCH_GRID` and `TURBO_KEYBOARD` via `F2` and toolbar button without cart reset | `PosBillingController.PosMode`, `togglePosMode()` |
| **R3** | **F3.2** | **Scanner Wedge & Keyboard Hook** | Continuous hardware barcode scan buffering and F1–F12 quick action handling | `PosBillingController.setupKeyboardAndScannerBuffer`, `BarcodeScannerBuffer` |
| **R3** | **F3.3** | **Tactile & Synthesized Audio Feedback** | Card click `ScaleTransition` pulse, repeated-click quantity increments, and non-blocking PCM audio tones via `javax.sound.sampled` | `PosAudioService.playItemAdded()`, `playError()` |
| **R3** | **F3.4** | **Interactive Split Tender Modal** | Multi-tender checkout modal across Cash, Card, and Store Credit with exact cash change math | `SplitTenderDialog`, `InvoiceDao.createInvoice`, `invoice_payments` |
| **R4** | **F4.1** | **Semantic Versioning v1.2.0** | Version bump across `pom.xml`, Windows packaging scripts, window titles, and system metadata | `pom.xml`, `installer.iss`, `package.bat`, `package.ps1`, `Main.java` |
| **R4** | **F4.2** | **Continuous Verification Guard** | 100% test pass rate maintained across 544+ baseline tests with zero regressions | `mvnw.cmd test` |
| **R4** | **F4.3** | **Offline Dev-Mode Sovereignty** | Modular, network-independent local launch in development mode | `mvnw.cmd javafx:run`, `RunFX`, `Main` |

---

## 3. 4-Tier Test Architecture (v1.2.0 Track)

The v1.2.0 E2E test suite resides in package `com.example.t.e2e.v12` and organizes verification into four structured tiers:

```
src/test/java/com/example/t/e2e/v12/
├── V12CompetitorMatrixE2ETest.java       # R1 & R4: Competitor benchmark, versioning & differentiators
├── V12CommercialLookFeelE2ETest.java     # R2: Themes, visual cards, category pills, order summary
└── V12DualModePosE2ETest.java            # R3: Dual-mode POS, scanner wedge, audio tones, split tender
```

```
                                  [ Tier 4 ]
                         Real-World Retail Scenarios
                    Peak Retail Rush • Night Shift Workflow
                  Multi-Tender High-Volume • Dual-Mode Counter
               ─────────────────────────────────────────────────
                                  [ Tier 3 ]
                        Cross-Feature Pairwise Matrix
                     Theme Switch during Active Split Cart
                      Dual-Mode Toggle with Parked Carts
                     Barcode Wedge Scan in Touch-Grid Mode
               ─────────────────────────────────────────────────
                                  [ Tier 2 ]
                        Boundary & Corner Case Stress
                     Over-tender / Under-tender Split Math
                     Rapid Theme Flips • Empty Category Pills
                     Missing Cover Art • Zero & Max Stock Badges
               ─────────────────────────────────────────────────
                                  [ Tier 1 ]
                          Core Feature Verification
                      >=5 Discrete Tests per Feature Area
                     R1 Matrix & About • R2 Themes & Cards
                     R3 Dual POS & Tenders • R4 Versioning
```

---

## 4. Detailed Test Tier Scenarios

### Tier 1: Feature Coverage ($\ge 5$ Tests per Feature Area)

#### Area R1: Competitor Matrix & System Differentiators (F1.1, F1.2, F1.3)
1. `testCompetitorMatrixFileCompleteness`: Verifies `docs/COMPETITOR_MATRIX.md` exists, contains all 5 target competitors (Square, Loyverse, Lightspeed, BookManager, Basil), covers all 25 benchmark criteria, and provides 1/3/5-year TCO calculations.
2. `testInAppAboutDialogAccessibility`: Verifies `MainShellController` exposes about dialog navigation via `navAboutBtn` and `versionLabel` click handlers.
3. `testZeroSaaSAndOfflinePersistenceContract`: Verifies application persistence architecture uses embedded SQLite 3 WAL mode with zero cloud subscription lock-in.
4. `testDirectEscPosHardwareCapabilities`: Verifies binary ESC/POS command sequences for drawer kick (`0x1B, 0x70, 0x00, 0x19, 0xFA`), cut, and instant formatting.
5. `testMultiRegionalTaxAndZatcaQrSupport`: Verifies multi-regional tax engine contracts for USA sales tax, UK VAT, and Gulf bilingual ZATCA Phase 2 QR generation.

#### Area R2: Real Commercial Visual Look & Feel (F2.1, F2.2, F2.3, F2.4, F2.5)
1. `testThemeManagerTogglePrimerLightToDark`: Verifies runtime theme toggle sets AtlantaFX Primer Dark and applies `.theme-dark` root style class.
2. `testThemePersistenceInStoreSettings`: Verifies SQLite schema migration V3 adds `ui_theme` column to `store_settings` and `SettingsDao` persists updates.
3. `testProductTileRenderingAndMetadata`: Verifies product cards render title, author, price, and cover image container / category glyph.
4. `testCategoryFilterPillsPopulation`: Verifies category chips display `All Items`, `Fiction`, `Non-Fiction`, `Academic`, `Stationery`, `Magazines`, and `Best Sellers`.
5. `testOrderSummaryCardElevationAndReadout`: Verifies cashier sidebar layout contains elevation styling, discount buttons (-5%, -10%, -20%), and high-contrast total readout.

#### Area R3: Dual-Mode POS & Interactive Workflow (F3.1, F3.2, F3.3, F3.4)
1. `testDualModePosSwitching`: Verifies switching between `TOUCH_GRID` and `TURBO_KEYBOARD` adjusts catalog table/grid visibility while preserving active cart contents.
2. `testKeyboardAndScannerBufferHooked`: Verifies `setupKeyboardAndScannerBuffer()` registers keyboard event filters and barcode listener on POS startup.
3. `testProductCardClickCartIncrement`: Verifies clicking product tiles in touch grid adds items and repeated clicks increment line-item quantity.
4. `testSynthesizedAudioToneNonBlocking`: Verifies `PosAudioService` synthesizes 1850Hz chime and 350Hz error buzz asynchronously and safely in headless mode.
5. `testSplitTenderMathematicalCalculation`: Verifies split payment computation across Cash, Card, and Store Credit with exact cash change math.

#### Area R4: Semantic Versioning & System Consistency (F4.1, F4.2, F4.3)
1. `testPomXmlVersion120`: Verifies `pom.xml` contains `<version>1.2.0</version>` and `<app.version>1.2.0</app.version>`.
2. `testPackagingScriptsVersion120`: Verifies `installer.iss`, `package.bat`, and `package.ps1` reference version `1.2.0`.
3. `testMainWindowTitleVersion120`: Verifies application startup title reflects `v1.2.0`.
4. `testSidebarVersionLabel120`: Verifies `MainShell.fxml` sidebar label displays `Version 1.2.0 • Offline Ready`.
5. `testDevModeExecutionContract`: Verifies `RunFX` and `Main` are executable without external network connectivity.

---

### Tier 2: Boundary & Corner Cases ($\ge 5$ Tests per Feature Area)

1. **Split Tender Boundaries**:
   - `testSplitTenderExactTenderZeroChange`: Sum of split tenders equals net total exactly ($0.00$ change).
   - `testSplitTenderOverCashTenderChangeDue`: Cash portion tendered exceeds remaining balance, producing positive cash change.
   - `testSplitTenderUnderTenderRejection`: Total split tenders less than net total is rejected.
   - `testSplitTenderSingleTenderFullCash`: Single cash tender through split dialog matching standard cash flow.
   - `testSplitTenderNonCashZeroChange`: Card or Store Credit cannot produce cash change.
2. **Visual & Theme Boundaries**:
   - `testRapidThemeSwitchingStability`: 50 rapid alternating toggles between Light and Dark themes without memory leakage or state desync.
   - `testThemePersistenceFallback`: Unknown or null theme defaults safely to `"LIGHT"`.
   - `testProductTileMissingCoverFallback`: Product without cover image displays high-contrast category glyph (`📖`, `✏`, etc.) without crashing.
   - `testProductTileStockBadgesThreeStates`: Zero stock displays `.stock-out` (Red), low stock ($\le \text{threshold}$) displays `.stock-low` (Amber), abundant stock displays `.stock-in` (Green).
   - `testCategoryFilterEmptyResults`: Selecting a category with 0 products displays an empty grid gracefully without UI freeze.
3. **POS Workflow Boundaries**:
   - `testModeSwitchDuringEmptyCart`: Toggling between Touch Grid and Turbo Keyboard with an empty cart.
   - `testModeSwitchMaxItemsCart`: Toggling modes with 100+ line items without lag or item loss.
   - `testBarcodeScanDuringTouchGridMode`: Hardware barcode scan received while cashier is browsing touch tile grid seamlessly adds item.
   - `testKeyboardShortcutsDisabledInModal`: F-keys do not trigger background screen actions while split tender modal is open.
   - `testAudioServiceAudioSystemUnavailable`: Audio service catches `LineUnavailableException` / `SecurityException` silently in constrained environments.

---

### Tier 3: Cross-Feature Pairwise Interactions

1. **Theme Toggle during Active Cart with Split Tender**:
   Switching between Light and Dark mode while an active cart has split tenders pending recalculates and maintains payment state cleanly.
2. **Dual-Mode Toggle with Multiple Parked Carts**:
   Toggling from Touch Grid to Turbo Keyboard mode with 3 carts parked in memory preserves all parked carts and recalls them accurately.
3. **Continuous Barcode Scan + Touch Grid Card Click Interleaving**:
   Cashier scans 2 items via barcode wedge, taps 1 product card on screen, and scans another item; cart contains all 4 items with correct quantities.
4. **Category Filter Chip + Quick Discount Chip Interaction**:
   Filtering grid by "Stationery", selecting 3 items, applying `-10%` quick discount chip, and verifying net total and tax breakdown.
5. **Theme Switch + SQLite Settings DAO Mutation**:
   Toggling theme persists `ui_theme` to SQLite `store_settings`, followed by store name update; both settings persist across reload.
6. **Out-of-Stock Card Tap + Error Audio Buzz**:
   Tapping a product card with 0 inventory triggers error audio buzz without incrementing cart items.
7. **Multi-Currency Regional Preset Switch + Split Tender Math**:
   Switching preset to UK (£ GBP, 20% VAT) and performing split tender calculation with pence rounding precision.
8. **ZATCA Bilingual Invoice + ESC/POS Receipt Kick after Split Tender Checkout**:
   Completing a split tender transaction generates both ESC/POS drawer kick byte sequences and valid ZATCA QR TLV records.

---

### Tier 4: Real-World Retail Scenarios

1. **Scenario 1: Peak Retail Bookstore Rush (Touch-to-Turbo Speed Workflow)**:
   - Cashier starts in Visual Touch-Grid Mode for walk-in customer browsing.
   - Taps 2 fiction bestsellers and 1 stationery notebook.
   - Sudden queue surge occurs: Cashier presses `F2` to switch to Turbo-Keyboard Mode.
   - Rapidly scans 3 barcode items via wedge buffer.
   - Hits `F8` to open Split Tender: Customer pays $50.00 Cash + $24.50 on Card.
   - System computes exact change due, records atomic invoice and payment breakdown in SQLite, kicks cash drawer, and readies register for next customer in <100ms.
2. **Scenario 2: Academic Semester Opening Rush (Category Filtering & Bulk Discounts)**:
   - Student purchases multiple textbooks and stationery supplies.
   - Cashier uses Category Filter Pills (`Academic` -> selects Calculus, Physics; `Stationery` -> selects Notebooks, Pens).
   - Applies `-10%` educational discount chip.
   - Customer pays with Student Store Credit and Cash.
   - System updates inventory stock levels and verifies no overselling.
3. **Scenario 3: Night Shift Retail Operation (Dark Theme & Register Auditing)**:
   - Cashier toggles application to Primer Dark theme for reduced eye strain during evening hours.
   - Processes mixed sales transactions with varying payment tenders.
   - Closes register: Generates end-of-day Z-Report reconciling expected cash vs actual cash count.
   - Verifies theme preference remains Dark on subsequent restarts.
4. **Scenario 4: High-Street International Bookshop (Multi-Tax Split Tender & Thermal Receipt)**:
   - High-street store operating with multi-regional presets (UK GBP with split zero-rated books and standard-rated stationery).
   - Customer splits tender between Card and Cash.
   - System computes VAT lines, prints dual ESC/POS thermal receipt with drawer kick, and archives invoice to PDF.

---

## 5. Verification Matrix & Quality Thresholds

| Quality Metric | Target Threshold | Status / Enforcement |
|---|---|---|
| **Compilation** | Clean compilation via `mvnw.cmd test-compile` | Strict (0 errors, 0 warnings) |
| **Full Suite Pass Rate** | 100% pass rate across $\ge 544$ pre-existing tests | Strict (0 failures, 0 regressions) |
| **New v1.2.0 Tests** | $\ge 40$ discrete tests across Tiers 1–4 | Strict |
| **Monetary Math Precision** | Exact to 2 decimal places ($0.01$) via `BigDecimal` | Strict |
| **Headless Safety** | Tests pass without graphical desktop or physical audio hardware | Strict |
| **Database Isolation** | Hermetic temporary SQLite databases per test class | Strict |

---

## 6. Execution Instructions

```powershell
# Compile the test suite
.\mvnw.cmd test-compile

# Run all v1.2.0 E2E tests
.\mvnw.cmd test -Dtest="com.example.t.e2e.v12.*Test"

# Run individual v1.2.0 test suites
.\mvnw.cmd test -Dtest="com.example.t.e2e.v12.V12CompetitorMatrixE2ETest"
.\mvnw.cmd test -Dtest="com.example.t.e2e.v12.V12CommercialLookFeelE2ETest"
.\mvnw.cmd test -Dtest="com.example.t.e2e.v12.V12DualModePosE2ETest"

# Run the complete test suite (baseline + v1.2.0)
.\mvnw.cmd test
```
