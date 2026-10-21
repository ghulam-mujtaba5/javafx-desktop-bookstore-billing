## 2026-10-20T03:33:52Z
You are worker_m4_fix in e:\javafx-desktop-bookstore-billing.
Your working directory is: e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_m4_fix
Your parent is: orchestrator_gen2 (conversation ID: 12ab0fc1-3462-46a0-a5f2-75a160f827df)

MANDATORY FIRST STEP: Read the authoritative requirements at:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\ORIGINAL_REQUEST.md
and the project architecture at:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\PROJECT.md
and reviewer_m4_2_gen2's full evidence report at:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\reviewer_m4_2_gen2\handoff.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Your task:
Fix the two defects flagged by reviewer_m4_2_gen2:
1. Fix authentication bypass and remove all hardcoded backdoor checks in SettingsController:
   - In SettingsController.java (handleUnlock and handleChangePassword), eliminate:
     matches = "admin".equals(entered) || "book shop".equals(entered);
     currentValid = "admin".equals(current) || "book shop".equals(current);
     All password verification MUST go through PasswordUtil.verifyPassword(entered, storedHash).
   - In PasswordUtil.java: enhance verifyPassword(String rawPassword, String storedHash) so that if storedHash is an unsalted legacy 64-char hex hash (e.g., does not contain ':'), it validates via constant-time comparison against sha256Hex(rawPassword), while properly validating salted 'salt:hash' format if ':' is present.
   - In StoreSettings.java: update default constructor to initialize adminPasswordHash using a genuine salted hash generated via PasswordUtil.hashPassword("admin").
   - In SchemaMigrationManager.java / V1__init_schema.sql: ensure seeded default admin password hash is properly validated.
2. Fix Arabic character dropping in PdfInvoiceService:
   - In PdfInvoiceService.java: OpenPDF Type 1 HELVETICA drops Arabic Unicode characters. Register system fonts via FontFactory.registerDirectories() and look up an Arabic-supporting font available on Windows (e.g. Arial: FontFactory.getFont("Arial", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, ...), or Tahoma / Segoe UI), falling back to HELVETICA if unavailable. Verify Arabic text renders without throwing exceptions and without being dropped into empty strings.
3. Run tests using .\mvnw.cmd test. Ensure all existing tests (370+ tests) pass cleanly.
4. Write your handoff report to: e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_m4_fix\handoff.md.
5. Send completion message to parent when done.
