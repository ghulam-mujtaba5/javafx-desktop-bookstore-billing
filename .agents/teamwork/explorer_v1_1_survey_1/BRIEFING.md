# BRIEFING — 2026-10-20T20:55:00Z

## Mission
Investigate codebase and design architecture for Requirement R1: Intelligent Online/Offline ISBN Metadata Enrichment Engine.

## 🔒 My Identity
- Archetype: explorer
- Roles: investigation, synthesis
- Working directory: e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_v1_1_survey_1
- Original parent: 36ceb3be-97cf-46d3-97ed-e1b4ef0f155d
- Milestone: v1.1 Survey - Requirement R1 (Catalog, ISBN & Metadata)

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- Investigate R1 (Intelligent Online/Offline ISBN Metadata Enrichment Engine)
- Do NOT modify codebase source files directly

## Current Parent
- Conversation ID: 36ceb3be-97cf-46d3-97ed-e1b4ef0f155d
- Updated: not yet

## Investigation State
- **Explored paths**:
  - `pom.xml`, `src/main/java/module-info.java`
  - `src/main/java/com/example/t/Product.java`, `src/main/java/com/example/t/model/Category.java`
  - `src/main/java/com/example/t/database/DatabaseManager.java`, `SchemaMigrationManager.java`, `V1__init_schema.sql`
  - `src/main/java/com/example/t/dao/ProductDao.java`
  - `src/main/java/com/example/t/controller/StockManagementController.java`, `StockManagement.fxml`
  - `src/main/java/com/example/t/controller/PosBillingController.java`, `PosBilling.fxml`
  - `src/test/java/com/example/t/ProductTest.java`, `ProductDaoTest.java`
- **Key findings**:
  1. `java.net.http.HttpClient` in JDK 21 standard library is the ideal asynchronous, non-blocking HTTP client. Requires adding `requires java.net.http;` to `module-info.java`.
  2. No JSON parsing library currently exists in `pom.xml`. Either `com.google.code.gson:gson:2.11.0` or a zero-dependency `SimpleJson` parser should be adopted.
  3. `Product.java` currently lacks first-class `isbn`, `subtitle`, `genre`, `publicationYear`, `format`, and `coverImagePath` properties; in `ProductDao.java`, `product.getEdition()` was mapped into the SQLite `isbn` column.
  4. SQLite schema currently at version 1. A clean migration to version 2 (`V2__catalog_cache_and_isbn_enrichment.sql`) can introduce `catalog_cache` and indexes without breaking existing tables.
  5. `DatabaseManager` should add `getCoversDirectory()` resolving to `%LOCALAPPDATA%\BookstoreBilling\data\covers\`.
  6. Checksum validation algorithms (Mod 11 for ISBN-10, Mod 10 for ISBN-13) and bidirectional 978-prefix conversion fully formulated.
  7. Multi-tiered caching (L1 in-memory, L2 SQLite `catalog_cache`, local JPEG storage) guarantees sub-millisecond offline lookup with zero UI thread freezing.
  8. UI touchpoints in `StockManagementController` (Add/Edit Dialog ISBN lookup & auto-fill) and `PosBillingController` (barcode scan fallback to metadata service) mapped out.
- **Unexplored areas**: None for R1 survey scope.

## Key Decisions Made
- Propose non-blocking async architecture using `CompletableFuture` and daemon thread executor.
- Provide two JSON parser implementation options: standard Google Gson vs zero-dependency `SimpleJson`.
- Preserve existing `Product.java` constructors and methods to ensure 100% backward compatibility with all 374+ passing tests.

## Artifact Index
- e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_v1_1_survey_1\handoff.md — Final 5-component handoff report
