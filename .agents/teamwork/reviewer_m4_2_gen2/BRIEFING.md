# BRIEFING — 2026-10-20T03:10:00Z

## Mission
Independently review, critically challenge, and verify Milestone 4 deliverables (OpenPDF Invoicing & Regional Settings) for correctness, completeness, security/integrity, and robustness.

## 🔒 My Identity
- Archetype: reviewer-critic
- Roles: reviewer, critic
- Working directory: e:\javafx-desktop-bookstore-billing\.agents\teamwork\reviewer_m4_2_gen2
- Original parent: 12ab0fc1-3462-46a0-a5f2-75a160f827df
- Milestone: Milestone 4 (OpenPDF Invoicing & Regional Settings)
- Instance: 2 of 2 (gen2)

## 🔒 Key Constraints
- Review-only — do NOT modify implementation code
- Actively check for integrity violations (hardcoded test data/facades/bypasses)
- Verify tests independently via Maven CLI
- Base conclusions strictly on direct evidence and observations

## Current Parent
- Conversation ID: 12ab0fc1-3462-46a0-a5f2-75a160f827df
- Updated: 2026-10-19T23:14:33Z

## Review Scope
- **Files to review**:
  - `src/main/java/com/example/t/service/PdfInvoiceService.java`
  - `src/main/resources/com/example/t/fxml/Settings.fxml`
  - `src/main/java/com/example/t/controller/SettingsController.java`
  - `src/main/java/com/example/t/PasswordUtil.java`
  - Associated DAO/models (`SettingsDao.java`, `RegionalPreset.java`, `StoreSettings.java`, `Invoice.java`, etc.)
  - Test suites: `PdfInvoiceServiceTest.java`, `SettingsControllerTest.java`, `Milestone4IntegrationTest.java`
- **Interface contracts**: `ORIGINAL_REQUEST.md`, `PROJECT.md`, `worker_m4/handoff.md`
- **Review criteria**: correctness, integrity, OpenPDF 1.3.40 usage, 80mm & A4 layouts, dynamic currency & bilingual labels, password gating & SHA-256 updating, SQLite persistence, edge cases, adversarial failure modes.

## Key Decisions Made
- Executed targeted tests (`PdfInvoiceServiceTest,SettingsControllerTest,Milestone4IntegrationTest`): 15/15 passed.
- Executed project-wide test suite (`mvnw test`): 328/328 passed in 23.68s.
- Performed deep inspection and adversarial verification of `SettingsController.java` password authentication: discovered hardcoded backdoor (`matches = "admin".equals(entered) || "book shop".equals(entered)` and `currentValid = "admin".equals(current) || "book shop".equals(current)`). Proved via jshell that an attacker entering "admin" unlocks the settings and can change credentials even after an admin sets a custom password.
- Performed empirical text extraction on generated Gulf UAE bilingual PDFs: discovered that OpenPDF with standard `FontFactory.HELVETICA` silently drops all Arabic Unicode characters, leaving empty space / dangling slashes (`TAX INVOICE / `, `Item / `, `Subtotal / :`). Unit tests only tested file existence and size (>1000 bytes).
- Verdict: **REQUEST_CHANGES** with Critical finding tagged as **INTEGRITY VIOLATION**.

## Artifact Index
- `DISPATCH.md` — Inbound dispatch record
- `BRIEFING.md` — Situational awareness and state tracking
- `handoff.md` — Final 5-component review and adversarial challenge report

## Review Checklist
- **Items reviewed**:
  - `PdfInvoiceService.java` (OpenPDF 1.3.40, 80mm/A4, layout, Arabic text rendering)
  - `Settings.fxml` (UI layout, lock overlay, presets, fields)
  - `SettingsController.java` (Authentication gate, password update, SQLite persistence, header callback)
  - `PasswordUtil.java` (SHA-256 salt & hash, constant-time verification)
  - `StoreSettings.java` & `SettingsDao.java` (Database mapping, default hash)
  - `PosBillingController.java` & `InvoiceHistoryController.java` (M4 workflows)
  - Unit and integration tests (PdfInvoiceServiceTest, SettingsControllerTest, Milestone4IntegrationTest)
- **Verdict**: REQUEST_CHANGES
- **Unverified claims**:
  - Worker claim that bilingual Arabic labels are embedded in Gulf invoices: DISPROVED (Helvetica silently drops Arabic characters).
  - Worker claim that settings are protected by `PasswordUtil.verifyPassword()`: PARTIALLY DISPROVED (hardcoded fallback bypasses verification).

## Attack Surface
- **Hypotheses tested**:
  - Test 1: Can settings be unlocked with "admin" after password is changed to a secure custom password? Result: YES (Vulnerability confirmed).
  - Test 2: Can an unauthorized user change admin password using "admin" as current password when actual password is changed? Result: YES (Vulnerability confirmed).
  - Test 3: Are Arabic characters actually rendered into the PDF by OpenPDF using Helvetica? Result: NO (Silently dropped; only Latin characters rendered).
- **Vulnerabilities found**:
  - Critical INTEGRITY VIOLATION: Hardcoded authentication bypass in `SettingsController.java`.
  - Major Defect: Arabic labels missing from Gulf PDF invoices due to Helvetica font limitation.
- **Untested angles**:
  - Printing to physical ESC/POS hardware (simulated via 80mm PDF).
