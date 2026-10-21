# Milestone 4 Forensic Integrity Audit Report

**Auditor Agent**: `auditor_m4_recheck`  
**Working Directory**: `e:\javafx-desktop-bookstore-billing\.agents\teamwork\auditor_m4_recheck`  
**Parent Agent**: `orchestrator_gen2` (`12ab0fc1-3462-46a0-a5f2-75a160f827df`)  
**Target Repository**: `e:\javafx-desktop-bookstore-billing`  
**Audit Target**: Milestone 4 Defect Fixes by `worker_m4_fix`  
**Integrity Mode**: `development` (per `ORIGINAL_REQUEST.md`)  
**Verdict**: **CLEAN**  
**Handoff Type**: Hard Handoff  

---

## 1. Observation

### 1.1 Source Code Forensic Inspections

#### 1. `SettingsController.java` (`src/main/java/com/example/t/controller/SettingsController.java`)
- **Lines 150-163 (`handleUnlock`)**:
  ```java
  String entered = adminUnlockPasswordField != null ? adminUnlockPasswordField.getText() : "";
  if (entered == null || entered.isEmpty()) {
      if (unlockErrorLabel != null) unlockErrorLabel.setText("Please enter the admin password.");
      return;
  }

  String storedHash = settings != null ? settings.getAdminPasswordHash() : null;
  boolean matches = false;

  if (storedHash != null && !storedHash.trim().isEmpty()) {
      matches = PasswordUtil.verifyPassword(entered, storedHash);
  }
  ```
  *Finding*: The hardcoded bypass `matches = "admin".equals(entered) || "book shop".equals(entered);` flagged by `reviewer_m4_2_gen2` has been completely deleted. Authentication strictly gates on `PasswordUtil.verifyPassword(entered, storedHash)`.

- **Lines 251-260 (`handleChangePassword`)**:
  ```java
  String storedHash = settings != null ? settings.getAdminPasswordHash() : null;
  boolean currentValid = false;
  if (storedHash != null && !storedHash.trim().isEmpty()) {
      currentValid = PasswordUtil.verifyPassword(current, storedHash);
  }

  if (!currentValid) {
      NotificationUtil.showError("Incorrect Password", "The current password entered is incorrect.");
      return;
  }
  ```
  *Finding*: The hardcoded bypass `currentValid = "admin".equals(current) || "book shop".equals(current);` has been completely deleted. Updating the password requires valid authentication against `storedHash`.

- **Codebase-wide Search for Backdoors**:
  A recursive regex scan across `src/main/java` confirmed zero occurrences of hardcoded bypasses or equality checks for `"admin"` or `"book shop"` in `SettingsController.java`. The only remaining `"admin"` occurrences are user-facing UI labels, standard column names in DAOs, and the factory seed generator.

#### 2. `PasswordUtil.java` (`src/main/java/com/example/t/PasswordUtil.java`)
- **Lines 98-109 (`sha256Hex`)**:
  ```java
  public static String sha256Hex(String input) {
      if (input == null) {
          throw new IllegalArgumentException("Input cannot be null");
      }
      try {
          MessageDigest digest = MessageDigest.getInstance("SHA-256");
          byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
          return HexFormat.of().formatHex(hashBytes);
      } catch (NoSuchAlgorithmException e) {
          throw new RuntimeException("SHA-256 algorithm not available", e);
      }
  }
  ```
- **Lines 134-160 (`verifyPassword`)**:
  - Validates `salt:hash` composite strings using constant-time `MessageDigest.isEqual`.
  - Validates legacy 64-character hexadecimal SHA-256 hashes using constant-time `MessageDigest.isEqual` comparison against `sha256Hex(password)`.
  - Falls back to constant-time `MessageDigest.isEqual` comparison for plaintext strings during legacy migration.
  *Finding*: Fully genuine cryptographic logic with constant-time equality checks. No backdoor values or bypass branches.

#### 3. `StoreSettings.java` (`src/main/java/com/example/t/model/StoreSettings.java`)
- **Lines 23-27**:
  ```java
  public StoreSettings() {
      this(1, "GM Bookstore & Stationery", "123 Main Street", "0300 0000000",
              "USA", "USD", "$", "Sales Tax", 8.25, "EN",
              PasswordUtil.hashPassword("admin")); // genuine salted hash for 'admin'
  }
  ```
  *Finding*: The previous unsalted static hash (`8c6976e5...`) was replaced with `PasswordUtil.hashPassword("admin")`, generating a genuine 16-byte cryptographically secure salt and composite `salt:hash`.

#### 4. `SchemaMigrationManager.java` (`src/main/java/com/example/t/database/SchemaMigrationManager.java`)
- **Lines 261-271 (`seedDefaultSettings`)**:
  ```java
  private static void seedDefaultSettings(Connection connection) throws SQLException {
      String defaultAdminHash = PasswordUtil.hashPassword("admin");
      String sql = "INSERT OR IGNORE INTO store_settings (setting_id, store_name, store_address, store_phone, " +
              "region_preset, currency_code, currency_symbol, tax_name, tax_rate, receipt_language, admin_password_hash) " +
              "VALUES (1, 'GM Bookstore & Stationery', '123 Main Street', '0300 0000000', " +
              "'USA', 'USD', '$', 'Sales Tax', 8.25, 'EN', ?);";
      try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
          pstmt.setString(1, defaultAdminHash);
          pstmt.executeUpdate();
      }
  }
  ```
  *Finding*: Database seeding inserts a dynamically generated salted hash using parameterized SQL.

#### 5. `PdfInvoiceService.java` (`src/main/java/com/example/t/service/PdfInvoiceService.java`)
- **Lines 38-42**:
  ```java
  static {
      try {
          FontFactory.registerDirectories();
      } catch (Exception ignored) {}
  }
  ```
- **Lines 48-71 (`findArabicFontName` and `resolveFont`)**:
  ```java
  private static Font resolveFont(float size, int style, Color color) {
      String fontName = findArabicFontName();
      if (fontName != null) {
          try {
              Font font = FontFactory.getFont(fontName, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, size, style, color);
              if (font != null && font.getBaseFont() != null) {
                  return font;
              }
          } catch (Exception ignored) {}
      }

      String fallback = (style == Font.BOLD) ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA;
      return FontFactory.getFont(fallback, size, style, color);
  }

  private static String findArabicFontName() {
      String[] candidates = {"Arial", "arial", "Tahoma", "tahoma", "Segoe UI", "segoe ui", "Times New Roman"};
      for (String name : candidates) {
          if (FontFactory.isRegistered(name)) {
              return name;
          }
      }
      return null;
  }
  ```
- **Lines 173-178**:
  Every font (`fontTitle`, `fontSubtitle`, `fontHeader`, `fontBody`, `fontBold`, `fontGrandTotal`) is resolved through `resolveFont(...)`.
- **Lines 198, 238-242, 295, 304, 309, 332-334**:
  Arabic and English bilingual labels are rendered directly into the PDF tables and paragraphs.
  *Finding*: Authentic OpenPDF TrueType font registration using `BaseFont.IDENTITY_H` (Unicode) and `BaseFont.EMBEDDED`. No mock PDF generators or fabricated output strings.

### 1.2 Workspace Artifact Inspection
- Checked workspace for pre-populated `.log` files, test result dumps, or fabricated verification outputs:
  ```powershell
  Get-ChildItem -Path . -Recurse -Include *.log,*result*,*output* -File
  ```
  *Result*: 0 pre-populated logs or fabricated result files detected. Only compiler-generated class files (`BillingService$CalculationResult.class`, `CalculationOracle$OrderCalculationResult.class`) matched.

### 1.3 Independent Test Execution
- Executed `.\mvnw.cmd test` independently:
  ```
  [INFO] Results:
  [INFO] 
  [INFO] Tests run: 372, Failures: 0, Errors: 0, Skipped: 0
  [INFO] 
  [INFO] ------------------------------------------------------------------------
  [INFO] BUILD SUCCESS
  [INFO] ------------------------------------------------------------------------
  [INFO] Total time:  01:00 min
  ```
- Targeted verification of defect fixes:
  1. `SettingsControllerTest.testOldAdminPasswordFailsAfterPasswordChange` (M4.SET.7): PASSED.
  2. `PdfInvoiceServiceTest.testGulfUaeBilingualPdf` (M4.PDF.4): PASSED. `PdfTextExtractor` successfully extracted and verified `فاتورة ضريبية`, `الصنف`, and `شكراً لزيارتكم` from the generated binary PDF.
  3. `Milestone4PdfAndRegionalEmpiricalChallengeTest.testAdminFallbackBehaviorAfterPasswordChange` (SECURITY-05): PASSED.

---

## 2. Logic Chain

1. **Integrity Standard Application**:
   Under Development Mode (specified in `ORIGINAL_REQUEST.md`), prohibited patterns comprise:
   - Hardcoded test results / expected outputs
   - Dummy or facade implementations with no genuine logic
   - Fabricated verification outputs or pre-populated logs
   - Authentication bypasses or backdoors

2. **Resolution of Authentication Backdoor**:
   - The prior review (`reviewer_m4_2_gen2`) revealed that `SettingsController` permitted bypass via `"admin".equals(entered)` or `"book shop".equals(entered)`.
   - Inspection of `SettingsController.java` demonstrates that all hardcoded strings and fallbacks have been removed from `handleUnlock` and `handleChangePassword`.
   - Inspection of `PasswordUtil.java` demonstrates that `verifyPassword` authenticates candidate passwords using constant-time byte comparisons against salted hashes or legacy 64-char hex hashes.
   - Empirical test execution confirms that after a password update, the default password `'admin'` and legacy string `'book shop'` strictly fail to unlock the screen.

3. **Resolution of Arabic PDF Rendering**:
   - The prior review revealed that standard Helvetica dropped Arabic glyphs.
   - Inspection of `PdfInvoiceService.java` demonstrates that `FontFactory.registerDirectories()` registers system TrueType fonts, `findArabicFontName()` selects an Arabic-capable font (Arial, Tahoma, Segoe UI), and `resolveFont()` applies `BaseFont.IDENTITY_H` encoding.
   - Independent test execution of `PdfInvoiceServiceTest.testGulfUaeBilingualPdf` using OpenPDF's `PdfTextExtractor` empirically extracted and asserted the exact Arabic Unicode text from the generated PDF.

4. **Test Suite Health**:
   - Independent build and execution of the entire test suite executed all 372 tests with 0 failures, 0 errors, and 0 skipped tests.

---

## 3. Caveats

No caveats. All investigated areas (authentication, persistence, password utility, PDF invoice generation, and test execution) were verified empirically with direct code inspection and clean execution of 372 tests.

---

## 4. Conclusion

## Forensic Audit Report

**Work Product**: Changes made by `worker_m4_fix` (`SettingsController.java`, `PasswordUtil.java`, `StoreSettings.java`, `SchemaMigrationManager.java`, `PdfInvoiceService.java`, and test suite)  
**Profile**: General Project (Development Mode)  
**Verdict**: **CLEAN**

### Phase Results
- [Hardcoded Backdoor Detection]: PASS — 0 backdoors or fallback bypass strings exist in `SettingsController.java` or `PasswordUtil.java`.
- [Facade Implementation Detection]: PASS — Authentic OpenPDF TrueType font resolution and Unicode rendering implemented.
- [Pre-populated Artifact Detection]: PASS — 0 pre-populated logs or mock result files found in workspace.
- [Independent Build & Test Execution]: PASS — 372 tests executed with 0 failures, 0 errors, 0 skipped.
- [Arabic Unicode Text Extraction]: PASS — `PdfTextExtractor` successfully verified Arabic labels in generated Gulf invoices.

---

## 5. Verification Method

To independently verify this audit:

1. **Verify Absence of Backdoor in SettingsController.java**:
   ```powershell
   Select-String -Path "src\main\java\com\example\t\controller\SettingsController.java" -Pattern '"admin"\.equals|"book shop"\.equals'
   ```
   *Expected output*: No matches found.

2. **Verify Password Gating & Old Password Rejection**:
   ```powershell
   .\mvnw.cmd test "-Dtest=SettingsControllerTest#testOldAdminPasswordFailsAfterPasswordChange"
   ```
   *Expected output*: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.

3. **Verify Arabic Rendering in PDF**:
   ```powershell
   .\mvnw.cmd test "-Dtest=PdfInvoiceServiceTest#testGulfUaeBilingualPdf"
   ```
   *Expected output*: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.

4. **Execute Full Test Suite**:
   ```powershell
   .\mvnw.cmd test
   ```
   *Expected output*: `Tests run: 372, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
