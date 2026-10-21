## 2026-10-19T19:27:38Z
You are challenger_m2_1 for Milestone 2 in e:\javafx-desktop-bookstore-billing.
Your working directory is: e:\javafx-desktop-bookstore-billing\.agents\teamwork\challenger_m2_1
Your parent is: orchestrator_1 (conversation ID: a53fb0a7-174c-42cf-8f32-366cc200af43)

MANDATORY FIRST STEP: Read the authoritative requirements at:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\ORIGINAL_REQUEST.md
and worker_m2's handoff report at:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_m2\handoff.md

Your task:
Empirically stress-test the persistence and transaction layer:
1. Write a dedicated adversarial/stress test or test harness in src/test/java/com/example/t/ verifying:
   - Transaction atomicity: Verify that an order attempting to buy more units than in stock fails and rolls back completely (0 orphaned invoice rows, 0 altered stock quantities).
   - Foreign key cascade: Deleting an invoice properly removes its line items, but deleting a category with products is rejected or handled per constraint.
   - WAL mode concurrent readers: Verify multiple connections reading while a writer is active without database is locked errors.
   - Singleton constraint on store_settings: Verify attempts to insert a second row fail.
2. Execute tests via ./mvnw.cmd test.
3. Determine verdict: APPROVE or REQUEST_CHANGES.
4. Write your report to: e:\javafx-desktop-bookstore-billing\.agents\teamwork\challenger_m2_1\handoff.md.
5. Send completion message to parent.
