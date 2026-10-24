# Project: Modern Offline JavaFX Desktop Bookstore & Stationery Billing & Inventory (v1.2.0)

## Architecture
- **Runtime & Target**: OpenJDK 21 LTS, JavaFX 21.0.6, ControlsFX 11.2.1, AtlantaFX 2.0.1 (Windows 11 Fluent theme).
- **UI Architecture**: Single-stage Master Navigation Shell with dual commercial themes (AtlantaFX Primer Light & Primer Dark) toggled dynamically with SQLite persistence; Dual-Mode POS (Visual Touch-Grid Mode vs. Turbo-Keyboard Mode).
- **Persistence Architecture**: Embedded SQLite 3 (`bookstore.db`) under `%LOCALAPPDATA%\BookstoreBilling\data\` with WAL mode, foreign keys, automated schema migrations (`schema_migrations` table), and `store_settings.ui_theme` preference.
- **Hardware & Printing Engine**: Raw ESC/POS bytecode streamer (80mm / 58mm), paper cut command, and cash drawer kick pulse (`0x1B, 0x70, 0x00, 0x19, 0xFA`). OpenPDF 1.3.40 generating multi-regional invoices and 50x30mm thermal shelf/spine labels.
- **Audio & Haptics Engine**: Zero-external-dependency non-blocking PCM audio synthesizer (`javax.sound.sampled` via daemon thread) with JavaFX `ScaleTransition` card feedback.
- **Competitor Superiority**: Documented and in-app verified differentiators over Square for Retail, Loyverse, Lightspeed Retail, BookManager, and Basil ($0 SaaS fees, <1ms local WAL latency, 100% offline sovereignty, direct binary hardware control).
- **Packaging & Dev-Mode**: Zero-dependency Windows distribution (`v1.2.0`) via hybrid `jlink` + `jpackage`, and headless-safe modular dev-mode (`mvnw.cmd javafx:run`).

## Feature Inventory (v1.2.0)
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Competitor Benchmarking Matrix | 25-criteria benchmark against Square, Loyverse, Lightspeed, BookManager, Basil in `docs/COMPETITOR_MATRIX.md` | M1.2-A | Explorer 3 / R1 |
| 2 | In-App About & Comparison Modal | In-app dialog displaying superiority matrix & offline architecture differentiators | M1.2-A | Explorer 3 / R1 |
| 3 | Semantic Version Bump (v1.2.0) | Update version string to 1.2.0 across pom.xml, installer.iss, package scripts, Main, MainShell, tests | M1.2-A | Explorer 3 / R4 |
| 4 | SQLite Schema Migration V3 | Add `ui_theme TEXT DEFAULT 'LIGHT'` to `store_settings` table | M1.2-B | Explorer 1 / R2 |
| 5 | ThemeManager & AtlantaFX Toggle | Primer Light / Dark runtime switching with SQLite persistence and root CSS inversion | M1.2-B | Explorer 1 / R2 |
| 6 | Visual Product Card Grid | 155x195px cards with cover thumbnails, category glyphs, title, author, price, 3-state stock badges | M1.2-B | Explorer 1 / R2 |
| 7 | Category Filter Pills | Quick-filter chips (All, Fiction, Non-Fiction, Academic, Stationery, Magazines, Best Sellers) | M1.2-B | Explorer 1 / R2 |
| 8 | Premium Order Summary Card | Elevation drop shadow, line-item badges, -5%/-10%/-20% quick discount chips, 28px bold total readout | M1.2-B | Explorer 1 / R2 |
| 9 | Keyboard & Scanner Buffer Hook | Fix startup defect by calling `setupKeyboardAndScannerBuffer()` in `PosBillingController.initialize()` | M1.2-C | Explorer 2 / R3 |
| 10 | Dual-Mode POS Architecture | `PosMode` (Touch-Grid vs. Turbo-Keyboard) toggle via `F2` and toolbar button without cart reset | M1.2-C | Explorer 2 / R3 |
| 11 | Card Click Tactile & Audio Feedback | Non-blocking PCM audio chime/buzz via `javax.sound.sampled` daemon thread + scale pulse; quantity increment | M1.2-C | Explorer 2 / R3 |
| 12 | Interactive Split Tender Modal | `SplitTenderDialog` calculating remaining balance & cash change across Cash, Card, Store Credit | M1.2-C | Explorer 2 / R3 |
| 13 | Full Test Suite & Continuous Verification | Maintain 100% pass rate across all 544+ tests with zero regressions on JDK 21 LTS | M1.2-D | Explorer 3 / R4 |
| 14 | Dev-Mode Execution & Offline Sovereignty | Verify `mvnw.cmd javafx:run` launches modularly without internet dependencies | M1.2-D | Explorer 3 / R4 |

## Milestones (v1.2.0)
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1.2-A | Competitor Gap Analysis & Semantic Versioning (R1, R4a) | Features #1, #2, #3: Competitor matrix document, in-app comparison dialog, version bump across 7 files / 11 lines | none | PLANNED |
| M1.2-B | Real Commercial Visual Look & Feel & Themes (R2) | Features #4, #5, #6, #7, #8: Schema migration V3, ThemeManager, visual cards, category pills, order summary card | M1.2-A | PLANNED |
| M1.2-C | Dual-Mode POS Layout & Interactive Workflow (R3) | Features #9, #10, #11, #12: Keyboard & scanner buffer hook, Touch vs Turbo mode, audio/visual click feedback, SplitTenderDialog | M1.2-B | PLANNED |
| M1.2-D | Continuous Verification, E2E Testing & Dev-Mode (R4b) | Features #13, #14: 100% test pass (544+ tests), new v1.2.0 test coverage, live dev-mode verification | M1.2-C | PLANNED |

## Interface Contracts (v1.2.0)

### 1. Theme & Settings Contract (`com.example.t.util.ThemeManager` & `com.example.t.model.StoreSettings`)
- `StoreSettings`:
  - `String getUiTheme()` / `void setUiTheme(String uiTheme)` (values: `"LIGHT"`, `"DARK"`).
- `ThemeManager`:
  - `void applyTheme(Scene scene, String themeName, SettingsDao settingsDao)`
  - `void toggleTheme(Scene scene, SettingsDao settingsDao)`
  - `String getCurrentTheme()`

### 2. Dual-Mode POS Contract (`com.example.t.controller.PosBillingController`)
- `PosMode` enum: `TOUCH_GRID`, `TURBO_KEYBOARD`.
- `void setPosMode(PosMode mode)`
- `PosMode getPosMode()`
- `void togglePosMode()`
- Behaviors:
  - `TOUCH_GRID`: Catalog grid expanded with visual cards, category filter pills visible, cart sidebar elevated.
  - `TURBO_KEYBOARD`: Cart table maximized full-height, catalog grid collapsed (`visible=false, managed=false`), search/barcode field persistently focused.
  - Cart items, selected customer, and applied discount remain completely preserved across mode toggles.

### 3. POS Audio & Haptics Contract (`com.example.t.service.audio.PosAudioService`)
- `PosAudioService`:
  - `void playItemAdded()`: 1850Hz 45ms synthesized PCM chime (non-blocking daemon thread).
  - `void playError()`: 350Hz 120ms synthesized PCM buzz (non-blocking daemon thread).
  - Headless safe: catches all exceptions silently during headless CI/unit tests.

### 4. Split Tender Modal Contract (`com.example.t.controller.SplitTenderDialog`)
- Inputs: `double netTotal`, `String currencySymbol`.
- Outputs: `Optional<List<InvoicePayment>>` with calculated change due for cash tender.
- Validation: Total tendered payments must be $\ge \text{netTotal}$.

## Code Layout (v1.2.0 Additions)
```
docs/
└── COMPETITOR_MATRIX.md
src/main/java/com/example/t/
├── controller/
│   └── SplitTenderDialog.java
├── service/
│   └── audio/
│       └── PosAudioService.java
└── util/
    └── ThemeManager.java
src/main/resources/com/example/t/
├── css/
│   └── modern-fluent.css (dark theme rules & card elevations)
└── fxml/
    ├── AboutDialog.fxml (or modal in MainShell)
    └── SplitTenderDialog.fxml
```
