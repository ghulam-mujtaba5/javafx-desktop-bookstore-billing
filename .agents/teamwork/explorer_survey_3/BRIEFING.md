# BRIEFING — 2026-10-19T18:33:00Z

## Mission
Investigate R3 (Persistence & Data Storage: SQLite, legacy import, CSV sync, AppData path) and R4 (Packaging & Standalone Windows Distribution: jpackage, modularity, JRE bundling) for the Bookstore Billing project.

## 🔒 My Identity
- Archetype: explorer
- Roles: explorer, synthesis
- Working directory: e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_survey_3
- Original parent: a53fb0a7-174c-42cf-8f32-366cc200af43
- Milestone: survey

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- Scope: R3 Persistence & Data Storage and R4 Packaging & Standalone Windows Distribution
- Produce comprehensive handoff report at e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_survey_3\handoff.md
- Never modify application source code

## Current Parent
- Conversation ID: a53fb0a7-174c-42cf-8f32-366cc200af43
- Updated: 2026-10-19T18:25:16Z

## Investigation State
- **Explored paths**:
  - `pom.xml`, `module-info.java`
  - `FileHandler.java`, `FilePathManager.java`, `Stock.java`, `Product.java`, `Order.java`, `Invoice.java`, `Shop.java`, `Password.java`
  - Screen controllers: `AddStockScreen.java`, `UpdateStockScreen.java`, `ViewStockScreen.java`, `CreateOrderScreen.java`, `ViewInvoicesScreen.java`, `SettingsScreen.java`, `Main.java`, `RunFX.java`
  - Legacy data files: `stock.txt`, `Shop_Data.txt`, `password.txt`, `invoice_history.txt`, `invoice_history.csv`
- **Key findings**:
  - Legacy persistence relies on hardcoded paths (`D:\t\src\main\...` and `C:\Program Files (x86)\...`), which cause immediate file errors on systems without D: drive.
  - Flat text file writes are non-atomic with zero concurrency or crash safety.
  - SQLite schema designed with 6 core tables: `schema_migrations`, `categories`, `products`, `invoices`, `invoice_items`, `store_settings`, with WAL mode and indexes.
  - AppData resolution strategy defined targeting `%LOCALAPPDATA%\BookstoreBilling\data\` with robust fallbacks.
  - Automatic migration runner using `PRAGMA user_version` planned with seed data for bookstore and stationery catalog items.
  - Backward-compatible legacy text importer designed for stock.txt, shop_data.txt, password.txt, and invoice_history.txt.
  - CSV sync designed with append-only ledger on invoice creation and full CSV export/import for catalog and invoices.
  - Packaging strategy formulated using Java 21 `jlink` minimal custom runtime (~38MB) + classpath application bundle via `jpackage --type app-image` (and optional WiX `.exe` / MSIX for Microsoft Store).
- **Unexplored areas**: None; all survey objectives addressed.

## Key Decisions Made
- Chose `%LOCALAPPDATA%` over `%APPDATA%` to prevent roaming profile SQLite locking/corruption issues.
- Adopted "runtime image + classpath app" architecture for `jpackage` to eliminate modularity conflicts with SQLite JDBC and PDF libraries while preserving tiny footprint.
- Designed unified `products` table supporting both Books and Stationery with category relationships.

## Artifact Index
- handoff.md — Comprehensive 5-component analysis report
- progress.md — Liveness heartbeat and step tracking
- DISPATCH.md — Incoming dispatch log
