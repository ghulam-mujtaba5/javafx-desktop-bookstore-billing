## 2026-10-19T19:42:02Z

You are worker_m3, implementing Milestone 3 (Modern Fluent UI Shell & Stock Management) for e:\javafx-desktop-bookstore-billing.
Your working directory is: e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_m3
Your parent is: orchestrator_1 (conversation ID: a53fb0a7-174c-42cf-8f32-366cc200af43)

MANDATORY FIRST STEP: Read the authoritative requirements at:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\ORIGINAL_REQUEST.md
and the project architecture at:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\PROJECT.md
and survey reports:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_survey_2\handoff.md
e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_m2\handoff.md

Your exclusive write ownership:
- src/main/resources/com/example/t/fxml/MainShell.fxml
- src/main/resources/com/example/t/fxml/StockManagement.fxml
- src/main/resources/com/example/t/css/modern-fluent.css (and any CSS stylesheets)
- src/main/java/com/example/t/controller/MainShellController.java
- src/main/java/com/example/t/controller/StockManagementController.java
- src/main/java/com/example/t/Main.java (modernize to initialize DatabaseManager, apply AtlantaFX PrimerLight/Cupertino theme, and load MainShell as primary single-window stage)
- Cleaning up/modernizing legacy screen classes (AddStockScreen, UpdateStockScreen, ViewStockScreen) so they integrate cleanly with the new controllers and SQLite ProductDao/CategoryDao.
- Automated tests for UI shell and stock management in src/test/java/com/example/t/

Specific Milestone 3 deliverables:
1. AtlantaFX Windows 11 Fluent Theme Integration:
   - In `Main.java`, apply AtlantaFX theme (`Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());` or `CupertinoLight`).
   - Create `modern-fluent.css` for clean typography, Fluent sidebar styling, rounded cards, and status badges.
2. Master Navigation Shell (Single-Stage Architecture):
   - Replace legacy disjointed multi-window floating stages with a unified single desktop window.
   - `MainShell.fxml` & `MainShellController.java`:
     - Responsive left sidebar with navigation items: POS Billing (F5/M4), Stock Management (F12/M3), Invoices & History (F14/M4), Store Settings (F15/M4), and Exit.
     - Top header displaying store name, current regional preset/currency, and quick status.
     - Center Viewport dynamically swapping views using `StackPane` or `BorderPane`.
     - Toast notifications container for `NotificationUtil`.
3. Complete Product & Stock Management Screen:
   - `StockManagement.fxml` & `StockManagementController.java`:
     - Real-time TableView bound to `ProductDao` with observable product properties.
     - Filtering and search: Quick search by product name, SKU / barcode, or category (All, Books, Stationery, etc.).
     - Categorization: Support both Books (with Author, Publisher, Edition, ISBN) and Stationery (general merchandise).
     - Low-Stock Indicator: Visual badge and low-stock filter for products where `stock_quantity <= min_stock_threshold` (with warning badge).
     - Add Product Dialog/Form: Add new book or stationery item with validation, category selection, and instant database insert via `ProductDao`.
     - Update / Restock Dialog/Form: Edit item details, price, purchase price, or restock quantity (eliminating the legacy duplicate ID bug).
     - Delete action with confirmation dialog.
4. Clean Compilation & Test Pass:
   - Run `.\mvnw.cmd clean test-compile` and `.\mvnw.cmd test` to ensure 100% clean build with all M1, M2, and E2E tests passing!

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

When complete, write your handoff report to: `e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_m3\handoff.md` and send a completion message to the parent.
