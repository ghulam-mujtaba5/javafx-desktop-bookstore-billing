# BRIEFING — 2026-10-20T22:42:00Z

## Mission
Remediate the ISBN-10 check digit case sensitivity and regex range defect in CatalogCacheDao, update V2 schema and SchemaMigrationManager to use COLLATE NOCASE for isbn10, and add COLLATE NOCASE to ProductDao SKU lookup and InvoiceDao invoice_number lookup.

## 🔒 My Identity
- Archetype: worker
- Roles: implementer, qa
- Working directory: e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_v1_1_m1a_remediation
- Original parent: 36ceb3be-97cf-46d3-97ed-e1b4ef0f155d
- Milestone: 1.1-A Remediation

## 🔒 Key Constraints
- Follow minimal change principle.
- No dummy/facade implementations or hardcoded values.
- Maintain real state and real behavior.
- Clean regex: `[^0-9Xx]`.
- Normalize uppercase for ISBN-10 in save, findByIsbn, delete.
- Add COLLATE NOCASE in SQL queries for isbn10, SKU, and invoice_number.
- Verify with `DaoStressAndEdgeCaseEmpiricalTest` and full `./mvnw.cmd test`.

## Current Parent
- Conversation ID: 36ceb3be-97cf-46d3-97ed-e1b4ef0f155d
- Updated: 2026-10-20T22:42:00Z

## Task Summary
- **What to build**: Fix ISBN-10 casing and regex defect in CatalogCacheDao; update V2 DDL and SchemaMigrationManager; add COLLATE NOCASE in ProductDao and InvoiceDao; pass all tests.
- **Success criteria**: 10/10 tests pass in DaoStressAndEdgeCaseEmpiricalTest; 422+ tests pass in full test suite.
- **Interface contracts**: `PROJECT.md`
- **Code layout**: `src/main/java/...` and `src/main/resources/...`

## Key Decisions Made
- Normalized ISBN-10 values to uppercase upon ingestion in `save()` and before binding in queries in `findByIsbn()` and `delete()`.
- Updated SQL queries in `findByIsbn()` and `delete()` to use `COLLATE NOCASE` for both raw column comparison and `REPLACE(isbn10, '-', '')`.
- Updated schema definition in `V2__v1_1_enhancements.sql` and `SchemaMigrationManager.java` fallback to declare `isbn10 TEXT COLLATE NOCASE`.
- Hardened `ProductDao.findBySku` and `InvoiceDao.findInvoiceByNumber` queries with `COLLATE NOCASE`.
- Added 4 regression tests to `CatalogCacheDaoTest.java` verifying check digit casing, hyphens, and whitespace edge cases.

## Artifact Index
- `DISPATCH.md` — Original task instructions from orchestrator
- `BRIEFING.md` — Working context and status
- `progress.md` — Liveness heartbeat and milestone tracking
- `handoff.md` — Final verification and completion report

## Change Tracker
- **Files modified**:
  - `src/main/java/com/example/t/dao/CatalogCacheDao.java`: Regex fix, uppercase binding, query collation
  - `src/main/resources/com/example/t/db/V2__v1_1_enhancements.sql`: Updated isbn10 column to TEXT COLLATE NOCASE
  - `src/main/java/com/example/t/database/SchemaMigrationManager.java`: Updated fallback V2 DDL to TEXT COLLATE NOCASE
  - `src/main/java/com/example/t/dao/ProductDao.java`: Added COLLATE NOCASE to findBySku query
  - `src/main/java/com/example/t/dao/InvoiceDao.java`: Added COLLATE NOCASE to findInvoiceByNumber query
  - `src/test/java/com/example/t/dao/CatalogCacheDaoTest.java`: Added 4 regression unit tests for check digit X/x and regex stripping
- **Build status**: PASS (436/436 tests passing)
- **Pending issues**: None

## Quality Status
- **Build/test result**: PASS. `DaoStressAndEdgeCaseEmpiricalTest`: 10/10 pass. Full suite: 436/436 pass (0 failures, 0 errors).
- **Lint status**: Clean
- **Tests added/modified**: 4 new tests in `CatalogCacheDaoTest.java`

## Loaded Skills
- None (standard implementer/qa role)
