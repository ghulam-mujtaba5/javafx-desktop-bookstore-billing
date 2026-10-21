# Comprehensive UI Screens, POS Workflows & AtlantaFX Fluent Styling Survey Report

**Explorer**: `explorer_survey_2`  
**Date**: 2026-10-19  
**Target Repository**: `e:\javafx-desktop-bookstore-billing`  
**Parent Agent**: `orchestrator_1` (Conversation ID: `a53fb0a7-174c-42cf-8f32-366cc200af43`)  
**Status**: Completed (Hard Handoff)

---

## 1. Observation

### 1.1 Source Code and Resource File Inventory

Direct inspection of `src/main/resources/com/example/t` and `src/main/java/com/example/t` revealed 14 FXML files, 3 CSS stylesheets, 2 image/icon assets in resources, and 23 Java source files:

| File Name | Location | Size / Lines | Status / Purpose |
|---|---|---|---|
| `AddStock.fxml` | `src/main/resources/...` | 1,582 B (28 lines) | Duplicate of `AddStockScreen.fxml`. Points to `AddStockScreen` controller. |
| `AddStockScreen.fxml` | `src/main/resources/...` | 1,582 B (28 lines) | Unused legacy FXML layout with fields `productIdField`, `productNameField`, etc. |
| `CreateOrderScreen.fxml` | `src/main/resources/...` | **0 B (empty)** | Blank placeholder file. |
| `Menu.fxml` | `src/main/resources/...` | 840 B (14 lines) | Simple VBox menu referencing `com.example.t.MenuController`. |
| `menu_screen.fxml` | `src/main/resources/...` | 1,367 B (22 lines) | Alternative Menu FXML referencing `MenuController` with close button. |
| `PasswordScreen.fxml` | `src/main/resources/...` | 395 B (12 lines) | Completely commented out XML. |
| `Password_Screen.fxml` | `src/main/resources/...` | 1,150 B (25 lines) | FXML for password with `onAction="#enterButtonAction"` (not in Java class). |
| `SettingsScreen.fxml` | `src/main/resources/...` | **0 B (empty)** | Blank placeholder file. |
| `UpdateStockScreen.fxml` | `src/main/resources/...` | **0 B (empty)** | Blank placeholder file. |
| `ViewInvoices.fxml` | `src/main/resources/...` | 1,884 B (39 lines) | Legacy semester project FXML with `invoiceTableView`. |
| `ViewInvoicesScreen.fxml`| `src/main/resources/...` | **0 B (empty)** | Blank placeholder file. |
| `viewOrder.fxml` | `src/main/resources/...` | 1,867 B (40 lines) | Legacy semester project FXML with `orderTableView`. |
| `ViewOrderScreen.fxml` | `src/main/resources/...` | 6,157 B (125 lines)| **CORRUPTED XML SYNTAX**: Broken tags prepended at lines 1–40 before root `<VBox>`. |
| `ViewStock.fxml` | `src/main/resources/...` | 1,202 B (23 lines) | Outdated semester project FXML titled "Make Order" pointing to non-existent `MakeOrderController`. |
| `ViewStockScreen.fxml` | `src/main/resources/...` | 1,732 B (33 lines) | BorderPane layout matching `@FXML` fields in `ViewStockScreen.java`. |
| `modern-theme.css` | `src/main/resources/...` | **0 B (empty)** | Blank stylesheet; referenced in `SettingsScreen.java:104`. |
| `modern-theme-new.css`| `src/main/resources/...` | **0 B (empty)** | Blank stylesheet. |
| `modern-windows-theme.css`| `src/main/resources/...`| **0 B (empty)** | Blank stylesheet. |
| `icon.ico` | `src/main/resources/...` | 0 B (in target) | Icon asset. |
| `Main.java` | `src/main/java/...` | 321 lines | Main application class. Builds `showMenuScreen()` entirely in procedural Java code. |
| `RunFX.java` | `src/main/java/...` | 8 lines | Launcher class calling `Main.main(args)`. |
| `PasswordScreen.java` | `src/main/java/...` | 112 lines | Extends `Application`. Pure Java layout (`GridPane`). |
| `AddStockScreen.java` | `src/main/java/...` | 285 lines | Pure Java layout (`GridPane`, `TableView`). Hardcoded image path `file:///D:/t/src/main/background1.jpg`. |
| `UpdateStockScreen.java`| `src/main/java/...` | 427 lines | Pure Java layout. Has TableView and modal dialogs. Duplicates products on add. |
| `CreateOrderScreen.java`| `src/main/java/...` | 406 lines | Pure Java layout. Hardcoded `D:\t\src\main\...`. Double-discount calculation bug. |
| `ViewOrderScreen.java` | `src/main/java/...` | 40 lines | Pure Java layout. Creates unpopulated TableView without column value factories. |
| `ViewStockScreen.java` | `src/main/java/...` | 82 lines | `@FXML` Controller. References non-existent `Product` properties (`author`, `publisher`, `edition`). Missing `show()` method. |
| `ViewInvoicesScreen.java`| `src/main/java/...` | 539 lines | `@FXML` Controller. Calls non-existent `Invoice` property methods and empty `NotificationUtil`. Missing `show()` method. |
| `SettingsScreen.java` | `src/main/java/...` | 122 lines | Extends `Stage`. Pure Java layout. Calls empty `Logger.java`. Missing multi-regional settings. |
| `MenuController.java` | `src/main/java/...` | **0 B (empty)** | Blank stub class. |
| `NotificationUtil.java`| `src/main/java/...` | **0 B (empty)** | Blank stub class. |
| `Logger.java` | `src/main/java/...` | **0 B (empty)** | Blank stub class. |
| `PasswordUtil.java` | `src/main/java/...` | **0 B (empty)** | Blank stub class. |
| `Invoice.java` | `src/main/java/...` | 68 lines | Plain POJO with standard getters. Missing JavaFX property methods (`invoiceIdProperty()`, etc.) and typo field `private double neprice;`. |
| `Product.java` | `src/main/java/...` | 138 lines | Model with JavaFX properties, but lacks category (Book vs Stationery), barcode/SKU, author, publisher, ISBN. |
| `FilePathManager.java` | `src/main/java/...` | 44 lines | Hardcoded paths: `D:\t\src\main\stock.txt`, `C:\Program Files (x86)\GM Shop\...`. |
| `FileHandler.java` | `src/main/java/...` | 139 lines | Path concatenation bug: `STOCK_FILE_PATH + File.separator + STOCK_FILE_NAME` (`...stock.txt\stock.txt`). |

### 1.2 Verbatim Compilation Failures
Executing `./mvnw.cmd test-compile` on the existing codebase fails with 9 distinct errors directly originating from the UI/controller mismatch:
```
[ERROR] /E:/javafx-desktop-bookstore-billing/src/main/java/com/example/t/ViewInvoicesScreen.java:[62,13] cannot access com.example.t.NotificationUtil
  bad source file: E:\javafx-desktop-bookstore-billing\src\main\java\com\example\t\NotificationUtil.java
    file does not contain class com.example.t.NotificationUtil
[ERROR] /E:/javafx-desktop-bookstore-billing/src/main/java/com/example/t/ViewInvoicesScreen.java:[69,77] cannot find symbol
  symbol:   method invoiceIdProperty()
  location: class com.example.t.Invoice
[ERROR] /E:/javafx-desktop-bookstore-billing/src/main/java/com/example/t/ViewInvoicesScreen.java:[70,80] cannot find symbol
  symbol:   method customerNameProperty()
  location: class com.example.t.Invoice
[ERROR] /E:/javafx-desktop-bookstore-billing/src/main/java/com/example/t/ViewInvoicesScreen.java:[71,72] cannot find symbol
  symbol:   method invoiceDateProperty()
  location: class com.example.t.Invoice
[ERROR] /E:/javafx-desktop-bookstore-billing/src/main/java/com/example/t/ViewInvoicesScreen.java:[72,73] cannot find symbol
  symbol:   method orderTotalProperty()
  location: class com.example.t.Invoice
[ERROR] /E:/javafx-desktop-bookstore-billing/src/main/java/com/example/t/ViewInvoicesScreen.java:[111,48] cannot find symbol
  symbol:   method readInvoicesFromFile()
  location: class com.example.t.Invoice
[ERROR] /E:/javafx-desktop-bookstore-billing/src/main/java/com/example/t/SettingsScreen.java:[51,17] cannot access com.example.t.Logger
  bad source file: E:\javafx-desktop-bookstore-billing\src\main\java\com\example\t\Logger.java
    file does not contain class com.example.t.Logger
[ERROR] /E:/javafx-desktop-bookstore-billing/src/main/java/com/example/t/Main.java:[280,28] cannot find symbol
  symbol:   method show()
  location: variable viewStockScreen of type com.example.t.ViewStockScreen
[ERROR] /E:/javafx-desktop-bookstore-billing/src/main/java/com/example/t/Main.java:[304,31] cannot find symbol
  symbol:   method show()
  location: variable viewInvoicesScreen of type com.example.t.ViewInvoicesScreen
```

### 1.3 Screen Navigation & Windowing Model
- In `Main.java`:
  - `start()` launches `PasswordScreen` on `primaryStage`.
  - On password success, `showMenuScreen()` is invoked, which constructs a menu UI with an unbounded background image and 7 buttons.
  - Clicking any button launches an independent `Stage` (`new AddStockScreen().show()`, `new UpdateStockScreen().show()`, `new CreateOrderScreen().show()`, `new SettingsScreen().openSettingsScreen()`).
  - Result: The user ends up with multiple disconnected top-level floating windows across their desktop instead of a single, cohesive desktop application shell.

### 1.4 Business Logic & POS Workflow Defects
1. **Double-Discount Bug in `CreateOrderScreen.java`**:
   - In `addButton.setOnAction` (lines 150–156):
     ```java
     double discountedPrice = selectedProduct.getPrice() * (1 - discount / 100);
     Product orderProduct = new Product(selectedProduct.getProductId(), selectedProduct.getProductName(), quantity, discountedPrice, discount);
     ```
   - In `generateInvoiceButton.setOnAction` (lines 182–183):
     ```java
     double discountedPrice = product.getPrice() * (1 - product.getDiscount() / 100);
     totalAmount += discountedPrice * product.getQuantity();
     ```
   - Because `product.getPrice()` already holds the discounted price, `totalAmount` discounts the price **a second time**! (e.g. $100 with 10% discount becomes $90, and then $81!).
2. **Duplicate Stock IDs in `UpdateStockScreen.java`**:
   - In `showAddStockDialog` (line 410):
     ```java
     Product newStock = new Product(selectedProduct.getProductId(), selectedProduct.getProductName(), newQuantity, salePrice, purchasePrice);
     data.add(newStock);
     ```
   - When a user restocks an existing product, it appends a new item with the identical `productId` rather than updating the quantity of the existing record.
3. **Random Order ID Collisions**:
   - `generateOrderId()` in `CreateOrderScreen.java:403` generates `(int) (Math.random() * 1000) + 1`. This allows collision after fewer than 40 orders due to the birthday paradox.
4. **Missing POS Calculations**:
   - No Sales Tax / VAT computation.
   - No Tendered Cash input field.
   - No Change Due calculation.
   - Hardcoded mixed currency strings (`$` in dialogs, `Rs` in text receipts, `$` in tables).

---

## 2. Logic Chain

### 2.1 The Architectural "Split Personality"
- **Observation**: `Main.java`, `PasswordScreen.java`, `AddStockScreen.java`, `UpdateStockScreen.java`, and `CreateOrderScreen.java` construct UI trees dynamically in Java using `new VBox()`, `new GridPane()`, `new Stage()`. Meanwhile, `ViewStockScreen.java` and `ViewInvoicesScreen.java` were partially rewritten with `@FXML` annotations, while their corresponding FXML files are either empty (`0 bytes`), duplicate semester project relics, or syntactically corrupted (`ViewOrderScreen.fxml`).
- **Inference**: An incomplete refactoring was started to migrate semester project Java code into modern FXML views, but was abandoned mid-way, leaving the project in a broken, non-compilable state.
- **Deduction**: We must decide on a clean, unified architectural model:
  - Option A: Complete the FXML MVC architecture with clean, well-formed FXML views and strongly-typed Controllers.
  - Option B: Use pure Java programmatic layout (or JavaFX DSL).
  - **Verdict**: Option A is standard JavaFX best practice, fully compatible with SceneBuilder and AtlantaFX CSS themes, and cleanly separates declarative layout from controller logic and database services.

### 2.2 Missing POS & Bookstore Requirements (R2 Gap Breakdown)
- **Observation R2.1 (Product & Stock Management)**:
  - `Product.java` contains only generic fields (`productId`, `productName`, `quantity`, `price`, `purchasePrice`).
  - There is no categorization for Books vs Stationery.
  - There are no book-specific attributes (Author, Publisher, Edition, ISBN).
  - There are no stationery-specific attributes (Brand, Unit).
  - There is no Barcode / SKU field or scanner input listener.
  - There is no threshold for low-stock warnings.
  - `ViewStockScreen.java` attempted to bind `author`, `publisher`, and `edition`, which caused TableView binding failures.
- **Observation R2.2 (Order & Billing POS Screen)**:
  - POS screen lacks real-time subtotal, discount total, tax/VAT calculation, and change computation.
  - POS screen lacks quick barcode scanning (with auto-focus on search field and instant cart addition).
  - POS screen lacks cart quantity adjustments (+ / - buttons or inline editing).
  - Double discount calculation bug corrupts invoice totals.
- **Observation R2.3 (Invoicing & History)**:
  - There is no PDF generation library or export code.
  - Receipt re-printing only relies on `javafx.print.PrinterJob` without thermal 80mm roll formatting.
  - `ViewInvoicesScreen.java` cannot load data because `Invoice.readInvoicesFromFile()` does not exist and `Invoice` lacks JavaFX observable properties.
- **Observation R2.4 (Settings & Store Configuration)**:
  - `SettingsScreen.java` only edits password and shop name/address/phone.
  - Zero multi-regional support: no currency selector, no tax rate/type selector, no VAT registration number, no bilingual invoice templates.
  - Admin password stored in cleartext.

### 2.3 AtlantaFX & Windows 11 Fluent UI Feasibility
- **Observation**: All three CSS files in `src/main/resources/com/example/t` are 0-byte empty files. Hardcoded inline styles and hardcoded absolute image paths (`D:\t\src\main\...`) are scattered across Java files.
- **Inference**: The application currently has no real stylesheet and relies on default Modena CSS or broken inline styles.
- **Deduction**: Adding `atlantafx-base:2.0.1` and applying `Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet())` or `CupertinoLight` immediately elevates all standard controls (Buttons, TextFields, Tables, Dialogs, CheckBoxes, DatePickers) to modern Windows 11 styling. A custom overlay stylesheet (`app-fluent.css`) will handle custom POS styling (cards, KPI tiles, cart badge, sidebar navigation, status pills).

---

## 3. Detailed Screen Inventory & Controller Bindings

### 3.1 Existing Screen Analysis Matrix

| Screen / Component | FXML File | Controller Class | Invocation Mechanism | Current State & Deficiencies |
|---|---|---|---|---|
| **App Launch / Auth** | `Password_Screen.fxml` (old: `PasswordScreen.fxml`) | `PasswordScreen.java` | `Main.java:52` -> `passwordScreen.start(primaryStage)` | Pure Java layout ignoring FXML. Hardcoded image path. Cleartext password check. |
| **Main Navigation Menu** | `Menu.fxml` / `menu_screen.fxml` | `MenuController.java` (empty stub) | `Main.java:66` -> `showMenuScreen()` | Procedural Java layout. 7 vertical buttons. Spawns new `Stage` per button. `MenuController.java` is 0 bytes. |
| **Add Stock** | `AddStockScreen.fxml` (duplicate `AddStock.fxml`) | `AddStockScreen.java` | `Main.java:274` -> `new AddStockScreen(stock).show()` | Pure Java layout. Hardcoded `D:\t\...` image path. No category (Books vs Stationery), no barcode, no low-stock limit. |
| **Update Stock** | `UpdateStockScreen.fxml` (0 bytes) | `UpdateStockScreen.java` | `Main.java:284` -> `new UpdateStockScreen().show()` | Pure Java layout. Duplicates product record upon restocking. Re-instantiates `new Stock()` repeatedly. |
| **View Stock Inventory**| `ViewStockScreen.fxml` | `ViewStockScreen.java` | `Main.java:280` -> `new ViewStockScreen().show()` | **COMPILATION FAILURE**: No `show()` method. Controller binds to non-existent `Product` fields (`author`, `publisher`, `edition`). |
| **POS Order & Billing** | `CreateOrderScreen.fxml` (0 bytes) | `CreateOrderScreen.java` | `Main.java:290` -> `new CreateOrderScreen(stock, shop).show()` | Pure Java layout. Double discount bug. No tax calculation. No cash payment/change. Hardcoded image & text file paths. |
| **View Order** | `ViewOrderScreen.fxml` (corrupted) / `viewOrder.fxml` | `ViewOrderScreen.java` | `Main.java:298` -> `new ViewOrderScreen().show()` | `ViewOrderScreen.fxml` has syntax errors at lines 1–40. `ViewOrderScreen.java` creates empty unpopulated TableView. |
| **Invoices & History** | `ViewInvoicesScreen.fxml` (0 bytes) / `ViewInvoices.fxml` | `ViewInvoicesScreen.java` | `Main.java:304` -> `new ViewInvoicesScreen().show()` | **COMPILATION FAILURE**: No `show()` method. References non-existent `Invoice` property methods and empty `NotificationUtil`. |
| **Settings & Config** | `SettingsScreen.fxml` (0 bytes) | `SettingsScreen.java` | `Main.java:258` -> `new SettingsScreen(password).openSettingsScreen(...)` | **COMPILATION FAILURE**: Calls empty `Logger.java`. Lacks multi-regional presets, currencies, tax configurations, and receipt headers. |

---

## 4. Complete POS & Bookstore Workflows (R2 Gap Analysis)

### 4.1 Product & Stock Management
- **Current Flow**:
  - `AddStockScreen` prompts for ID, Name, Purchase Price, Sale Price, Quantity.
  - `UpdateStockScreen` displays TableView, permits in-cell editing of sale price, sorting by price/ID, and opening two dialogs (Change Price, Add Stock).
- **Required POS Enhancements**:
  1. **Dual Categorization (Books vs. Stationery)**:
     - `Category`: `BOOK` or `STATIONERY`.
     - When `BOOK`: Show/require `Author`, `Publisher`, `Edition`, `ISBN`.
     - When `STATIONERY`: Show/require `Brand`, `Unit` (e.g. Piece, Box, Pack, Set).
  2. **Barcode / SKU Support**:
     - Unique string field for Barcode/SKU.
     - Auto-generation of SKU if blank (e.g. `BK-1001`, `ST-2001`).
     - Barcode input field with scanner detection (key listener detecting rapid character bursts ending in Enter).
  3. **Low-Stock Warning System**:
     - Configurable `minStockThreshold` (default: 5 units).
     - Visual badge/indicator in UI table (e.g., Warning icon + orange highlight when stock <= threshold, Red when out of stock).
     - Filter button / tab on Inventory screen: `All Items`, `Books`, `Stationery`, `Low Stock Alert (< 5)`.
  4. **Stock Adjustment Audit**:
     - Restocking should add to current quantity, not insert duplicate rows.
     - Record stock adjustments with timestamp and reason (Received Stock, Inventory Count Correction, Damaged/Returned).

### 4.2 Order & Billing POS Screen
- **Current Flow**:
  - `CreateOrderScreen` shows two tables (Catalog and Order Items). User clicks catalog item, enters quantity and discount, clicks Add, then clicks Generate Invoice, enters customer name in a modal text prompt, and writes tab-delimited text to `invoice_history.txt`.
- **Required POS Enhancements**:
  1. **High-Speed POS Layout**:
     - Split pane: Left 65% is Active Cart Table with Quick Add / Barcode scanner bar; Right 35% is Payment & Summary Panel.
     - Direct barcode scan auto-adds item to cart or increments quantity if already in cart.
     - Search bar with instant autocomplete / typeahead search by Name, SKU, or ISBN.
  2. **Cart Controls**:
     - Inline quantity stepper (`+` / `-` buttons and editable quantity cell).
     - Quick item discount (% or fixed amount) with permission check if needed.
     - Quick delete button per line item.
     - "Clear Cart" button with confirmation.
  3. **Calculation & Billing Engine**:
     - **Subtotal**: Sum of (Item Unit Price * Quantity).
     - **Discount Total**: Sum of line discounts + overall invoice discount.
     - **Taxable Amount**: Subtotal - Discount.
     - **Tax / VAT Amount**: Taxable Amount * (Regional Tax Rate %).
     - **Grand Total**: Taxable Amount + Tax / VAT Amount.
     - **Cash Payment Computation**:
       - `Amount Tendered / Cash Received` input field (with quick denomination buttons: e.g. Exact, +$5, +$10, +$20, +$50, +$100).
       - Real-time `Change Due` display (`Amount Tendered - Grand Total`).
       - Validation: Cannot complete cash order if `Amount Tendered < Grand Total`.
  4. **Customer Information**:
     - Walk-in Customer (default) or Registered Customer input (Name, Phone/Mobile, Email, Tax ID / TRN).

### 4.3 Invoicing & History
- **Current Flow**:
  - Writes unstructured text to `invoice_history.txt` and raw CSV to `invoice.csv`.
  - `ViewInvoicesScreen` contains uncompilable table and basic printer job.
- **Required POS Enhancements**:
  1. **Invoice History Table**:
     - Invoice #, Date/Time, Customer Name, Item Count, Subtotal, Tax Amount, Grand Total, Payment Method (Cash, Card, Other), Actions.
     - Filtering controls:
       - Date Range Picker (`From Date` - `To Date`).
       - Search by Invoice Number or Customer Name.
       - Filter by Payment Method or Status.
  2. **Invoice Actions**:
     - **View Details**: Full breakdown of line items, tax, discounts, customer information.
     - **Re-Print Invoice**: Preview and print to physical printer or Windows default printer.
     - **Export to PDF**: Generate clean, professional PDF invoice using OpenPDF / PDFBox.
     - **Export to CSV**: Export filtered invoice list or line-item sales report to CSV.

### 4.4 Settings & Store Configuration (Multi-Regional Support)
- **Current Flow**:
  - Only stores shop name, address, and mobile in `shop_data.txt`.
- **Required POS Enhancements**:
  1. **Regional Presets**:
     - **USA Preset**:
       - Currency: USD (`$`).
       - Tax Name: `Sales Tax`.
       - Default Tax Rate: `7.0%` (user adjustable `0.0%` to `15.0%`).
       - Receipt Title: `Sales Receipt`.
       - Date Format: `MM/dd/yyyy`.
     - **UK Preset**:
       - Currency: GBP (`£`).
       - Tax Name: `VAT`.
       - Default Tax Rate: `20.0%` (standard), supports 5% reduced and 0% zero-rated.
       - Required Field: `VAT Registration Number` (e.g. `GB 123 4567 89`).
       - Receipt Title: `Tax Invoice`.
       - Date Format: `dd/MM/yyyy`.
     - **Middle East / Gulf Preset (GCC)**:
       - Selectable Country: UAE, Saudi Arabia (KSA), Qatar, Kuwait, Bahrain, Oman.
       - Currencies: AED (`AED` / `د.إ`), SAR (`SAR` / `ر.س`), QAR (`QAR` / `ر.ق`), KWD, BHD, OMR.
       - Tax Rates: KSA (15%), UAE (5%), Bahrain (10%), Oman (5%), Qatar (0%).
       - Required Field: `Tax Registration Number (TRN) / الرقم الضريبي`.
       - Bilingual Arabic/English Receipt Template:
         - Dual-language headers: "Tax Invoice / فاتورة ضريبية", "Date / التاريخ", "Total / المجموع", "Tax / الضريبة".
         - RTL layout support and UTF-8 Arabic font rendering.
         - ZATCA Phase 1 QR code generation ready (Seller Name, TRN, Timestamp, Total, VAT Total in TLV Base64).
  2. **Store Customization**:
     - Store Name, Branch/Store Number, Address, Phone, Email, Website.
     - Receipt Header Message (e.g. "Thank you for shopping at GM Bookstore!").
     - Receipt Footer / Policy (e.g. "Returns accepted within 7 days with original receipt.").
     - Receipt Page Format Toggle: Standard A4/Letter vs 80mm Thermal Receipt.
  3. **Security & Administration**:
     - Admin PIN / Password with salt & hash (`PasswordUtil.java`).
     - Password required for: Settings Screen, Deleting Stock, Voiding Invoices.

---

## 5. Modern Windows 11 Fluent UI & Styling Integration

### 5.1 AtlantaFX Theme Architecture
- **Dependency**: `io.github.mkpaz:atlantafx-base:2.0.1` (to be added to `pom.xml`).
- **Initialization**:
  In `Main.java` before showing the primary stage:
  ```java
  Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
  // Optional dark mode toggle:
  // Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
  ```
  `PrimerLight` or `CupertinoLight` immediately skin all JavaFX components with Windows 11 Fluent-like design tokens (clean rounded borders, micro-interactions, modern focus rings, native drop-shadows).

### 5.2 Custom Stylesheet (`app-fluent.css`) Design Specifications
A clean, centralized stylesheet `src/main/resources/com/example/t/app-fluent.css` should replace the three empty CSS files. It provides:
1. **Windows 11 Typography**:
   ```css
   .root {
       -fx-font-family: "Segoe UI Variable Text", "Segoe UI", -apple-system, system-ui, sans-serif;
       -fx-font-size: 13px;
       -color-primary: #0067c0;
       -color-primary-hover: #0f6cbd;
   }
   ```
2. **Card & Surface System**:
   ```css
   .card {
       -fx-background-color: #ffffff;
       -fx-background-radius: 8px;
       -fx-border-color: #e5e5e5;
       -fx-border-radius: 8px;
       -fx-border-width: 1px;
       -fx-padding: 16px;
       -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.04), 8, 0, 0, 2);
   }
   ```
3. **Sidebar Navigation**:
   ```css
   .sidebar {
       -fx-background-color: #f3f3f3;
       -fx-border-color: transparent #e5e5e5 transparent transparent;
       -fx-border-width: 1px;
   }
   .nav-button {
       -fx-alignment: CENTER_LEFT;
       -fx-background-color: transparent;
       -fx-background-radius: 6px;
       -fx-padding: 10px 16px;
       -fx-font-size: 14px;
   }
   .nav-button:hover {
       -fx-background-color: #eaeaea;
   }
   .nav-button.active {
       -fx-background-color: #e0e8f2;
       -fx-text-fill: #0067c0;
       -fx-font-weight: bold;
   }
   ```
4. **POS KPI Tiles & Status Badges**:
   ```css
   .kpi-tile {
       -fx-background-color: white;
       -fx-border-color: #e0e0e0;
       -fx-border-radius: 8px;
       -fx-background-radius: 8px;
       -fx-padding: 12px 16px;
   }
   .badge-low-stock {
       -fx-background-color: #fff4ce;
       -fx-text-fill: #795e00;
       -fx-background-radius: 12px;
       -fx-padding: 2px 8px;
       -fx-font-size: 11px;
       -fx-font-weight: bold;
   }
   .badge-in-stock {
       -fx-background-color: #dff6dd;
       -fx-text-fill: #107c10;
       -fx-background-radius: 12px;
       -fx-padding: 2px 8px;
       -fx-font-size: 11px;
   }
   ```
5. **Responsive Layout Guidelines**:
   - Window minimum dimensions: `minWidth = 1080`, `minHeight = 700`.
   - Default startup window: `1280 x 800` (fits standard 1366x768 and 1920x1080 screens perfectly).
   - Use `Priority.ALWAYS` and `HBox.hgrow` / `VBox.vgrow` instead of static manual coordinate math (`Screen.getPrimary().getBounds().getWidth() - 200`).

---

## 6. Recommended UI Architecture & Navigation Model

### 6.1 Unified Single-Stage Master Shell
Instead of launching detached floating `Stage` instances, adopt a **Master-Detail Application Shell**:

```
+-----------------------------------------------------------------------------------------------+
| Top Header Bar: Store Logo | Store Name | Active Preset (e.g. 🇺🇸 USD / 7% Tax) | Admin / Lock |
+-----------------------------------------------------------------------------------------------+
| Navigation Sidebar   | Main Content Viewport (Dynamic StackPane / FXML Loader)                |
|                      |                                                                        |
| [1] Point of Sale    |  Active Screen Loaded Dynamically:                                     |
| [2] Stock Inventory  |  - PosBillingView.fxml (Active Cart, Scanner, Payment)                 |
| [3] Invoices History |  - StockInventoryView.fxml (Books/Stationery Tabs, Low Stock Alert)    |
| [4] Store Settings   |  - InvoiceHistoryView.fxml (Filters, Re-print, PDF Export)             |
|                      |  - SettingsView.fxml (Multi-regional Presets, Store Details, Security) |
|                      |                                                                        |
| [5] Logout / Lock    |                                                                        |
+----------------------+------------------------------------------------------------------------+
| Bottom Status Bar: Status Messages | Real-time Clock | Database Connection Status: SQLite OK  |
+-----------------------------------------------------------------------------------------------+
```

### 6.2 Proposed Clean MVC Class Structure

```
com.example.t/
├── Main.java                          (Application entry point, initializes AtlantaFX & Shell)
├── RunFX.java                         (Launcher class for packaging)
├── module-info.java                   (Modular declarations with JavaFX, AtlantaFX, SQLite)
│
├── controller/
│   ├── MainShellController.java       (Coordinates sidebar navigation & view switching)
│   ├── PosBillingController.java      (POS cart, scanning, discounts, tax, change calculator)
│   ├── StockInventoryController.java  (Product list, category filter, low-stock alerts, CRUD)
│   ├── ProductDialogController.java   (Add / Edit product modal dialog)
│   ├── InvoiceHistoryController.java  (Search, date filters, view invoice, reprint, PDF/CSV)
│   ├── SettingsController.java        (Multi-regional presets, store attributes, password)
│   └── LoginModalController.java      (Admin authentication modal)
│
├── model/
│   ├── Product.java                   (Book vs Stationery, barcode, author, minStock, properties)
│   ├── ProductCategory.java           (Enum: BOOK, STATIONERY)
│   ├── OrderItem.java                 (Line item with unitPrice, quantity, discount, lineTotal)
│   ├── Invoice.java                   (Invoice metadata, totals, tax, customer, paymentMethod)
│   ├── RegionPreset.java              (Enum/Model: USA, UK, UAE, SAUDI_ARABIA, QATAR, etc.)
│   └── StoreSettings.java             (Store profile, active region, tax rates, custom headers)
│
├── service/
│   ├── DatabaseManager.java           (SQLite connection pool & schema migration)
│   ├── ProductRepository.java         (SQLite DAO for products & inventory adjustments)
│   ├── InvoiceRepository.java         (SQLite DAO for orders, invoices, and line items)
│   ├── BillingCalculator.java         (Pure calculation logic for subtotal, discount, tax, change)
│   ├── PdfInvoiceGenerator.java       (OpenPDF / PDFBox generator with regional & bilingual layout)
│   └── CsvExportService.java          (CSV export utility)
│
└── util/
    ├── NotificationUtil.java          (Modern Fluent alerts, toasts, and dialogs)
    ├── PasswordUtil.java              (Salted PBKDF2 / SHA-256 hashing)
    └── Logger.java                    (Clean System.Logger wrapper)
```

### 6.3 File Cleanup Plan
To resolve all compiler errors and ambiguity:
1. **Remove Broken / Corrupted / Redundant Files**:
   - Delete `ViewOrderScreen.fxml` (corrupted XML).
   - Delete duplicate legacy FXMLs: `AddStock.fxml`, `ViewStock.fxml`, `viewOrder.fxml`, `ViewInvoices.fxml`, `Menu.fxml`, `menu_screen.fxml`, `PasswordScreen.fxml`.
   - Delete empty 0-byte CSS files (`modern-theme.css`, `modern-theme-new.css`, `modern-windows-theme.css`).
2. **Implement Canonical Modern FXMLs**:
   - `MainShell.fxml`: Unified application frame with sidebar and content container.
   - `PosBillingView.fxml`: Complete POS billing interface.
   - `StockInventoryView.fxml`: Inventory management with Books vs Stationery categorization and low-stock filters.
   - `ProductDialog.fxml`: Modern modal for Adding/Editing stock.
   - `InvoiceHistoryView.fxml`: Invoice history, filtering, PDF export, re-print.
   - `SettingsView.fxml`: Multi-regional presets, tax rates, store branding.
   - `LoginModal.fxml`: Clean administrative lock screen.
3. **Implement Canonical Modern CSS**:
   - `app-fluent.css`: Integrated with AtlantaFX `PrimerLight` / `CupertinoLight`.

---

## 7. Caveats
1. **Network Independence**: All styling must function 100% offline without external CDN dependencies or web fonts. System fonts (`Segoe UI`) and embedded assets must be used.
2. **Arabic Bidi Rendering**: OpenPDF / PDFBox requires standard UTF-8 Arabic font files (e.g. bundled Noto Sans Arabic or Windows system fonts) to properly render Arabic letters in cursive linked form on bilingual invoices.
3. **Database Dependency**: The POS, Inventory, and Invoice screens require the SQLite DAO layer being investigated by `explorer_survey_3`. The UI controllers must interface cleanly with the repository contracts.

---

## 8. Conclusion
The current project has severe architectural fragmentation: half the screens are written in procedural Java spawning multiple disconnected stages, while the other half are incomplete or broken FXML controllers with empty or corrupted files, resulting in 9 immediate compilation failures. Furthermore, critical POS workflows are missing (categorization, barcode input, low-stock warnings, cash change calculation, multi-regional tax/currency presets, and PDF generation), and a double-discount bug causes calculation errors.

Adopting AtlantaFX with a single-stage Master Navigation Shell, completing the clean FXML MVC architecture, fixing the billing calculations, and implementing the multi-regional settings for USA, UK, and Middle East/Gulf countries will deliver a polished, modern, native Windows 11 desktop experience that fully satisfies requirement R2.

---

## 9. Verification Method

### 9.1 Verification Steps
1. **Source Inspection**: Inspect `ViewInvoicesScreen.java:62,69,111`, `SettingsScreen.java:51`, `Main.java:280,304`, and `CreateOrderScreen.java:181-184` to verify the exact compilation errors and double-discount bug reported.
2. **Build Verification**: Run `.\mvnw.cmd test-compile` from `e:\javafx-desktop-bookstore-billing` to confirm the 9 compilation failures.
3. **XML Syntax Verification**: Inspect `src/main/resources/com/example/t/ViewOrderScreen.fxml` lines 1–40 to verify the malformed XML tags.
4. **Invalidation Conditions**: If `.\mvnw.cmd test-compile` succeeds without fixing stub files or method bindings, or if `Product.java` already contained `author` and `category` fields, this survey would be invalidated. Both conditions were conclusively disproven.
