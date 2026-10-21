# Progress — worker_m4_fix

Last visited: 2026-10-20T05:15:00Z
Status: Completed

## Tasks
- [x] Read DISPATCH.md and initialize BRIEFING.md / progress.md
- [x] Read ORIGINAL_REQUEST.md, PROJECT.md, and reviewer_m4_2_gen2/handoff.md
- [x] Inspect relevant code files:
  - SettingsController.java
  - PasswordUtil.java
  - StoreSettings.java
  - SchemaMigrationManager.java & V1__init_schema.sql
  - PdfInvoiceService.java
  - Related test files
- [x] Plan modifications
- [x] Execute changes for Defect 1 (Authentication / Password)
  - Remove all hardcoded backdoors in SettingsController.java (handleUnlock and handleChangePassword)
  - Enhance PasswordUtil.java: added sha256Hex, isHex64, and legacy 64-char unsalted hex hash verification via constant-time comparison
  - Update StoreSettings.java default constructor to initialize adminPasswordHash using PasswordUtil.hashPassword("admin")
  - Update SchemaMigrationManager.java to seed default admin password using PasswordUtil.hashPassword("admin")
  - Update LegacyDataImporter.java to migrate legacy uncustomized "book shop" to "admin"
  - Update src/main/password.txt default to "admin"
- [x] Execute changes for Defect 2 (Arabic PDF rendering)
  - In PdfInvoiceService.java: call FontFactory.registerDirectories() in static initializer
  - Added font discovery helper findArabicFontName() checking Arial, Tahoma, Segoe UI, Times New Roman
  - Added resolveFont(size, style, color) with BaseFont.IDENTITY_H and BaseFont.EMBEDDED, falling back to Helvetica
  - Applied resolveFont across all invoice fonts (title, subtitle, header, body, bold, grand total)
- [x] Add/update automated unit tests
  - Added testOldAdminPasswordFailsAfterPasswordChange (M4.SET.7) in SettingsControllerTest.java
  - Added testSha256HexAndLegacyUnsaltedHexHashValidation in PasswordUtilTest.java
  - Enhanced testGulfUaeBilingualPdf in PdfInvoiceServiceTest.java to extract text via PdfTextExtractor and assert Arabic text presence
  - Updated testAdminFallbackBehaviorAfterPasswordChange in Milestone4PdfAndRegionalEmpiricalChallengeTest.java to assert old password fails
- [x] Run build and all tests (`.\mvnw.cmd test`): 372 tests passed cleanly (0 failures, 0 errors)
- [x] Update BRIEFING.md
- [x] Write handoff.md
- [ ] Notify parent orchestrator
