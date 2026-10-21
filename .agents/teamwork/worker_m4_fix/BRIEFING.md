# BRIEFING — 2026-10-20T05:16:00Z

## Mission
Fix defect 1 (authentication bypass / hardcoded backdoors in SettingsController, PasswordUtil, StoreSettings, SchemaMigrationManager) and defect 2 (Arabic character dropping in PdfInvoiceService using OpenPDF with Windows system fonts).

## 🔒 My Identity
- Archetype: worker_m4_fix
- Roles: implementer, qa, specialist
- Working directory: e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_m4_fix
- Original parent: 12ab0fc1-3462-46a0-a5f2-75a160f827df
- Milestone: M4 Defect Fixes

## 🔒 Key Constraints
- DO NOT CHEAT. All implementations must be genuine. No hardcoding or dummy facades.
- All password verification in SettingsController must go through PasswordUtil.verifyPassword(entered, storedHash).
- PasswordUtil.verifyPassword must support both salted hash (salt:hash) and legacy unsalted 64-char sha256 hex with constant-time comparison.
- StoreSettings default constructor must initialize adminPasswordHash with salted hash for "admin".
- PdfInvoiceService must register directories and look up an Arabic-supporting font on Windows (Arial / Tahoma / Segoe UI with IDENTITY_H) with fallback to HELVETICA.
- All tests must pass (370+ tests).

## Current Parent
- Conversation ID: 12ab0fc1-3462-46a0-a5f2-75a160f827df
- Updated: 2026-10-20T05:16:00Z

## Task Summary
- **What to build**: Fix authentication bypass / password hash handling and Arabic font support in PDF generation.
- **Success criteria**: All password bypasses removed, legacy + salted hashes supported, StoreSettings seeded with valid hash, Arabic text renders properly without dropping glyphs in PdfInvoiceService, all mvn tests pass.
- **Interface contracts**: e:\javafx-desktop-bookstore-billing\.agents\teamwork\PROJECT.md
- **Code layout**: e:\javafx-desktop-bookstore-billing\.agents\teamwork\PROJECT.md

## Change Tracker
- **Files modified**:
  - `src/main/java/com/example/t/controller/SettingsController.java`: Removed hardcoded backdoor checks from handleUnlock and handleChangePassword.
  - `src/main/java/com/example/t/PasswordUtil.java`: Added sha256Hex, isHex64, and legacy 64-char hex hash constant-time verification.
  - `src/main/java/com/example/t/model/StoreSettings.java`: Default constructor generates genuine salted hash via PasswordUtil.hashPassword("admin").
  - `src/main/java/com/example/t/database/SchemaMigrationManager.java`: Seeded store_settings with genuine salted hash for admin.
  - `src/main/java/com/example/t/database/LegacyDataImporter.java`: Modernized legacy unconfigured placeholder "book shop" to "admin".
  - `src/main/password.txt`: Updated default to "admin".
  - `src/main/java/com/example/t/service/PdfInvoiceService.java`: FontFactory.registerDirectories(), font discovery (Arial/Tahoma/Segoe UI) with IDENTITY_H/EMBEDDED and Helvetica fallback.
  - `src/test/java/com/example/t/SettingsControllerTest.java`: Added testOldAdminPasswordFailsAfterPasswordChange (M4.SET.7).
  - `src/test/java/com/example/t/PasswordUtilTest.java`: Added testSha256HexAndLegacyUnsaltedHexHashValidation.
  - `src/test/java/com/example/t/PdfInvoiceServiceTest.java`: Enhanced testGulfUaeBilingualPdf with PdfReader/PdfTextExtractor assertion of Arabic text.
  - `src/test/java/com/example/t/Milestone4PdfAndRegionalEmpiricalChallengeTest.java`: Updated testAdminFallbackBehaviorAfterPasswordChange assertion.
- **Build status**: BUILD SUCCESS (372 tests passing, 0 failures, 0 errors, 0 skipped)
- **Pending issues**: None

## Quality Status
- **Build/test result**: PASS (372/372 tests)
- **Lint status**: Clean
- **Tests added/modified**: 2 added, 2 updated

## Loaded Skills
None

## Key Decisions Made
- All password verification routes strictly through PasswordUtil.verifyPassword(candidate, storedHash) using MessageDigest.isEqual.
- Enhanced PasswordUtil to handle both modern salt:hash format and legacy 64-char hex format.
- OpenPDF uses registered Windows system fonts with BaseFont.IDENTITY_H for Unicode Arabic rendering, verified via PdfTextExtractor.

## Artifact Index
- DISPATCH.md — assignment instructions
- BRIEFING.md — working memory
- progress.md — liveness heartbeat
- handoff.md — final handoff report
