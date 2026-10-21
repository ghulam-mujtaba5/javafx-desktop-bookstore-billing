## 2026-10-19T21:45:58Z
You are challenger_m4_1 for Milestone 4 in e:\javafx-desktop-bookstore-billing.
Your working directory is: e:\javafx-desktop-bookstore-billing\.agents\teamwork\challenger_m4_1
Your parent is: orchestrator_1 (conversation ID: a53fb0a7-174c-42cf-8f32-366cc200af43)

MANDATORY FIRST STEP: Read the authoritative requirements at:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\ORIGINAL_REQUEST.md
and worker_m4's handoff report at:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_m4\handoff.md

Your task:
Empirically stress-test the POS Checkout & Invoicing layer:
1. Write a dedicated empirical challenge test class or test harness in src/test/java/com/example/t/ verifying:
   - Atomic checkout rollback: Buying more quantity than available stock fails and cleanly rolls back (0 invoices created, 0 stock modified).
   - Rapid cart mutations: High-volume item additions, barcode scanner burst entries, quantity toggles, discount adjustments.
   - Cash tendered calculation boundaries: Sub-cent rounding, exact change due, insufficient cash rejection.
   - Invoice search resilience: Search with special characters, non-existent customer names, inverted date ranges.
2. Execute tests via .\mvnw.cmd test.
3. Determine verdict: APPROVE or REQUEST_CHANGES.
4. Write your report to: e:\javafx-desktop-bookstore-billing\.agents\teamwork\challenger_m4_1\handoff.md.
5. Send completion message to parent.
