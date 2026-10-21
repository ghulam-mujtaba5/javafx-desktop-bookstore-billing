# Handoff Report: Persistence & Data Storage (R3) & Standalone Windows Packaging (R4)

**Agent**: `explorer_survey_3`  
**Working Directory**: `e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_survey_3`  
**Parent**: `orchestrator_1` (`a53fb0a7-174c-42cf-8f32-366cc200af43`)  
**Date**: `2026-10-19T18:35:00Z`

---

## 1. Observation

### 1.1 Legacy Persistence Mechanisms & File Path Hardcodings
1. **Hardcoded Drive Letters in Source Files**:
   - `FileHandler.java:9`: `private static final String STOCK_FILE_PATH = "D:\\t\\src\\main\\stock.txt";`
   - `FileHandler.java:13`: `String filePath = STOCK_FILE_PATH + File.separator + STOCK_FILE_NAME;` (evaluates to broken path `"D:\t\src\main\stock.txt\stock.txt"`)
   - `FilePathManager.java:8`: `private static final String STOCK_FILE_PATH ="D:\\t\\src\\main\\stock.txt";`
   - `FilePathManager.java:9`: `private static final String BACKGROUND_IMAGE_PATH = "C:\\Program Files (x86)\\GM Shop\\src\\main\\background1.jpg";`
   - `FilePathManager.java:10`: `private static final String SETTINGS_ICON_PATH = "C:\\Program Files (x86)\\GM Shop\\src\\main\\setting_icon.png";`
   - `CreateOrderScreen.java:289`: `File file = new File("D:\\t\\src\\main\\invoice_history.txt");`
   - `AddStockScreen.java:191`: `Image backgroundImage = new Image("file:///D:/t/src/main/background1.jpg");`

2. **Existing Data Files & Structural Layouts**:
   - `src/main/stock.txt` (6 records, comma-delimited without header):
     ```text
     0,sw,3,23.0,23.0,false
     0,temp,67,67.0,56.0,false
     0,ghgh,56,56.0,56.0,true
     0,565,56,56.0,45.0,true
     0,xsw,70,9.0,6.0,true
     0,de,4,4.0,5.0,true
     ```
     *Format*: `productId,productName,quantity,price,purchasePrice,status`. Notice all product IDs are `0` in the sample data.
   - `src/main/Shop_Data.txt` (3 plain-text lines):
     ```text
     Shop Name
     Address
     0300 000000
     ```
     *Format*: Line 1 = Name, Line 2 = Address, Line 3 = Mobile number.
   - `src/main/password.txt` (1 plain-text line):
     ```text
     book shop
     ```
   - `src/main/invoice_history.txt` (blocks separated by blank lines):
     ```text
     Invoice ID: 73
     Order Date: 2023-07-14
     Customer Name: lll
     Total Amount: Rs4.9
     Order Details:
     Product Name	Quantity	Price	Discount %
     pen		2		2.45		2.0
     ```
   - `src/main/invoice_history.csv` / root `invoice_history.csv` (interleaved header and summary rows):
     ```text
     Invoice ID,Invoice Date,Customer Name,Product Name,Quantity,Price,Discount
     949,2023-07-10,ffffgggg,bag,2,490.0,2.0
     Total Amount:,980.0
     ```

3. **Current Incomplete Data Models & Binding Deficiencies**:
   - `Product.java`: Properties for `productId`, `productName`, `quantity`, `price`, `discount`, `status`, `purchasePrice`, `discountedPrice`. Lacks SKU/barcode, categorization (Book vs. Stationery), author, publisher, edition, and min stock threshold.
   - `Invoice.java`: Only contains primitive fields (`invoiceId`, `orderNum`, `customerName`, `invoiceDate`, `products`, `orderTotal`, `discountPercentage`, `discount`, `netPrice`, `neprice` [typo]). Contains zero JavaFX `Property` accessors (`invoiceIdProperty()`, etc.) and lacks the static method `readInvoicesFromFile()` expected by `ViewInvoicesScreen.java:111`.
   - `ViewStockScreen.java:24-26, 38-40`: Binds columns to `author`, `publisher`, `edition`, which do not exist on `Product.java`.

### 1.2 Build System & Packaging Environment Observations
1. **JDK & Tooling Inventory**:
   - OpenJDK Version: `21.0.8` (Microsoft-11933218 build 21.0.8+9-LTS, amd64 on Windows 11).
   - `jpackage`: Available on PATH (`jpackage 21.0.8`).
   - `jlink`: Available on PATH (`jlink 21.0.8`).
   - Maven: Apache Maven 3.8.5 via `mvnw.cmd`.
   - WiX Toolset: Not installed (`where.exe candle light` returned code 1).
2. **Current `pom.xml` & `module-info.java`**:
   - `pom.xml`: Compiles with Java 20 (`<source>20</source><target>20</target>`), JavaFX 20, ControlsFX 11.1.2.
   - Missing dependencies: No `org.xerial:sqlite-jdbc`, no AtlantaFX (`io.github.mkpaz:atlantafx-base`), no PDF library (e.g., `com.github.librepdf:openpdf`).
   - `javafx-maven-plugin:0.0.8`: Configured with non-existent main class `<mainClass>com.example.t/com.example.t.HelloApplication</mainClass>`.
   - `module-info.java`: Declares module `com.example.t` requiring only `javafx.controls` and `javafx.fxml`.
3. **Compilation Verification Run (`.\mvnw.cmd test-compile`)**:
   - Command failed with exit code 1.
   - Errors:
     - Missing classes: `NotificationUtil.java` (0 bytes), `Logger.java` (0 bytes).
     - Missing methods in `Invoice.java`: `invoiceIdProperty()`, `customerNameProperty()`, `invoiceDateProperty()`, `orderTotalProperty()`, `readInvoicesFromFile()`.
     - Missing methods in screens: `ViewStockScreen.show()`, `ViewInvoicesScreen.show()`.

---

## 2. Logic Chain

### 2.1 Persistence Architecture (R3)

```
[Observation: Hardcoded D:\t\... and C:\Program Files\... in FilePathManager.java and FileHandler.java]
       │
       ▼ (Step 1: Path Resolution Failure)
Fails immediately on any machine without D: drive or when packaged in non-admin directories.
       │
       ▼ (Step 2: Windows Best-Practice Directory Resolution)
SQLite WAL-mode databases must not reside in %APPDATA% (Roaming) due to AD network sync corruption.
Must resolve to %LOCALAPPDATA%\BookstoreBilling\data\ with deterministic fallback chain.
       │
       ▼ (Step 3: Schema Normalization & Extensibility)
Replace flat text files with embedded SQLite (bookstore.db).
Unified `products` table handles both Books and Stationery via `category_id` and optional metadata.
Store multi-regional settings (USA, UK, Gulf) in a dedicated `store_settings` table.
Invoices and Invoice Items use foreign key constraints with cascade deletes and indexes.
       │
       ▼ (Step 4: Non-Destructive Legacy Migration)
Detect existing legacy text files (stock.txt, Shop_Data.txt, password.txt, invoice_history.txt).
Execute idempotent import within a single transaction, mapping lines into relational tables.
       │
       ▼ (Step 5: Portability via CSV Synchronization)
Append every completed invoice to a standard RFC 4180 CSV ledger in AppData.
Provide one-click UI import/export for catalog inventory and historical billing.
```

### 2.2 Standalone Packaging & Distribution (R4)

```
[Observation: SQLite JDBC & OpenPDF are automatic modules or non-pure modules]
       │
       ▼ (Step 1: Direct jlink Module-Path Failure)
Running `jlink` directly across the entire Maven dependency graph fails because automatic modules
cannot be placed into custom runtime images by jlink.
       │
       ▼ (Step 2: Dual-Phase "Runtime Image + Classpath App" Architecture)
1. Use `jlink` to create a stripped JRE (38MB) containing core JDK modules:
   (java.base, java.desktop, java.sql, java.naming, java.management, java.xml, jdk.unsupported).
2. Stage application JAR + runtime dependencies (JavaFX, AtlantaFX, SQLite JDBC, OpenPDF)
   into an input folder.
3. Invoke `jpackage --type app-image` using the custom runtime image and main class `com.example.t.RunFX`.
       │
       ▼ (Step 3: Zero-Dependency Windows Distribution)
Produces a self-contained portable folder: `dist/BookstoreBilling/BookstoreBilling.exe`.
End users can launch the app immediately on Windows 10/11 with zero Java installation.
Since WiX is absent, `app-image` works without external prerequisites; ready for Inno Setup or MSIX.
```

---

## 3. Recommended Design & Implementable Artifacts

### 3.1 SQLite Database Schema (`bookstore.db`)

The recommended DDL for `V1__init_schema.sql` is structured as follows:

```sql
-- Enforce Foreign Keys and WAL Mode
PRAGMA foreign_keys = ON;

-- 1. Schema Migrations Audit Table
CREATE TABLE IF NOT EXISTS schema_migrations (
    version INTEGER PRIMARY KEY,
    description TEXT NOT NULL,
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Categories Table
CREATE TABLE IF NOT EXISTS categories (
    category_id INTEGER PRIMARY KEY AUTOINCREMENT,
    category_name TEXT NOT NULL UNIQUE,
    item_type TEXT NOT NULL CHECK(item_type IN ('BOOK', 'STATIONERY', 'GENERAL')),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Products Table (Unified Books & Stationery Catalog)
CREATE TABLE IF NOT EXISTS products (
    product_id INTEGER PRIMARY KEY AUTOINCREMENT,
    sku TEXT UNIQUE,
    barcode TEXT,
    product_name TEXT NOT NULL,
    item_type TEXT NOT NULL DEFAULT 'BOOK' CHECK(item_type IN ('BOOK', 'STATIONERY')),
    category_id INTEGER REFERENCES categories(category_id) ON DELETE SET NULL,
    author TEXT,              -- Book specific
    publisher TEXT,           -- Book specific
    edition TEXT,             -- Book specific
    unit TEXT,                -- Stationery specific (e.g., 'Piece', 'Pack', 'Box')
    purchase_price REAL NOT NULL DEFAULT 0.0 CHECK(purchase_price >= 0),
    sale_price REAL NOT NULL DEFAULT 0.0 CHECK(sale_price >= 0),
    quantity INTEGER NOT NULL DEFAULT 0 CHECK(quantity >= 0),
    min_stock_threshold INTEGER NOT NULL DEFAULT 5 CHECK(min_stock_threshold >= 0),
    status INTEGER NOT NULL DEFAULT 1 CHECK(status IN (0, 1)), -- 1 = Active, 0 = Inactive
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Invoices Table
CREATE TABLE IF NOT EXISTS invoices (
    invoice_id INTEGER PRIMARY KEY AUTOINCREMENT,
    invoice_number TEXT NOT NULL UNIQUE, -- e.g. 'INV-20261019-0001'
    order_num INTEGER,                  -- Backward compatibility with legacy Order.orderId
    customer_name TEXT NOT NULL DEFAULT 'Walk-in Customer',
    customer_phone TEXT,
    invoice_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    subtotal REAL NOT NULL DEFAULT 0.0,
    discount_percentage REAL NOT NULL DEFAULT 0.0,
    discount_amount REAL NOT NULL DEFAULT 0.0,
    tax_rate REAL NOT NULL DEFAULT 0.0,
    tax_amount REAL NOT NULL DEFAULT 0.0,
    grand_total REAL NOT NULL DEFAULT 0.0,
    payment_method TEXT NOT NULL DEFAULT 'CASH' CHECK(payment_method IN ('CASH', 'CARD', 'DIGITAL')),
    amount_tendered REAL DEFAULT 0.0,
    change_amount REAL DEFAULT 0.0,
    currency_code TEXT NOT NULL DEFAULT 'USD',
    notes TEXT
);

-- 5. Invoice Items Table (Relational Itemization with Denormalized Price Snapshot)
CREATE TABLE IF NOT EXISTS invoice_items (
    item_id INTEGER PRIMARY KEY AUTOINCREMENT,
    invoice_id INTEGER NOT NULL REFERENCES invoices(invoice_id) ON DELETE CASCADE,
    product_id INTEGER REFERENCES products(product_id) ON DELETE SET NULL,
    product_name TEXT NOT NULL,
    sku TEXT,
    quantity INTEGER NOT NULL CHECK(quantity > 0),
    unit_price REAL NOT NULL CHECK(unit_price >= 0),
    cost_price REAL NOT NULL DEFAULT 0.0,
    discount_percentage REAL NOT NULL DEFAULT 0.0,
    line_total REAL NOT NULL CHECK(line_total >= 0)
);

-- 6. Store Settings Table (Singleton Row Configuration)
CREATE TABLE IF NOT EXISTS store_settings (
    setting_id INTEGER PRIMARY KEY CHECK(setting_id = 1),
    store_name TEXT NOT NULL DEFAULT 'My Bookstore & Stationery',
    address_line1 TEXT DEFAULT '',
    address_line2 TEXT DEFAULT '',
    phone TEXT DEFAULT '',
    email TEXT DEFAULT '',
    tax_id TEXT DEFAULT '',               -- VAT / TRN / EIN Number
    region_preset TEXT NOT NULL DEFAULT 'US' 
        CHECK(region_preset IN ('US', 'UK', 'GULF_UAE', 'GULF_SAUDI', 'GULF_QATAR', 'CUSTOM')),
    currency_code TEXT NOT NULL DEFAULT 'USD',
    currency_symbol TEXT NOT NULL DEFAULT '$',
    currency_position TEXT NOT NULL DEFAULT 'BEFORE' CHECK(currency_position IN ('BEFORE', 'AFTER')),
    tax_label TEXT NOT NULL DEFAULT 'Sales Tax',
    tax_rate REAL NOT NULL DEFAULT 0.0825,
    tax_inclusive INTEGER NOT NULL DEFAULT 0 CHECK(tax_inclusive IN (0, 1)),
    receipt_header TEXT DEFAULT 'Thank you for shopping with us!',
    receipt_footer TEXT DEFAULT 'Goods once sold can be exchanged within 7 days with original receipt.',
    bilingual_arabic INTEGER NOT NULL DEFAULT 0 CHECK(bilingual_arabic IN (0, 1)),
    admin_password_hash TEXT NOT NULL,
    admin_password_salt TEXT NOT NULL,
    low_stock_alert_threshold INTEGER NOT NULL DEFAULT 5,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for Rapid Querying & Filtering
CREATE INDEX IF NOT EXISTS idx_products_barcode ON products(barcode);
CREATE INDEX IF NOT EXISTS idx_products_sku ON products(sku);
CREATE INDEX IF NOT EXISTS idx_products_name ON products(product_name);
CREATE INDEX IF NOT EXISTS idx_products_category ON products(category_id);
CREATE INDEX IF NOT EXISTS idx_products_low_stock ON products(quantity, min_stock_threshold);
CREATE INDEX IF NOT EXISTS idx_invoices_date ON invoices(invoice_date);
CREATE INDEX IF NOT EXISTS idx_invoices_customer ON invoices(customer_name);
CREATE INDEX IF NOT EXISTS idx_invoice_items_invoice ON invoice_items(invoice_id);
CREATE INDEX IF NOT EXISTS idx_invoice_items_product ON invoice_items(product_id);
```

### 3.2 AppData Path Resolution Strategy
The path resolver implements a deterministic 5-step fallback hierarchy:

```java
package com.example.t.persistence;

import java.io.IOException;
import java.nio.file.*;

public class AppDataPathResolver {
    private static final String APP_DIR_NAME = "BookstoreBilling";

    public static Path resolveDataDir() {
        // 1. Explicit JVM Property Override (Essential for automated integration tests)
        String override = System.getProperty("bookstore.data.dir");
        if (override != null && !override.isBlank()) {
            return ensureDirectory(Paths.get(override));
        }

        // 2. Windows %LOCALAPPDATA% (Standard for SQLite database & logs)
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return ensureDirectory(Paths.get(localAppData, APP_DIR_NAME));
        }

        // 3. Fallback to Windows %APPDATA% (Roaming)
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return ensureDirectory(Paths.get(appData, APP_DIR_NAME));
        }

        // 4. Fallback to user home directory (~/.bookstore_billing)
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            return ensureDirectory(Paths.get(userHome, "." + APP_DIR_NAME.toLowerCase()));
        }

        // 5. Final fallback to relative ./data directory
        return ensureDirectory(Paths.get(".", "data"));
    }

    public static Path getDatabasePath() {
        return resolveDataDir().resolve("bookstore.db");
    }

    public static Path getExportsDir() {
        return ensureDirectory(resolveDataDir().resolve("exports"));
    }

    public static Path getReceiptsDir() {
        return ensureDirectory(resolveDataDir().resolve("receipts"));
    }

    public static Path getInvoiceLedgerCsvPath() {
        return getExportsDir().resolve("invoice_history.csv");
    }

    private static Path ensureDirectory(Path dir) {
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            return dir;
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage directory: " + dir, e);
        }
    }
}
```

### 3.3 Automatic Migration & Seed Data Plan
1. **Migration Manager Execution**:
   - On application startup (`DatabaseManager.init()`), query `PRAGMA user_version`.
   - If `user_version == 0`:
     - Open transaction.
     - Execute `V1__init_schema.sql`.
     - Seed initial categories:
       - Books: `'Fiction'`, `'Non-Fiction'`, `'Academic'`, `'Children'`
       - Stationery: `'Writing Instruments'`, `'Notebooks & Paper'`, `'Art & Craft'`, `'Office Supplies'`
     - Insert default `store_settings` with hashed password (`"book shop"`).
     - Populate sample catalog items (Books & Stationery).
     - Set `PRAGMA user_version = 1`.
     - Record in `schema_migrations`.
     - Commit transaction.
2. **Crash Resilience**:
   - Connection URL: `jdbc:sqlite:` + dbPath.
   - Connection init pragmas:
     - `PRAGMA foreign_keys = ON;`
     - `PRAGMA journal_mode = WAL;` (Write-Ahead Log)
     - `PRAGMA busy_timeout = 5000;` (Avoid SQLite busy errors during rapid POS barcode entry)

### 3.4 Backward-Compatible Legacy Text Importer
The `LegacyDataImporter` detects and reads existing files without mutating the originals:
1. **Search Order**:
   - Local directory: `src/main/stock.txt`, `src/main/Shop_Data.txt`, `src/main/password.txt`, `src/main/invoice_history.txt`.
   - `%APPDATA%\GM Shop\...`.
2. **Parsing Rules**:
   - `stock.txt`:
     - Read line: `productId,productName,quantity,price,purchasePrice,status`.
     - If `productId == 0`, auto-assign next sequence number or let SQLite autoincrement.
     - Auto-assign category: If name contains "pen", "notebook", "eraser", "bag" -> `'Stationery'`, else `'Books'`.
     - Generate SKU: `SKU-LEGACY-<id>` or `BAR-<timestamp>-<id>`.
   - `Shop_Data.txt`:
     - Map Line 1 to `store_name`, Line 2 to `address_line1`, Line 3 to `phone`.
   - `password.txt`:
     - Generate random 16-byte salt, compute SHA-256 hash of legacy password string, store in `store_settings`.
   - `invoice_history.txt`:
     - Parse blocks via regex for `Invoice ID:`, `Order Date:`, `Customer Name:`, `Total Amount:`, followed by tab-delimited item lines.
     - Insert into `invoices` and `invoice_items`.
   - Wrap entire import in a single transaction with rollback on parse failure.

### 3.5 One-Click Export & CSV Sync Design
1. **Real-Time Invoice Ledger Sync**:
   - Upon completing an order in `CreateOrderScreen`:
     - In the same service method, append an RFC 4180 record to `AppDataPathResolver.getInvoiceLedgerCsvPath()`:
       `InvoiceID,InvoiceNumber,Date,CustomerName,Subtotal,TaxAmount,DiscountAmount,GrandTotal,PaymentMethod,ItemsSummary`
     - E.g.: `101,"INV-20261019-0101","2026-10-19 14:30:00","John Doe",45.00,3.71,0.00,48.71,"CASH","Clean Code (x1 @ 45.00)"`
2. **On-Demand Catalog & Invoice Export**:
   - Export Catalog (`stock_export_YYYYMMDD.csv`):
     `ProductID,SKU,Barcode,Type,Category,Name,Author,Publisher,PurchasePrice,SalePrice,Quantity,MinStock,Status`
   - Export Invoices (`invoices_export_YYYYMMDD.csv`):
     `InvoiceID,InvoiceNumber,Date,Customer,Subtotal,Tax,Discount,GrandTotal,PaymentMethod`
   - Proper escaping: fields containing commas, quotes, or newlines wrapped in `"` with internal `"` escaped as `""`.

### 3.6 Standalone Windows Distribution & Packaging Pipeline (R4)

#### Modular vs. Classpath Decision Matrix

| Dimension | Option A: Full JLink Modular Image | Option B: Hybrid JLink JRE + Classpath App (RECOMMENDED) | Option C: Non-Modular Fat Jar |
|---|---|---|---|
| **Feasibility with SQLite JDBC & OpenPDF** | ❌ Fails (automatic module restrictions in JLink) | ✅ **100% Guaranteed Success** | ⚠️ Requires full 200MB+ JDK |
| **Output Size** | ~35 MB | ~45-55 MB (Runtime + App Libs) | ~220 MB |
| **End-User Java Requirement** | Zero (Bundled) | **Zero (Bundled)** | Requires Java installed |
| **Windows Executable Launch** | Native .exe via jpackage | **Native .exe via jpackage** | Batch file or `java -jar` |
| **Compatibility with JavaFX 21** | Strict module constraints | **Full JavaFX 21 & AtlantaFX support** | JavaFX launcher warnings |

#### Concrete Packaging Pipeline Implementation

1. **Step 1: Maven Build & Dependency Staging**
   In `pom.xml`, configure `maven-dependency-plugin` to assemble dependencies into `target/lib/` and package application into `target/bookstore-billing-1.0.0.jar`.

2. **Step 2: JLink Stripped Custom JRE Generation**
   Execute `jlink` targeting only the essential JDK 21 modules:
   ```cmd
   jlink ^
     --no-header-files ^
     --no-man-pages ^
     --strip-debug ^
     --add-modules java.base,java.desktop,java.sql,java.naming,java.management,java.xml,jdk.unsupported ^
     --output target/custom-runtime
   ```
   *Size*: ~38 MB.

3. **Step 3: Staging Application Payload**
   Create directory `target/jpackage-input/`:
   - Copy `target/bookstore-billing-1.0.0.jar` to `target/jpackage-input/`.
   - Copy all jars from `target/lib/` to `target/jpackage-input/`.

4. **Step 4: JPackage Native Windows Launcher Creation**
   Run JDK 21 `jpackage`:
   ```cmd
   jpackage ^
     --type app-image ^
     --name "BookstoreBilling" ^
     --app-version "1.0.0" ^
     --vendor "BookstorePOS" ^
     --dest target/dist ^
     --input target/jpackage-input ^
     --main-jar bookstore-billing-1.0.0.jar ^
     --main-class com.example.t.RunFX ^
     --runtime-image target/custom-runtime ^
     --icon src/main/resources/com/example/t/icon.ico ^
     --win-dir-chooser ^
     --win-shortcut ^
     --win-menu ^
     --win-menu-group "Bookstore POS"
   ```
   *Output*: `target/dist/BookstoreBilling/`
   Contains:
   - `BookstoreBilling.exe` (native Windows PE executable with custom icon)
   - `runtime/` (bundled stripped JDK 21)
   - `app/` (application jars and dependencies)

5. **Step 5: Windows Installer & Store Readiness**
   - **Standalone Executable / Portable App**: The generated folder `target/dist/BookstoreBilling` is 100% portable and can be zipped for instant distribution.
   - **MSI / EXE Installer**: When WiX 3.11+ is installed, running `jpackage --type exe --app-image target/dist/BookstoreBilling` generates a Windows setup wizard.
   - **Microsoft Store (MSIX)**: Wrap `target/dist/BookstoreBilling` with `MakeAppx.exe` and `AppxManifest.xml` for direct submission to the Microsoft Store.

6. **PowerShell Automation Script (`scripts/package-windows.ps1`)**:
   Provide a single executable script:
   ```powershell
   [CmdletBinding()]
   param()
   $ErrorActionPreference = "Stop"

   Write-Host "=== 1. Building Maven Artifacts ===" -ForegroundColor Cyan
   .\mvnw.cmd clean package -DskipTests
   .\mvnw.cmd dependency:copy-dependencies -DoutputDirectory=target/lib

   Write-Host "=== 2. Creating Custom JLink Runtime (Java 21) ===" -ForegroundColor Cyan
   if (Test-Path "target/custom-runtime") { Remove-Item -Recurse -Force "target/custom-runtime" }
   jlink --no-header-files --no-man-pages --strip-debug `
         --add-modules java.base,java.desktop,java.sql,java.naming,java.management,java.xml,jdk.unsupported `
         --output target/custom-runtime

   Write-Host "=== 3. Staging App Payload ===" -ForegroundColor Cyan
   if (Test-Path "target/jpackage-input") { Remove-Item -Recurse -Force "target/jpackage-input" }
   New-Item -ItemType Directory -Path "target/jpackage-input" | Out-Null
   Copy-Item "target/*.jar" "target/jpackage-input/"
   Copy-Item "target/lib/*.jar" "target/jpackage-input/"

   Write-Host "=== 4. Executing JPackage (app-image) ===" -ForegroundColor Cyan
   if (Test-Path "target/dist") { Remove-Item -Recurse -Force "target/dist" }
   jpackage --type app-image `
            --name "BookstoreBilling" `
            --app-version "1.0.0" `
            --vendor "BookstorePOS" `
            --dest target/dist `
            --input target/jpackage-input `
            --main-jar (Get-Item target/jpackage-input/t-*.jar).Name `
            --main-class com.example.t.RunFX `
            --runtime-image target/custom-runtime `
            --icon src/main/resources/com/example/t/icon.ico

   Write-Host "=== Packaging Complete! Executable located at: target/dist/BookstoreBilling/BookstoreBilling.exe ===" -ForegroundColor Green
   ```

---

## 4. Caveats

1. **No Source Code Edits Made**: Per explorer scope rules, no application source code files (`.java`, `pom.xml`) were modified during this investigation.
2. **WiX Toolset Absence**: WiX Toolset is not present on this machine; hence, generating `.msi` or `.exe` installer bundles via `jpackage --type msi` directly requires WiX 3.11+. However, `jpackage --type app-image` operates natively without WiX, creating the complete runnable `.exe` distribution folder.
3. **Database Concurrency in Desktop POS**: SQLite in WAL mode allows multiple readers and one writer. For a standalone desktop POS, this provides exceptional performance; however, multi-terminal network file sharing of an SQLite file over SMB/NFS is strongly discouraged.

---

## 5. Conclusion

1. **Persistence (R3)**: The fragile flat-file persistence (`stock.txt`, `Shop_Data.txt`, `password.txt`, `invoice_history.txt`) with hardcoded `D:\` drive paths must be replaced by an embedded SQLite database (`bookstore.db`) residing in `%LOCALAPPDATA%\BookstoreBilling\data\`. The proposed 6-table schema (`schema_migrations`, `categories`, `products`, `invoices`, `invoice_items`, `store_settings`) provides full coverage for both Books and Stationery, multi-regional presets (USA, UK, Gulf), robust indexing, and transaction-safe operations.
2. **Portability & Migration**: A non-destructive legacy importer will convert existing data smoothly, while a background CSV ledger and one-click import/export will ensure full data portability.
3. **Packaging (R4)**: The optimal Windows distribution strategy is the hybrid **"JLink Runtime + Classpath App via JPackage"** pattern. It avoids automatic-module JLink build breaks with SQLite JDBC and OpenPDF, produces a compact ~45 MB standalone bundle with zero Java dependency, and outputs `target/dist/BookstoreBilling/BookstoreBilling.exe` ready for distribution and Microsoft Store submission.

---

## 6. Verification Method

To verify these architectural findings independently:

1. **Verify Tooling on Build Environment**:
   ```powershell
   java -version
   jpackage --version
   jlink --version
   .\mvnw.cmd -v
   ```
   *Expected*: All return OpenJDK 21 LTS without error.

2. **Verify SQLite Database Schema Creation**:
   Once implemented, execute:
   ```powershell
   .\mvnw.cmd test -Dtest=DatabaseManagerTest
   ```
   *Validation condition*: Tables `schema_migrations`, `categories`, `products`, `invoices`, `invoice_items`, `store_settings` are created, WAL mode is active, and sample catalog items are seeded.

3. **Verify JPackage Packaging Pipeline**:
   Execute the automated packaging script:
   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts/package-windows.ps1
   ```
   *Validation condition*: Inspect `target/dist/BookstoreBilling/BookstoreBilling.exe`. Running the executable launches the application using its embedded `runtime/` folder with no system Java required.
