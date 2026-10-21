# Milestone 4 Defect Fixes Handoff Report

**Agent**: `worker_m4_fix`  
**Working Directory**: `e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_m4_fix`  
**Parent Agent**: `orchestrator_gen2` (`12ab0fc1-3462-46a0-a5f2-75a160f827df`)  
**Target Repository**: `e:\javafx-desktop-bookstore-billing`  
**Timestamp**: `2026-10-20T05:25:00Z`  
**Handoff Type**: Hard Handoff  

---

## 1. Observation

### 1.1 Defects Flagged by Reviewer (`reviewer_m4_2_gen2`)
In `e:\javafx-desktop-bookstore-billing\.agents\teamwork\reviewer_m4_2_gen2\handoff.md`, two defects were identified:
1. **Critical Integrity Violation / Authentication Bypass**:
   In `SettingsController.java` (`handleUnlock` lines 157-167 and `handleChangePassword` lines 256-266):
   ```java
   matches = "admin".equals(entered) || "book shop".equals(entered);
   currentValid = "admin".equals(current) || "book shop".equals(current);
   ```
   Allowing unauthorized access and password overwrite even after the administrator changed their password. This originated because `StoreSettings.java` stored an unsalted raw SHA-256 hash (`8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918`), which `PasswordUtil.verifyPassword` could not match without `:` delimiter.
2. **Arabic Character Dropping in PDF Invoices**:
   In `PdfInvoiceService.java` (lines 138-143): Standard Type 1 `HELVETICA` was used, which is limited to WinAnsi/Latin-1 encoding. Arabic Unicode characters (e.g. `فاتورة ضريبية`, `الصنف`, `الكمية`, `السعر`, `المجموع`, `شكراً لزيارتكم`) were dropped into empty spaces when rendering bilingual Gulf invoices.

### 1.2 Implemented Changes
1. **`SettingsController.java`**:
   - In `handleUnlock`: Completely removed the backdoor fallback `matches = "admin".equals(entered) || "book shop".equals(entered);`. Now strictly executes:
     ```java
     String storedHash = settings != null ? settings.getAdminPasswordHash() : null;
     boolean matches = false;
     if (storedHash != null && !storedHash.trim().isEmpty()) {
         matches = PasswordUtil.verifyPassword(entered, storedHash);
     }
     ```
   - In `handleChangePassword`: Completely removed `currentValid = "admin".equals(current) || "book shop".equals(current);`. Now strictly executes:
     ```java
     String storedHash = settings != null ? settings.getAdminPasswordHash() : null;
     boolean currentValid = false;
     if (storedHash != null && !storedHash.trim().isEmpty()) {
         currentValid = PasswordUtil.verifyPassword(current, storedHash);
     }
     ```
2. **`PasswordUtil.java`**:
   - Added `public static String sha256Hex(String input)` computing raw SHA-256 hex string with null-safety.
   - Added `private static boolean isHex64(String s)` checking for 64-character hexadecimal format.
   - Enhanced `verifyPassword(String password, String storedSaltAndHash)`:
     - If `storedSaltAndHash` contains `:`, validates using `verifyPassword(password, salt, expectedHash)` (salted PBKDF/SHA-256 with constant-time `MessageDigest.isEqual`).
     - If `storedSaltAndHash` is a 64-character hex hash, calculates `sha256Hex(password)` and compares constant-time via `MessageDigest.isEqual` with case-insensitivity.
     - Otherwise falls back to constant-time comparison against legacy plain text strings during migration.
3. **`StoreSettings.java`**:
   - Updated default constructor to initialize `adminPasswordHash` using `PasswordUtil.hashPassword("admin")`, generating a genuine 16-byte random salt and SHA-256 composite `salt:hash`.
4. **`SchemaMigrationManager.java`**:
   - Updated `seedDefaultSettings(Connection connection)` to insert `PasswordUtil.hashPassword("admin")` via `PreparedStatement`.
5. **`LegacyDataImporter.java` & `src/main/password.txt`**:
   - In `LegacyDataImporter.java`: When migrating legacy `password.txt`, modernized unconfigured default placeholder `"book shop"` to `"admin"`.
   - In `src/main/password.txt`: Updated default content to `"admin"`.
6. **`PdfInvoiceService.java`**:
   - Added static initializer:
     ```java
     static {
         try {
             FontFactory.registerDirectories();
         } catch (Exception ignored) {}
     }
     ```
   - Added `findArabicFontName()` checking system fonts available on Windows (`"Arial"`, `"arial"`, `"Tahoma"`, `"tahoma"`, `"Segoe UI"`, `"segoe ui"`, `"Times New Roman"`).
   - Added `resolveFont(float size, int style, Color color)` with `BaseFont.IDENTITY_H` and `BaseFont.EMBEDDED`, catching exceptions and falling back to `FontFactory.HELVETICA` / `FontFactory.HELVETICA_BOLD`.
   - Updated invoice typography (`fontTitle`, `fontSubtitle`, `fontHeader`, `fontBody`, `fontBold`, `fontGrandTotal`) to use `resolveFont`.
7. **Automated Tests**:
   - `SettingsControllerTest.java`: Added `testOldAdminPasswordFailsAfterPasswordChange` (M4.SET.7) asserting that after changing the admin password, old password `'admin'` and legacy string `'book shop'` fail to unlock the screen (`assertFalse(controller.isUnlocked())`).
   - `PasswordUtilTest.java`: Added `testSha256HexAndLegacyUnsaltedHexHashValidation` testing raw SHA-256 hex generation and constant-time legacy hex hash verification.
   - `PdfInvoiceServiceTest.java`: Enhanced `testGulfUaeBilingualPdf` using `PdfReader` and `PdfTextExtractor` to verify Arabic labels (`فاتورة ضريبية`, `الصنف`, `شكراً لزيارتكم`) are present in generated PDFs.
   - `Milestone4PdfAndRegionalEmpiricalChallengeTest.java`: Updated `testAdminFallbackBehaviorAfterPasswordChange` asserting `assertFalse(controller.isUnlocked())` when entering old password `'admin'` after password change.

### 1.3 Test Execution Results
Execution of targeted tests:
```powershell
.\mvnw.cmd test "-Dtest=SettingsControllerTest,PasswordUtilTest,PdfInvoiceServiceTest,Milestone4PdfAndRegionalEmpiricalChallengeTest"
```
Result:
```
[INFO] Tests run: 42, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Full test suite execution:
```powershell
.\mvnw.cmd test
```
Result:
```
[INFO] Results:
[INFO] Tests run: 372, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time: 40.028 s
```

---

## 2. Logic Chain

1. **Defect 1 Resolution (Authentication Integrity)**:
   - Observation 1.1 identified that hardcoded `"admin"` / `"book shop"` fallbacks in `SettingsController` allowed unauthorized unlock and password change even after password update.
   - Observation 1.2 removed all hardcoded fallback logic from `handleUnlock` and `handleChangePassword`.
   - To ensure genuine password verification, `PasswordUtil.verifyPassword` was enhanced to support both modern `salt:hash` and legacy 64-character SHA-256 hashes using constant-time `MessageDigest.isEqual`.
   - `StoreSettings` and `SchemaMigrationManager` were updated to initialize `adminPasswordHash` with a cryptographically salted hash of `"admin"` generated via `PasswordUtil.hashPassword("admin")`.
   - Tests in `SettingsControllerTest.java` and `Milestone4PdfAndRegionalEmpiricalChallengeTest.java` confirmed that wrong passwords fail, correct passwords succeed, and old passwords cannot unlock after a password change.

2. **Defect 2 Resolution (Arabic PDF Glyphs)**:
   - Observation 1.1 identified that standard Type 1 Helvetica dropped Arabic Unicode characters in OpenPDF.
   - Observation 1.2 added system font registration (`FontFactory.registerDirectories()`) and Unicode font resolution using `BaseFont.IDENTITY_H` with Windows system fonts (Arial / Tahoma / Segoe UI), with graceful fallback to Helvetica.
   - In `PdfInvoiceServiceTest.testGulfUaeBilingualPdf`, text extraction via `PdfTextExtractor` verified that Arabic strings (`فاتورة ضريبية`, `الصنف`, `شكراً لزيارتكم`) are now genuinely preserved in the PDF output.

3. **Regression Safety**:
   - The full test suite ran 372 tests (increased from 370) with 0 failures, 0 errors, and 0 skipped tests, verifying that no existing functionality was broken.

---

## 3. Caveats

- System font lookup for Arabic relies on standard Windows font installations (Arial, Tahoma, Segoe UI) registered via `FontFactory.registerDirectories()`. On non-Windows platforms without these fonts, it safely falls back to standard Helvetica.
- No other caveats.

---

## 4. Conclusion

Both defects flagged in Milestone 4 review have been completely resolved with genuine logic, strict security, and zero hardcoded backdoors:
1. Authentication gating is genuine and exclusively handled via `PasswordUtil.verifyPassword`, supporting both salted hashes and legacy 64-char hex hashes.
2. Arabic text renders properly into PDF invoices using OpenPDF and system TrueType fonts with `BaseFont.IDENTITY_H`.
3. All 372 tests in the project pass cleanly.

---

## 5. Verification Method

To independently verify this work:

1. **Execute full test suite**:
   ```powershell
   .\mvnw.cmd test
   ```
   *Expected result*: `Tests run: 372, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.

2. **Verify Password Bypass Vulnerability is Eliminated**:
   Inspect `src/main/java/com/example/t/controller/SettingsController.java` to confirm no `"admin".equals` or `"book shop".equals` exists in `handleUnlock` or `handleChangePassword`.
   Run `SettingsControllerTest.testOldAdminPasswordFailsAfterPasswordChange` (M4.SET.7).

3. **Verify Arabic Text in PDF Invoices**:
   Run `PdfInvoiceServiceTest.testGulfUaeBilingualPdf` (M4.PDF.4).
   Confirm `PdfTextExtractor` successfully asserts the presence of `فاتورة ضريبية`, `الصنف`, and `شكراً لزيارتكم`.
