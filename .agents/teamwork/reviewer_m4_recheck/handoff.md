# Milestone 4 Re-Check Review Report

**Agent**: `reviewer_m4_recheck`  
**Working Directory**: `e:\javafx-desktop-bookstore-billing\.agents\teamwork\reviewer_m4_recheck`  
**Parent Agent**: `orchestrator_gen2` (`12ab0fc1-3462-46a0-a5f2-75a160f827df`)  
**Target Repository**: `e:\javafx-desktop-bookstore-billing`  
**Timestamp**: `2026-10-20T06:10:00Z`  
**Verdict**: **APPROVE**  
**Integrity Finding**: **NO INTEGRITY VIOLATIONS DETECTED (RESOLVED)**  
**Handoff Type**: Hard Handoff  

---

## 1. Observation

### 1.1 Direct Code Observations: SettingsController.java & PasswordUtil.java
In `src/main/java/com/example/t/controller/SettingsController.java`:
- Lines 150-164 (`handleUnlock`):
  ```java
  @FXML
  public void handleUnlock(ActionEvent event) {
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

      if (matches) {
          unlockScreen();
      } else {
  ```
  *Direct observation*: All fallback logic (`"admin".equals(entered) || "book shop".equals(entered)`) has been completely excised. Verification strictly invokes `PasswordUtil.verifyPassword(entered, storedHash)`.

- Lines 251-260 (`handleChangePassword`):
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
  *Direct observation*: All hardcoded current password fallbacks have been completely excised. Only genuine verification via `PasswordUtil.verifyPassword(current, storedHash)` is permitted.

In `src/main/java/com/example/t/PasswordUtil.java`:
- Lines 134-160 (`verifyPassword`):
  ```java
  public static boolean verifyPassword(String password, String storedSaltAndHash) {
      if (password == null || storedSaltAndHash == null) {
          return false;
      }

      if (storedSaltAndHash.contains(":")) {
          String[] parts = storedSaltAndHash.split(":", 2);
          if (parts.length == 2) {
              return verifyPassword(password, parts[0], parts[1]);
          }
      }

      // If storedSaltAndHash is an unsalted legacy 64-char hex hash
      if (isHex64(storedSaltAndHash)) {
          String computedHex = sha256Hex(password);
          return MessageDigest.isEqual(
                  computedHex.getBytes(StandardCharsets.UTF_8),
                  storedSaltAndHash.toLowerCase().getBytes(StandardCharsets.UTF_8)
          );
      }

      // Fallback for legacy plain text passwords during migration
      return MessageDigest.isEqual(
              password.getBytes(StandardCharsets.UTF_8),
              storedSaltAndHash.getBytes(StandardCharsets.UTF_8)
      );
  }
  ```
  *Direct observation*: Supports modern salted hashes (`salt:hash`), legacy 64-character SHA-256 hex hashes, and legacy plain text values, all utilizing constant-time comparison via `MessageDigest.isEqual`.

In `src/main/java/com/example/t/model/StoreSettings.java` (lines 23-27) & `src/main/java/com/example/t/database/SchemaMigrationManager.java` (lines 261-271):
- Seeded admin password hash is properly initialized using `PasswordUtil.hashPassword("admin")`, generating a genuine 16-byte random salt and SHA-256 hash composite.

### 1.2 Direct Code Observations: PdfInvoiceService.java
In `src/main/java/com/example/t/service/PdfInvoiceService.java`:
- Lines 38-42 (Static font directory registration):
  ```java
  static {
      try {
          FontFactory.registerDirectories();
      } catch (Exception ignored) {}
  }
  ```
- Lines 48-71 (Arabic font resolution with IDENTITY_H):
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
- Lines 173-178: All invoice document typography elements (`fontTitle`, `fontSubtitle`, `fontHeader`, `fontBody`, `fontBold`, `fontGrandTotal`) utilize `resolveFont(...)`.

### 1.3 Test Suite Executions

1. Targeted Maven test execution:
   ```powershell
   .\mvnw.cmd test "-Dtest=SettingsControllerTest,PdfInvoiceServiceTest,Milestone4IntegrationTest"
   ```
   Output:
   ```
   [INFO] Running com.example.t.Milestone4IntegrationTest
   [INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.328 s -- in com.example.t.Milestone4IntegrationTest
   [INFO] Running com.example.t.PdfInvoiceServiceTest
   [INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.602 s -- in com.example.t.PdfInvoiceServiceTest
   [INFO] Running com.example.t.SettingsControllerTest
   [INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.043 s -- in com.example.t.SettingsControllerTest
   [INFO] 
   [INFO] Results:
   [INFO] 
   [INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
   [INFO] BUILD SUCCESS
   ```

2. Full Maven test execution:
   ```powershell
   .\mvnw.cmd test
   ```
   Output:
   ```
   [INFO] Results:
   [INFO] 
   [INFO] Tests run: 372, Failures: 0, Errors: 0, Skipped: 0
   [INFO] ------------------------------------------------------------------------
   [INFO] BUILD SUCCESS
   [INFO] ------------------------------------------------------------------------
   [INFO] Total time:  01:00 min
   ```

3. Adversarial Single-Test Executions:
   - `SettingsControllerTest#testOldAdminPasswordFailsAfterPasswordChange`:
     `[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` -> `BUILD SUCCESS`
   - `PdfInvoiceServiceTest#testGulfUaeBilingualPdf`:
     `[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` -> `BUILD SUCCESS`

---

## 2. Logic Chain

1. **Authentication Integrity Verification**:
   - In the prior review (`reviewer_m4_2_gen2`), an integrity violation was flagged because `SettingsController` had hardcoded bypass fallbacks (`matches = "admin".equals(entered) || "book shop".equals(entered)`), allowing anyone to unlock the screen or overwrite the admin password even after a password update.
   - Observation 1.1 establishes that these bypass strings have been removed completely from `SettingsController.java`.
   - `StoreSettings` and `SchemaMigrationManager` now properly seed salted hashes via `PasswordUtil.hashPassword("admin")`, allowing `PasswordUtil.verifyPassword` to succeed on initial setup without fallbacks.
   - Observation 1.1 and 1.3 confirm that when an admin changes their password, the new password is saved as a salted hash. Subsequent attempts to unlock with the old default password (`"admin"`) or legacy string (`"book shop"`) fail (`assertFalse(controller.isUnlocked())`).
   - Therefore, Defect 1 (Authentication Bypass) has been completely resolved without residual backdoors.

2. **Arabic PDF Font and Text Preservation Verification**:
   - In the prior review, `PdfInvoiceService` used standard Type 1 `HELVETICA`, causing OpenPDF to drop all Arabic characters into empty gaps.
   - Observation 1.2 demonstrates that `FontFactory.registerDirectories()` registers host system fonts in a static block, and `resolveFont()` resolves available Arabic-supporting TrueType fonts (Arial, Tahoma, Segoe UI) with `BaseFont.IDENTITY_H` and `BaseFont.EMBEDDED`.
   - Observation 1.3 demonstrates that `PdfInvoiceServiceTest#testGulfUaeBilingualPdf` uses `com.lowagie.text.pdf.parser.PdfTextExtractor` to parse the rendered PDF, verifying the presence of `فاتورة ضريبية` (Tax Invoice), `الصنف` (Item), and `شكراً لزيارتكم` (Thank you for your visit).
   - Therefore, Defect 2 (Arabic Character Dropping) has been completely resolved.

3. **Zero Regressions & Full Test Suite Health**:
   - All 372 tests in the project pass with 0 failures, 0 errors, and 0 skipped tests.
   - Code layout conforms strictly to `PROJECT.md`.

---

## 3. Caveats

- System font lookup for Arabic relies on standard Windows font installations (Arial, Tahoma, Segoe UI) registered via `FontFactory.registerDirectories()`. On non-Windows platforms without these fonts, it safely falls back to standard Helvetica.
- No other caveats.

---

## 4. Conclusion

**Verdict: APPROVE**

Both defects identified during the Milestone 4 quality review have been thoroughly and genuinely resolved:
1. Gating in `SettingsController` is genuine with zero hardcoded bypasses. The old password cannot unlock settings after a password change.
2. Bilingual Gulf invoices render Arabic text correctly using OpenPDF with TrueType fonts and `BaseFont.IDENTITY_H`.
3. All unit and integration test suites pass with 100% success (372/372 tests).

Milestone 4 is fully verified and ready for sign-off.

---

## 5. Verification Method

To independently verify these findings:

1. **Targeted Tests**:
   ```powershell
   .\mvnw.cmd test "-Dtest=SettingsControllerTest,PdfInvoiceServiceTest,Milestone4IntegrationTest"
   ```
   *Expected outcome*: `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.

2. **Full Test Suite**:
   ```powershell
   .\mvnw.cmd test
   ```
   *Expected outcome*: `Tests run: 372, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.

3. **Source Code Inspection**:
   - Inspect `src/main/java/com/example/t/controller/SettingsController.java` to confirm absence of `"admin".equals` or `"book shop".equals`.
   - Inspect `src/main/java/com/example/t/service/PdfInvoiceService.java` to confirm `FontFactory.registerDirectories()` and `BaseFont.IDENTITY_H`.
