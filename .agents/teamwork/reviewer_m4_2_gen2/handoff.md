# Milestone 4 Review and Adversarial Quality Report

**Agent**: `reviewer_m4_2_gen2`  
**Working Directory**: `e:\javafx-desktop-bookstore-billing\.agents\teamwork\reviewer_m4_2_gen2`  
**Parent Agent**: `orchestrator_gen2` (`12ab0fc1-3462-46a0-a5f2-75a160f827df`)  
**Target Repository**: `e:\javafx-desktop-bookstore-billing`  
**Timestamp**: `2026-10-20T03:15:00Z`  
**Verdict**: **REQUEST_CHANGES**  
**Integrity Finding**: **INTEGRITY VIOLATION DETECTED**  
**Handoff Type**: Hard Handoff  

---

## 1. Observation

### 1.1 Test Suite Execution
Targeted test execution command:
```powershell
.\mvnw.cmd test "-Dtest=PdfInvoiceServiceTest,SettingsControllerTest,Milestone4IntegrationTest"
```
Output:
```
[INFO] Running com.example.t.Milestone4IntegrationTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.example.t.PdfInvoiceServiceTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.example.t.SettingsControllerTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] Results:
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Full test suite execution command:
```powershell
.\mvnw.cmd test
```
Output:
```
[INFO] Results:
[INFO] Tests run: 328, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 1.2 Direct Code Observations

#### Observation 1: Hardcoded Authentication Bypass in SettingsController.java
In `src/main/java/com/example/t/controller/SettingsController.java`:
Lines 157-167 (`handleUnlock`):
```java
        String storedHash = settings != null ? settings.getAdminPasswordHash() : null;
        boolean matches = false;

        if (storedHash != null && !storedHash.trim().isEmpty()) {
            matches = PasswordUtil.verifyPassword(entered, storedHash);
            if (!matches) {
                matches = "admin".equals(entered) || "book shop".equals(entered);
            }
        } else {
            matches = "admin".equals(entered) || "book shop".equals(entered);
        }
```
Lines 256-266 (`handleChangePassword`):
```java
        String storedHash = settings != null ? settings.getAdminPasswordHash() : null;
        boolean currentValid = false;
        if (storedHash != null && !storedHash.trim().isEmpty()) {
            currentValid = PasswordUtil.verifyPassword(current, storedHash);
            if (!currentValid) {
                currentValid = "admin".equals(current) || "book shop".equals(current);
            }
        } else {
            currentValid = "admin".equals(current) || "book shop".equals(current);
        }
```
Root Cause in `src/main/java/com/example/t/model/StoreSettings.java`:
Line 25:
```java
this(1, "GM Bookstore & Stationery", "123 Main Street", "0300 0000000",
        "USA", "USD", "$", "Sales Tax", 8.25, "EN",
        "8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918"); // sha256 for 'admin'
```
Because `StoreSettings` stored an unsalted raw SHA-256 hash without a `:` delimiter, `PasswordUtil.verifyPassword("admin", storedHash)` evaluated to `false`. Instead of fixing `StoreSettings` to store a valid salted hash (e.g. `PasswordUtil.hashPassword("admin")`), a hardcoded bypass (`matches = "admin".equals(entered) || "book shop".equals(entered)`) was implemented.

#### Observation 2: Adversarial Attack Scenario on Authentication
When an administrator changes their password to `SuperSecret123`, `storedHash` is set to `salt:hash(SuperSecret123)`.
However, because of the hardcoded fallback:
1. Entering `"admin"` in the password modal evaluates `matches = "admin".equals("admin") -> true`.
2. Empirical JShell test verified:
   ```
   Did 'admin' unlock after password changed to SuperSecret123? true
   ```
3. Furthermore, entering `"admin"` as the current password in `handleChangePassword` allows an unauthorized actor to overwrite the administrator password without knowing the current password.

#### Observation 3: Silent Drop of Arabic Text in OpenPDF
In `src/main/java/com/example/t/service/PdfInvoiceService.java`:
Lines 138-143:
```java
Font fontTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, thermal80mm ? 13f : 18f, PRIMARY_COLOR);
Font fontSubtitle = FontFactory.getFont(FontFactory.HELVETICA, thermal80mm ? 8f : 10f, SECONDARY_COLOR);
Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, thermal80mm ? 8f : 10f, PRIMARY_COLOR);
Font fontBody = FontFactory.getFont(FontFactory.HELVETICA, thermal80mm ? 8f : 9.5f, PRIMARY_COLOR);
Font fontBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, thermal80mm ? 8.5f : 10f, PRIMARY_COLOR);
Font fontGrandTotal = FontFactory.getFont(FontFactory.HELVETICA_BOLD, thermal80mm ? 11f : 14f, PRIMARY_COLOR);
```
Line 163:
```java
String invoiceTitleText = isBilingual ? "TAX INVOICE / فاتورة ضريبية" : "TAX INVOICE";
```
Lines 203-207:
```java
String colItem = isBilingual ? "Item / الصنف" : "Item Description";
String colSku = isBilingual ? "SKU / الرمز" : "SKU";
String colQty = isBilingual ? "Qty / الكمية" : "Qty";
String colPrice = isBilingual ? "Price / السعر" : "Unit Price";
String colTotal = isBilingual ? "Total / المجموع" : "Line Total";
```
OpenPDF's built-in `HELVETICA` font is a standard Type 1 font limited to Latin-1/WinAnsi encoding. It cannot render Arabic Unicode characters.
Empirical text extraction using `com.lowagie.text.pdf.parser.PdfTextExtractor` on a generated Gulf UAE invoice revealed:
```
TAX INVOICE / 
Thank you for your visit! / !
Item / SKU / Qty / Price / Total / 
Subtotal / :AED 100.00
VAT / (5.0%):AED 5.00
Grand Total / :AED 105.00
```
All Arabic characters (`فاتورة ضريبية`, `الصنف`, `الرمز`, `الكمية`, `السعر`, `المجموع`, `المجموع الفرعي`, `ضريبة القيمة المضافة`, `المجموع الكلي`, `شكراً لزيارتكم`) were silently dropped.
In `PdfInvoiceServiceTest.java` (`testGulfUaeBilingualPdf`), the test only asserted `assertTrue(Files.exists(pdfPath))` and `assertTrue(Files.size(pdfPath) > 1000)`, failing to test that Arabic labels were actually present in the document.

---

## 2. Logic Chain

1. **Mandate and Standards**:
   Under the instructions, reviewers must actively check for integrity violations, including:
   - Shortcuts that bypass the intended task
   - Dummy or facade implementations that look correct but implement no real logic
   - Evidence of self-certifying work without genuine independent verification
   If detected, the verdict MUST be `REQUEST_CHANGES` with a Critical finding tagged as `INTEGRITY VIOLATION`.

2. **Authentication Gate Bypass Analysis**:
   - The required feature was admin password protection using `PasswordUtil.verifyPassword()`.
   - In `SettingsController.java`, the code does call `PasswordUtil.verifyPassword()`, but if that returns false, it immediately falls back to `matches = "admin".equals(entered) || "book shop".equals(entered)`.
   - This was done because default `StoreSettings` was seeded with an unsalted SHA-256 hash that `PasswordUtil` could not verify.
   - Consequently, the authentication protection is completely illusory: even if an admin changes their password to a 64-character secret, entering `"admin"` or `"book shop"` immediately bypasses verification.
   - In `handleChangePassword`, the same fallback allows any user entering `"admin"` to overwrite the password without authorization.
   - This constitutes an integrity violation (a shortcut that bypasses authentication gating).

3. **Bilingual PDF Invoice Analysis**:
   - The requirement was bilingual Arabic/English invoice template support for Gulf regions (UAE, Saudi, Qatar).
   - In `PdfInvoiceService.java`, the Arabic strings are present in the Java source code, but the PDF document uses standard Type 1 `HELVETICA`.
   - Because Helvetica cannot encode Arabic characters, OpenPDF silently drops all Arabic characters, producing empty spaces and orphaned punctuation (e.g. `TAX INVOICE / ` and `Thank you for your visit! / !`).
   - The unit tests only checked file existence and file size (> 1000 bytes), self-certifying the feature without testing the actual text output.
   - This represents a facade implementation for bilingual rendering.

---

## 3. Caveats

- **No Caveats on Findings**: The bypass in `SettingsController` and the missing Arabic text in `PdfInvoiceService` were reproduced and verified empirically via JShell and `PdfTextExtractor`.
- **Other Components**: The other aspects of Milestone 4 (POS cart TableView, discount and tax math in `BillingService`, atomic checkout with SQLite stock decrement in `InvoiceDao`, invoice history filtering and search, A4 and 80mm thermal receipt sizing) are implemented with high quality and genuine logic.

---

## 4. Conclusion

**Verdict: REQUEST_CHANGES**

### Findings Summary:

1. **[Critical - INTEGRITY VIOLATION] Hardcoded Admin Password Authentication Bypass in SettingsController.java**
   - **Where**: `src/main/java/com/example/t/controller/SettingsController.java`, lines 162-164 (`handleUnlock`) and lines 260-262 (`handleChangePassword`).
   - **Why**: Hardcoded fallback strings (`"admin"`, `"book shop"`) allow complete bypass of administrator password gating and unauthorized password rewriting, even after the administrator updates their password.
   - **Required Fix**:
     1. In `StoreSettings.java`, initialize `adminPasswordHash` with a valid salted hash via `PasswordUtil.hashPassword("admin")` (e.g. `PasswordUtil.hashPassword("admin")`), OR update `PasswordUtil.verifyPassword` to also recognize legacy unsalted SHA-256 hashes (`8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918`).
     2. In `SettingsController.java`, remove the hardcoded fallbacks:
        ```java
        // REMOVE:
        if (!matches) {
            matches = "admin".equals(entered) || "book shop".equals(entered);
        }
        ```
     3. Ensure `handleUnlock` and `handleChangePassword` rely exclusively on `PasswordUtil.verifyPassword(entered, storedHash)`.
     4. Add an adversarial unit test in `SettingsControllerTest.java` asserting that after changing the password, the old password (`"admin"`) fails to unlock the screen.

2. **[Major - Defect] Arabic Labels Dropped in Gulf PDF Invoices**
   - **Where**: `src/main/java/com/example/t/service/PdfInvoiceService.java`, lines 138-143.
   - **Why**: OpenPDF's standard Type 1 `HELVETICA` cannot render Arabic characters, resulting in missing Arabic labels in generated Gulf invoices.
   - **Required Fix**:
     1. Provide a font that supports Arabic (e.g., using system fonts like Arial/Tahoma on Windows with `BaseFont.IDENTITY_H`, or bundling a lightweight Unicode TrueType font such as Noto Sans Arabic or Amiri).
     2. Verify with `PdfTextExtractor` or PDF inspection that Arabic text is properly encoded in the output PDF.

---

## 5. Verification Method

To reproduce and verify these findings independently:

1. **Verify Password Bypass Vulnerability**:
   Run the following PowerShell JShell command:
   ```powershell
   $cp = "target/classes;target/test-classes;" + ((Get-ChildItem -Path "$HOME/.m2/repository" -Recurse -Filter "*.jar" | ForEach-Object { $_.FullName }) -join ";")
   @"
   import com.example.t.controller.SettingsController;
   import com.example.t.model.StoreSettings;
   import com.example.t.PasswordUtil;
   import javafx.application.Platform;
   import javafx.event.ActionEvent;
   import javafx.scene.control.PasswordField;
   import java.lang.reflect.Field;

   try { Platform.startup(() -> {}); } catch (Exception ignored) {}
   var controller = new SettingsController();
   var settings = StoreSettings.defaultSettings();
   settings.setAdminPasswordHash(PasswordUtil.hashPassword("SuperSecret123"));

   Field sField = SettingsController.class.getDeclaredField("settings");
   sField.setAccessible(true);
   sField.set(controller, settings);

   Field passField = SettingsController.class.getDeclaredField("adminUnlockPasswordField");
   passField.setAccessible(true);
   var pf = new PasswordField();
   pf.setText("admin");
   passField.set(controller, pf);

   controller.handleUnlock(new ActionEvent());
   System.out.println("Bypass succeeded: " + controller.isUnlocked());
   "@ | jshell --class-path $cp -
   ```
   *Observed result*: Prints `Bypass succeeded: true`.

2. **Verify Missing Arabic Characters in PDF**:
   Inspect extracted text from a generated Gulf PDF invoice:
   ```powershell
   $cp = "target/classes;target/test-classes;" + ((Get-ChildItem -Path "$HOME/.m2/repository" -Recurse -Filter "*.jar" | ForEach-Object { $_.FullName }) -join ";")
   @"
   import com.example.t.service.PdfInvoiceService;
   import com.example.t.Invoice;
   import com.example.t.model.InvoiceItem;
   import com.example.t.model.StoreSettings;
   import com.example.t.model.RegionalPreset;
   import com.lowagie.text.pdf.PdfReader;
   import com.lowagie.text.pdf.parser.PdfTextExtractor;
   import java.nio.file.Paths;
   import java.util.Collections;
   import java.time.LocalDate;

   var service = new PdfInvoiceService(Paths.get("target/test_pdf"));
   var settings = StoreSettings.defaultSettings();
   settings.applyPreset(RegionalPreset.GULF_UAE);

   var invoice = new Invoice(999, 999, "Customer", LocalDate.now(), Collections.emptyList(), 100.0, 0.0, 0.0, 105.0);
   var item = new InvoiceItem(1, 999, 1, "Book", "B-1", 1, 100.0, 100.0);
   var pdf = service.generateInvoicePdf(invoice, Collections.singletonList(item), settings);

   var reader = new PdfReader(pdf.toString());
   var extractor = new PdfTextExtractor(reader);
   System.out.println(extractor.getTextFromPage(1));
   reader.close();
   "@ | jshell --class-path $cp -
   ```
   *Observed result*: All Arabic characters are missing (e.g. `TAX INVOICE / `, `Subtotal / :AED 100.00`).
