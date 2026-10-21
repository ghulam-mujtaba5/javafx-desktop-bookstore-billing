## 2026-10-19T18:40:43Z

[Message] timestamp=2026-10-19T18:40:43Z sender=a53fb0a7-174c-42cf-8f32-366cc200af43 priority=MESSAGE_PRIORITY_HIGH
content=You are worker_m1, implementing Milestone 1 (Build, Core Dependencies & Clean Compilation to JDK 21 LTS) for e:\javafx-desktop-bookstore-billing.
Your working directory is: e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_m1
Your parent is: orchestrator_1 (conversation ID: a53fb0a7-174c-42cf-8f32-366cc200af43)

MANDATORY FIRST STEP: Read the authoritative requirements at:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\ORIGINAL_REQUEST.md
and the project architecture at:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\PROJECT.md
Also review the survey findings at:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_survey_1\handoff.md
e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_survey_2\handoff.md
e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_survey_3\handoff.md

Your exclusive write ownership:
- pom.xml
- src/main/java/module-info.java
- src/main/java/com/example/t/NotificationUtil.java (or com.example.t.util.NotificationUtil)
- src/main/java/com/example/t/Logger.java (or com.example.t.util.Logger)
- src/main/java/com/example/t/PasswordUtil.java (or com.example.t.util.PasswordUtil)
- src/main/java/com/example/t/MenuController.java (or com.example.t.controller.MenuController)
- src/main/java/com/example/t/Product.java
- src/main/java/com/example/t/Invoice.java
- Fixing all 9 compilation errors across existing classes (Main.java, ViewStockScreen.java, ViewInvoicesScreen.java, SettingsScreen.java) so ./mvnw.cmd clean test-compile compiles cleanly with ZERO errors.

Specific tasks for Milestone 1:
1. Upgrade `pom.xml`:
   - Java compiler source/target to 21
   - JavaFX version to 21.0.6 (controls, fxml)
   - ControlsFX to 11.2.1
   - Add AtlantaFX 2.0.1 (`io.github.mkpaz:atlantafx-base:2.0.1`)
   - Add SQLite JDBC 3.46.1.3 (`org.xerial:sqlite-jdbc:3.46.1.3`)
   - Add OpenPDF 1.3.40 (`com.github.librepdf:openpdf:1.3.40`)
   - Add JUnit 5 (jupiter-api, jupiter-engine: 5.10.2)
   - Add maven-surefire-plugin (3.2.5)
   - Update `javafx-maven-plugin` mainClass to `com.example.t.RunFX`
2. Update `module-info.java` to require necessary modules (`javafx.controls`, `javafx.fxml`, `org.controlsfx.controls`, `atlantafx.base`, `java.sql`) and open/export packages cleanly.
3. Fully implement incomplete stub classes:
   - `NotificationUtil.java`: Implement `showError(String title, String message)`, `showSuccess(String title, String message)`, `showWarning(String title, String message)` using JavaFX Alert or ControlsFX Notifications.
   - `Logger.java`: Robust logging utility implementing `info(String message)`, `warn(String message)`, `error(String message, Throwable t)` with timestamp and level.
   - `PasswordUtil.java`: Secure password hashing using SHA-256 with salt and verification methods (`hashPassword`, `verifyPassword`).
   - `MenuController.java`: Working controller with event handlers for menu navigation.
4. Upgrade Data Models & Bindings:
   - `Product.java`: Ensure all necessary fields and JavaFX property accessors (`productIdProperty()`, `productNameProperty()`, `quantityProperty()`, `priceProperty()`, `discountProperty()`, etc.) are present and properly exposed. Include Books vs Stationery attributes (`category`, `sku`, `author`, `publisher`, `edition`, `minStockThreshold`).
   - `Invoice.java`: Fix typo `neprice` -> `netPrice`. Add all missing JavaFX property accessors (`invoiceIdProperty()`, `customerNameProperty()`, `invoiceDateProperty()`, `orderTotalProperty()`). Implement `readInvoicesFromFile()` (returning empty list or delegates to legacy reader) so `ViewInvoicesScreen` compiles.
5. Fix Screen references:
   - Add `show()` method to `ViewStockScreen` and `ViewInvoicesScreen` (or instantiate Stage properly) so `Main.java` lines 280 and 304 compile cleanly.
   - Fix any broken package imports.
6. Verify Build:
   - Run `.\mvnw.cmd clean test-compile` and ensure it passes with BUILD SUCCESS and 0 errors!
