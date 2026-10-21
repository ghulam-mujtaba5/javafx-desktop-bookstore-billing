# Plan: JavaFX Bookstore & Billing v1.1.0 Enhancement

## Phase 0: Survey & Technical Baseline
Dispatch 3 parallel Explorers:
- **Explorer 1 (Catalog, ISBN & Metadata)**: Map models (`Product`), DAO layer, schema migration capabilities, HTTP clients, and background threading pattern for Google Books & Open Library APIs and ISBN validation.
- **Explorer 2 (POS Billing, Key Events & Peripherals)**: Map `PosBillingController`, `BillingService`, keyboard event filtering (`F1-F12`), barcode scanner input buffer, cart models, payment split tender logic.
- **Explorer 3 (Reporting, Printing & Build Verification)**: Map thermal ESC/POS streamer integration points, label generation, Dead Stock Radar & Z-report persistence requirements, pom.xml versioning & existing test suite baseline.

## Phase 1: Decomposition & Interface Contracts
- Synthesize explorer findings into `PROJECT.md` v1.1 enhancement milestones.
- Specify exact interface contracts and code layout for new services and models.

## Phase 2: Sequential Milestone Execution (Explorer -> Worker -> Reviewers -> Challengers -> Auditor -> Gate)
- **Milestone 1.1-A (R1)**: Asynchronous `MetadataService`, ISBN-10/13 checksum validation, local SQLite cache, Google Books/Open Library fallback, cover art caching, zero UI freezes.
- **Milestone 1.1-B (R2)**: Keyboard shortcuts (`F1-F12`), barcode scanner wedge input buffering, split tender payments (Cash, Card, Store Credit), cart park/resume, line-item notes & discounts.
- **Milestone 1.1-C (R3)**: Raw ESC/POS bytecode streamer (80mm/58mm), paper cut, drawer kick pulse, Code-128 / EAN-13 shelf/spine label generation (50x30mm).
- **Milestone 1.1-D (R4)**: Dead Stock Radar (60/90/180-day non-moving titles for distributor returns grouped by publisher), Dynamic Reorder Assistant, End-of-Day Financial Z-Report with audit ledger.
- **Milestone 1.1-E (R5)**: Version bump to 1.1.0 in pom.xml, title bar, metadata; ensure 100% of test suite passes cleanly on JDK 21 LTS (`mvnw.cmd test`); dev-mode verification.

## Phase 3: Final Verification & Sentinel Handoff
- Full test suite run (`mvnw.cmd test`), all acceptance criteria verified.
- Report completion back to Sentinel with evidence and artifact links.
