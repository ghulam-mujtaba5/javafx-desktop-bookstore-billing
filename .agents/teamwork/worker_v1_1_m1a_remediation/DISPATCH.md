## 2026-10-20T22:32:30Z
You are Worker 1.1-A Remediation.
Your working directory is: e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_v1_1_m1a_remediation
Your parent orchestrator is conversation ID: 36ceb3be-97cf-46d3-97ed-e1b4ef0f155d

Read:
- e:\javafx-desktop-bookstore-billing\.agents\teamwork\ORIGINAL_REQUEST.md
- e:\javafx-desktop-bookstore-billing\.agents\teamwork\PROJECT.md
- Patch & Strategy Report: e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_v1_1_m1a_fix_1\handoff.md
- Patch file: e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_v1_1_m1a_fix_1\catalog_cache_fix.patch
- DAO Cross-Audit Report: e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_v1_1_m1a_fix_2\handoff.md
- Downstream Contract Report: e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_v1_1_m1a_fix_3\handoff.md
- Challenger 2 Report: e:\javafx-desktop-bookstore-billing\.agents\teamwork\challenger_v1_1_m1a_2\handoff.md

## Scope & Objective
Remediate the ISBN-10 check digit case sensitivity and regex range defect in `CatalogCacheDao`:
1. In `src/main/java/com/example/t/dao/CatalogCacheDao.java`:
   - In `save()`: `pstmt.setString(2, metadata.getIsbn10() != null ? metadata.getIsbn10().trim().toUpperCase() : null);`
   - In `findByIsbn()`:
     - `String clean = rawIsbn.replaceAll("[^0-9Xx]", "").trim().toUpperCase();`
     - Query: `SELECT * FROM catalog_cache WHERE isbn13 = ? OR isbn10 = ? COLLATE NOCASE OR REPLACE(isbn13, '-', '') = ? OR REPLACE(isbn10, '-', '') = ? COLLATE NOCASE LIMIT 1;`
     - Bind parameter 2 with `rawIsbn.trim().toUpperCase()` and parameter 4 with `clean`.
   - In `delete()`:
     - `String clean = isbn.replaceAll("[^0-9Xx]", "").trim().toUpperCase();`
     - Query: `DELETE FROM catalog_cache WHERE isbn13 = ? OR isbn10 = ? COLLATE NOCASE OR REPLACE(isbn13, '-', '') = ? OR REPLACE(isbn10, '-', '') = ? COLLATE NOCASE;`
     - Bind parameter 2 with `isbn.trim().toUpperCase()` and parameter 4 with `clean`.
2. In `src/main/resources/com/example/t/db/V2__v1_1_enhancements.sql` (line 6) and `src/main/java/com/example/t/database/SchemaMigrationManager.java` (line 199):
   - Update `isbn10` column definition to: `isbn10 TEXT COLLATE NOCASE`.
3. In `src/main/java/com/example/t/dao/ProductDao.java` (line 66) and `src/main/java/com/example/t/dao/InvoiceDao.java` (line 158):
   - Add `COLLATE NOCASE` to `WHERE p.sku = ? COLLATE NOCASE` and `WHERE invoice_number = ? COLLATE NOCASE` for robust casing tolerance.
4. Execute test verification:
   - `.\mvnw.cmd test -Dtest=DaoStressAndEdgeCaseEmpiricalTest` (must pass 10/10 with 0 failures).
   - `.\mvnw.cmd test` (full suite: all 422+ tests must pass with 0 failures, 0 errors).

## MANDATORY INTEGRITY WARNING
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Write your handoff report to:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_v1_1_m1a_remediation\handoff.md
Send a completion message back to parent (36ceb3be-97cf-46d3-97ed-e1b4ef0f155d).
