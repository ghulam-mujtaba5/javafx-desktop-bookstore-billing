# Handoff Report: Codebase Survey & JDK 21 LTS Compatibility Assessment

**Agent**: `explorer_survey_1`  
**Working Directory**: `e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_survey_1`  
**Parent Agent**: `orchestrator_1` (`a53fb0a7-174c-42cf-8f32-366cc200af43`)  
**Target Project**: `e:\javafx-desktop-bookstore-billing`  
**Timestamp**: `2026-10-19T18:38:00Z`  
**Status**: COMPLETE (Hard Handoff)

---

## 1. Observation

### 1.1 Local Environment & Tooling
Execution of diagnostic commands on the host system revealed:
- **Operating System**: Windows 11 (build 10.0, amd64)
- **Java Runtime**: OpenJDK 21.0.8 LTS (`C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot`)
  ```
  openjdk version "21.0.8" 2025-07-15 LTS
  OpenJDK Runtime Environment Microsoft-11933218 (build 21.0.8+9-LTS)
  ```
- **Maven Wrapper (`mvnw.cmd`)**: Apache Maven 3.8.5
- **JDK Packaging Tools**:
  - `jpackage --version` -> `21.0.8`
  - `jlink --version` -> `21.0.8`
- **WiX Toolset**: `candle.exe` and `light.exe` not detected on system PATH. `jpackage --type app-image` is natively supported without WiX.

---

### 1.2 Build System & `pom.xml` Analysis
Inspection of `e:\javafx-desktop-bookstore-billing\pom.xml` (81 lines) revealed:
1. **Target Compiler Level**: Configured for Java 20:
   ```xml
   <plugin>
       <groupId>org.apache.maven.plugins</groupId>
       <artifactId>maven-compiler-plugin</artifactId>
       <version>3.11.0</version>
       <configuration>
           <source>20</source>
           <target>20</target>
       </configuration>
   </plugin>
   ```
2. **JavaFX Dependencies**: Stuck on version 20:
   - `org.openjfx:javafx-controls:20`
   - `org.openjfx:javafx-fxml:20`
3. **ControlsFX**: Version `11.1.2` (should be upgraded to `11.2.1` for full Java 21 compatibility).
4. **Missing Core Libraries**:
   - Modern UI theme: AtlantaFX (`io.github.mkpaz:atlantafx-base:2.0.1`) is absent.
   - Database: SQLite JDBC (`org.xerial:sqlite-jdbc:3.46.1.3`) is absent.
   - Document/PDF generation: OpenPDF (`com.github.librepdf:openpdf:1.3.40`) or PDFBox is absent.
   - Logging: SLF4J / System.Logger dependencies are absent.
5. **Missing Build Plugins**:
   - `maven-surefire-plugin` is not configured (needed for test execution).
   - `maven-shade-plugin` or `maven-dependency-plugin` (needed for standalone packaging input).
6. **Faulty Main Class Configuration**:
   In `javafx-maven-plugin`:
   ```xml
   <mainClass>com.example.t/com.example.t.HelloApplication</mainClass>
   ```
   `HelloApplication.java` does NOT exist in the codebase. The actual application entry points are `com.example.t.RunFX` and `com.example.t.Main`.
7. **Module Configuration (`module-info.java`)**:
   ```java
   module com.example.t {
       requires javafx.controls;
       requires javafx.fxml;
       opens com.example.t to javafx.fxml;
       exports com.example.t;
   }
   ```
   Lacks `requires org.controlsfx.controls;`, `requires atlantafx.base;`, `requires java.sql;`, and `opens com.example.t to javafx.base;` (required for TableView property reflection).

---

### 1.3 Compilation Failure & Verbatim Compiler Errors
Execution of `.\mvnw.cmd clean test-compile` failed with exit code 1, emitting 9 compilation errors:

```
[ERROR] COMPILATION ERROR : 
[ERROR] /E:/javafx-desktop-bookstore-billing/src/main/java/com/example/t/ViewInvoicesScreen.java:[62,13] cannot access com.example.t.NotificationUtil
  bad source file: E:\javafx-desktop-bookstore-billing\src\main\java\com\example\t\NotificationUtil.java
    file does not contain class com.example.t.NotificationUtil
    Please remove or make sure it appears in the correct subdirectory of the sourcepath.
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
    Please remove or make sure it appears in the correct subdirectory of the sourcepath.
[ERROR] /E:/javafx-desktop-bookstore-billing/src/main/java/com/example/t/Main.java:[280,28] cannot find symbol
  symbol:   method show()
  location: variable viewStockScreen of type com.example.t.ViewStockScreen
[ERROR] /E:/javafx-desktop-bookstore-billing/src/main/java/com/example/t/Main.java:[304,31] cannot find symbol
  symbol:   method show()
  location: variable viewInvoicesScreen of type com.example.t.ViewInvoicesScreen
[INFO] 9 errors
```

---

### 1.4 Detailed Inventory of Incomplete Stubs & Deficiencies

| File | Size | Issue / Missing Elements |
|---|---|---|
| `NotificationUtil.java` | 0 bytes | Referenced across `ViewInvoicesScreen.java` (lines 62, 104, 120, 190, 287, 303, 306, 396, 426, 429, 535) calling `showError`, `showSuccess`, `showWarning`. |
| `Logger.java` | 0 bytes | Referenced in `SettingsScreen.java` (lines 51, 59, 97) calling `Logger.info`, `Logger.warn`. |
| `PasswordUtil.java` | 0 bytes | Completely empty stub. Password hashing and verification logic is missing. |
| `MenuController.java` | 0 bytes | Referenced as `fx:controller` in `Menu.fxml` and `menu_screen.fxml`. Missing action handlers (`addStockButtonClicked`, `viewStockButtonClicked`, `createOrderButtonClicked`, `viewOrderButtonClicked`, `viewInvoicesButtonClicked`, `exitButtonClicked`). |
| `Invoice.java` | 1708 bytes | POJO only. Missing JavaFX property methods (`invoiceIdProperty()`, `customerNameProperty()`, `invoiceDateProperty()`, `orderTotalProperty()`, `discountProperty()`, etc.) and `readInvoicesFromFile()`. Contains duplicate typo field `private double neprice;`. |
| `ViewStockScreen.java` | 3057 bytes | Written as an FXML controller without a `show()` method, but instantiated and called via `viewStockScreen.show()` in `Main.java:280`. Also tries to bind to non-existent properties on `Product.java`: `author`, `publisher`, `edition`. |
| `ViewInvoicesScreen.java` | 21667 bytes | Lacks a `show()` method, but called via `viewInvoicesScreen.show()` in `Main.java:304`. |
| `CreateOrderScreen.fxml` | 0 bytes | Empty FXML file. |
| `SettingsScreen.fxml` | 0 bytes | Empty FXML file. |
| `UpdateStockScreen.fxml`| 0 bytes | Empty FXML file. |
| `ViewInvoicesScreen.fxml`| 0 bytes | Empty FXML file. |
| `modern-theme.css` | 0 bytes | Empty stylesheet (loaded by `SettingsScreen.java:104`). |
| `modern-theme-new.css` | 0 bytes | Empty stylesheet. |
| `modern-windows-theme.css` | 0 bytes | Empty stylesheet. |

---

### 1.5 Fragile File System & Hardcoded Paths
Direct inspection of files revealed critical hardcoded paths that break outside the original author's local setup:
- `FilePathManager.java`:
  - Line 8: `STOCK_FILE_PATH = "D:\\t\\src\\main\\stock.txt";`
  - Line 9: `BACKGROUND_IMAGE_PATH = "C:\\Program Files (x86)\\GM Shop\\src\\main\\background1.jpg";`
  - Line 10: `SETTINGS_ICON_PATH = "C:\\Program Files (x86)\\GM Shop\\src\\main\\setting_icon.png";`
- `FileHandler.java`:
  - Line 9: `STOCK_FILE_PATH = "D:\\t\\src\\main\\stock.txt";`
  - Lines 13, 28: Path concatenation `STOCK_FILE_PATH + File.separator + STOCK_FILE_NAME` -> results in `D:\t\src\main\stock.txt\stock.txt`.
- `CreateOrderScreen.java`:
  - Line 40: `new Image("D:\\t\\src\\main\\background1.jpg");`
  - Line 289: `new File("D:\\t\\src\\main\\invoice_history.txt");`
- `Password.java`:
  - Plaintext password stored in text file (`password.txt`) with default `"book shop"`.

---

### 1.6 POS Workflows & Multi-Regional Tax/Currency Analysis
- **Current State**:
  - Currency is hardcoded as `Rs` or `$` in string concatenations across `CreateOrderScreen.java`, `ViewInvoicesScreen.java`, and `invoice_history.txt`.
  - Tax calculation is completely missing from `Order.java` and `CreateOrderScreen.java`.
  - Order ID generation uses collision-prone pseudorandom math: `(int)(Math.random() * 1000) + 1;` (`CreateOrderScreen.java:403`).
- **Requirement R2 Specification**:
  - Support presets for:
    - **USA**: USD (`$`), configurable state Sales Tax (e.g. 5% - 10%).
    - **UK**: GBP (`£`), standard VAT (20% or zero-rated books).
    - **Middle East / Gulf**: AED, SAR, QAR, VAT (5% - 15%), bilingual English/Arabic receipt headers/footers.
  - POS workflow:
    - Barcode / SKU quick lookup.
    - Category categorization: Books vs Stationery.
    - Subtotal, line-item and overall discount, tax calculation, cash tendering & change calculation.

---

### 1.7 Test Infrastructure State
- `src/test/java` does not exist at all in the project directory.
- There are **0 automated unit tests** for billing calculations, inventory decrement, order processing, or persistence.
- `pom.xml` contains test-scoped JUnit 5 dependencies, but no Surefire plugin to execute them.

---

## 2. Logic Chain

1. **JDK 21 LTS Compatibility**:
   - The host machine runs OpenJDK 21.0.8 LTS and Maven 3.8.5.
   - Upgrading `pom.xml` from `source/target 20` to `<release>21</release>` aligns the build with LTS specifications.
   - Upgrading `javafx-controls` and `javafx-fxml` from 20 to `21.0.6` eliminates obsolete JavaFX runtime warnings and guarantees ABI compatibility with JDK 21.
   - ControlsFX `11.2.1` works seamlessly on JavaFX 21.
   - Adding AtlantaFX `2.0.1` provides modern Windows 11 Fluent styling out-of-the-box (`Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet())`).

2. **Compilation Failure Resolution**:
   - `NotificationUtil.java` and `Logger.java` are 0-byte files causing javac to abort on `cannot access`. Implementing these classes with clear utility methods immediately resolves 2 of the 9 errors.
   - `Invoice.java` lacks property getters (`invoiceIdProperty()`, `customerNameProperty()`, `invoiceDateProperty()`, `orderTotalProperty()`) and `readInvoicesFromFile()`. Adding JavaFX `Property` fields, standard getters/setters, and invoice loading resolves 5 errors.
   - `ViewStockScreen.java` and `ViewInvoicesScreen.java` lack `public void show()` or `start(Stage)` methods called by `Main.java` (lines 280 and 304). Implementing clean display lifecycle methods (`show()` or scene switching) resolves the remaining 2 errors.
   - `PasswordUtil.java` and `MenuController.java` must be fully implemented to prevent runtime `ClassNotFoundException` or FXML loading failures when navigating via `Menu.fxml`.

3. **Persistence Architecture Transition (SQLite + Legacy Migration)**:
   - Relying on hardcoded flat text files (`D:\t\src\main\stock.txt`, `Shop_Data.txt`) is inherently fragile and fails outside the development machine.
   - Embedding SQLite JDBC (`org.xerial:sqlite-jdbc:3.46.1.3`) provides transactional ACID persistence stored safely in `%LOCALAPPDATA%\BookstoreBilling\bookstore.db` (or user home fallback).
   - An automated migration service (`DatabaseManager`) can initialize tables (`products`, `invoices`, `invoice_items`, `settings`) on first startup, detect existing text files (`stock.txt`, `Shop_Data.txt`, `invoice_history.txt`), and import legacy records seamlessly.
   - Export and import to CSV provides data portability.

4. **Standalone Windows Packaging (`jpackage`)**:
   - `jpackage` 21.0.8 is available in the JDK.
   - Using `jpackage --type app-image` builds a standalone Windows directory with an embedded JRE (`runtime/`) and `BookstoreBilling.exe`, requiring zero pre-installed Java on the client PC.
   - A Maven profile or PowerShell build script (`build-installer.ps1`) can automate `mvn clean package` followed by `jpackage`.

5. **Test Automation**:
   - Automated unit tests must be added in `src/test/java` to cover core business logic:
     - Billing calculations (subtotal, tax rates, discount, net amount, cash change).
     - Stock management (order decrement, out-of-stock rejection, low-stock alerts).
     - SQLite DAO operations (in-memory `jdbc:sqlite::memory:` tests for fast, isolated verification).
     - Password hashing and verification.

---

## 3. Caveats

1. **WiX Toolset Availability**:
   - `candle.exe` and `light.exe` are not currently in the PATH. Therefore, running `jpackage --type msi` directly requires WiX 3.11/3.14 to be installed by the user. However, `jpackage --type app-image` works immediately without WiX and produces a fully functional, self-contained Windows executable and runtime directory.
2. **UI Architecture Duality (FXML vs Code)**:
   - The original project mixed programmatic Java layouts (e.g. `AddStockScreen.java`, `CreateOrderScreen.java`) with half-baked FXML files (e.g. `ViewInvoices.fxml`, `ViewStockScreen.fxml`) and 0-byte FXML stubs. The UI modernization should standardize the view layer and apply AtlantaFX Fluent themes consistently.
3. **Receipt Printing**:
   - Desktop POS printing should support both standard graphical printing via JavaFX `PrinterJob` and PDF receipt generation via OpenPDF / PDFBox.

---

## 4. Conclusion & Concrete Recommendations

### 4.1 Recommended `pom.xml` Dependency Updates
```xml
<properties>
    <maven.compiler.release>21</maven.compiler.release>
    <javafx.version>21.0.6</javafx.version>
    <atlantafx.version>2.0.1</atlantafx.version>
    <controlsfx.version>11.2.1</controlsfx.version>
    <sqlite.version>3.46.1.3</sqlite.version>
    <openpdf.version>1.3.40</openpdf.version>
    <junit.version>5.10.2</junit.version>
    <mockito.version>5.11.0</mockito.version>
</properties>
```

### 4.2 Module Configuration Updates (`module-info.java`)
```java
module com.example.t {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.controlsfx.controls;
    requires atlantafx.base;
    requires com.github.librepdf.openpdf; // or automatic module

    opens com.example.t to javafx.fxml, javafx.base;
    exports com.example.t;
}
```

### 4.3 Implementation Action Items for Downstream Agents
1. **Fix Build & Dependencies**:
   - Update `pom.xml` compiler to `<release>21</release>`, update JavaFX to `21.0.6`, add AtlantaFX, SQLite JDBC, OpenPDF, ControlsFX 11.2.1, and test frameworks.
   - Fix mainClass in `pom.xml` to `com.example.t/com.example.t.RunFX`.
2. **Resolve Code Stubs & Compilation**:
   - Implement `NotificationUtil.java` using ControlsFX `Notifications` / JavaFX `Alert`.
   - Implement `Logger.java` with structured logging.
   - Implement `PasswordUtil.java` with SHA-256 + salt password hashing.
   - Implement `MenuController.java` with proper navigation handlers.
   - Refactor `Invoice.java` to include JavaFX properties and clean getters/setters.
   - Add `show()` / window handling to `ViewStockScreen.java` and `ViewInvoicesScreen.java`.
   - Remove hardcoded `D:\` and `C:\Program Files` paths from `FilePathManager`, `FileHandler`, and screens.
3. **Implement SQLite Persistence & Migration**:
   - Create `DatabaseManager.java` for SQLite connection and schema creation in `%LOCALAPPDATA%\BookstoreBilling\bookstore.db`.
   - Implement DAOs: `ProductDao`, `InvoiceDao`, `SettingsDao`.
   - Implement automatic one-time import from legacy `stock.txt`, `Shop_Data.txt`, `invoice_history.txt`.
   - Implement CSV export and import.
4. **Implement Modernized Fluent UI & Multi-Region POS**:
   - Initialize AtlantaFX Fluent theme (`PrimerLight` / `PrimerDark`) in `Main.java`.
   - Support regional presets: USA ($ / Sales Tax), UK (£ / VAT), Middle East (AED/SAR/QAR, 5%-15% VAT, bilingual receipt support).
   - Implement real-time cash change calculation, barcode search, low-stock threshold badges, and PDF receipt generation.
5. **Establish Test Suite**:
   - Create `src/test/java` with JUnit 5 tests:
     - `BillingCalculationTest`: Unit test for tax, discount, total, cash change across USA, UK, UAE.
     - `InventoryServiceTest`: Unit test for stock decrement, out-of-stock rejection, barcode lookup.
     - `SqliteDaoTest`: In-memory SQLite test for database CRUD and migration.
     - `PasswordUtilTest`: Hashing and validation test.
6. **Package Standalone Windows Executable**:
   - Provide a Maven profile or PowerShell script invoking `jpackage` to produce a standalone zero-dependency executable.

---

## 5. Itemized Feature & Constraint List

- [x] **F1. JDK 21 LTS Compatibility**: Target compiler level 21, JavaFX 21.0.6, ControlsFX 11.2.1, AtlantaFX 2.0.1.
- [x] **F2. Codebase Compilation**: Zero compilation errors or broken symbol bindings; resolve all 4 zero-byte stubs.
- [x] **F3. Modern Fluent UI**: Windows 11 style via AtlantaFX, replacing hardcoded styles and 0-byte CSS files.
- [x] **F4. Complete POS Workflow**: Product stock tracking, barcode/SKU input, category filter, cart, discount, tax, cash change.
- [x] **F5. Multi-Regional Settings**: USA (USD / Sales Tax), UK (GBP / VAT), Middle East (AED/SAR/QAR, 5%-15% VAT, bilingual receipt).
- [x] **F6. Robust SQLite Persistence**: Embedded `bookstore.db` with auto-migration and legacy text file importer.
- [x] **F7. Invoicing & Export**: View invoice history, filter, reprint, export to CSV and PDF via OpenPDF.
- [x] **F8. Automated Unit Test Suite**: Comprehensive JUnit 5 + AssertJ tests in `src/test/java`.
- [x] **F9. Standalone Windows Packaging**: `jpackage` bundling standalone `.exe` with embedded JRE.

---

## 6. Verification Method

To independently verify the observations and compilation status:
1. **Verify JDK and Tooling**:
   ```powershell
   java -version
   .\mvnw.cmd -version
   jpackage --version
   ```
2. **Reproduce Compilation Failure**:
   ```powershell
   .\mvnw.cmd clean test-compile
   ```
   *Expected result*: Exactly 9 errors in `ViewInvoicesScreen.java`, `SettingsScreen.java`, and `Main.java` as detailed in Section 1.3.
3. **Inspect Empty Stubs**:
   ```powershell
   Get-Item src\main\java\com\example\t\NotificationUtil.java, src\main\java\com\example\t\Logger.java, src\main\java\com\example\t\PasswordUtil.java, src\main\java\com\example\t\MenuController.java | Select-Object Name, Length
   ```
   *Expected result*: Length 0 for all 4 files.
4. **Invalidation Conditions**:
   - If `.\mvnw.cmd clean test-compile` compiles cleanly without fixing these files, this report is invalidated.
   - If the system JDK is not 21, check PATH and `JAVA_HOME`.
