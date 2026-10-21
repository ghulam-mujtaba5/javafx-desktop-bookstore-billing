# Dispatch: worker_m4
Milestone 4 Implementation

## 2026-10-19T20:23:27Z
You are worker_m4, implementing Milestone 4 (POS Billing, Regional Settings & PDF Invoicing) for e:\javafx-desktop-bookstore-billing.
Your working directory is: e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_m4
Your parent is: orchestrator_1 (conversation ID: a53fb0a7-174c-42cf-8f32-366cc200af43)

MANDATORY FIRST STEP: Read the authoritative requirements at:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\ORIGINAL_REQUEST.md
and the project architecture at:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\PROJECT.md
and handoff reports from:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_m2\handoff.md
e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_m3\handoff.md

Your exclusive write ownership:
- src/main/resources/com/example/t/fxml/PosBilling.fxml
- src/main/resources/com/example/t/fxml/InvoiceHistory.fxml
- src/main/resources/com/example/t/fxml/Settings.fxml
- src/main/java/com/example/t/controller/PosBillingController.java
- src/main/java/com/example/t/controller/InvoiceHistoryController.java
- src/main/java/com/example/t/controller/SettingsController.java
- src/main/java/com/example/t/service/PdfInvoiceService.java
- src/main/java/com/example/t/controller/MainShellController.java (connect the real views into loadOrCreateView)
- Adapting/modernizing legacy screens (BillingScreen.java, ViewInvoicesScreen.java, SettingsScreen.java) to integrate with new DAOs and services.
- Automated tests in src/test/java/com/example/t/ covering Milestone 4 deliverables.

Specific Milestone 4 deliverables:
1. POS Billing Screen (`PosBilling.fxml` & `PosBillingController.java`):
   - Fast catalog search: by product name, SKU, or barcode with Enter key / barcode scanner auto-add to cart.
   - Interactive Cart TableView: line item details (Product Name, SKU, Unit Price, Quantity, Discount %, Line Total).
   - Cart actions: Increase/decrease quantity spinner or buttons, line item removal, clear cart.
   - Real-time order math using `BillingService`:
     - Subtotal, Order Discount % and amount, Taxable amount, Regional Tax / VAT rate and amount, Grand Total.
     - Currency formatting dynamically matching active store preset (`StoreSettings.getCurrencySymbol()`).
   - Payment & Tendered Cash:
     - Cash tendered input with real-time change due calculation (`changeDue = cashTendered - netTotal`).
     - Payment method selector (Cash / Card).
   - Atomic Checkout:
     - Save invoice and items via `InvoiceDao.createInvoice(Invoice, List<InvoiceItem>)`.
     - Automatically decrements stock in SQLite. Rolls back if stock is insufficient.
     - Generates PDF invoice via `PdfInvoiceService`.
     - Notification toast (`NotificationUtil.showSuccess(...)`).
     - Clears cart for next sale and updates shell header if needed.

2. Invoice History & Search Screen (`InvoiceHistory.fxml` & `InvoiceHistoryController.java`):
   - Master list TableView: Invoice ID, Customer Name, Date, Payment Method, Net Total.
   - Search filter: by Invoice ID, customer name, date range.
   - Detail view: line items of selected invoice (`invoice_items` with item name, quantity, unit price, total).
   - Actions:
     - Reprint / Re-export PDF receipt via `PdfInvoiceService`.
     - Export invoices to RFC 4180 CSV via `CsvSyncService.exportInvoicesToFile()`.

3. Multi-Regional Store Settings Screen (`Settings.fxml` & `SettingsController.java`):
   - Admin password protection: prompt for admin password using `PasswordUtil.verifyPassword()` before unlocking configuration.
   - Multi-regional preset switcher:
     - USA: USD ($), Sales Tax (e.g. 8.25%), English receipt.
     - UK: GBP (£), 20.0% VAT, English receipt.
     - GULF_UAE: AED (د.إ), 5.0% VAT, Bilingual Arabic/English receipt.
     - GULF_SAUDI: SAR (ر.س), 15.0% VAT, Bilingual Arabic/English receipt.
     - GULF_QATAR: QAR (ر.ق), 0.0% / custom VAT, Bilingual Arabic/English receipt.
   - Store metadata fields: Store Name, Address, Phone, Receipt Header/Footer note.
   - Change Admin Password dialog/form: hash new password using `PasswordUtil.hashPassword(...)`.
   - Save button persists to `SettingsDao.saveSettings(settings)` and calls `MainShellController.refreshHeader()`.

4. Branded OpenPDF Receipt Generation (`PdfInvoiceService.java`):
   - Uses OpenPDF 1.3.40 (`com.lowagie.text.*`).
   - Produces clean, professional 80mm receipt or A4 invoice PDF:
     - Store Name, Address, Phone, Regional Tax/VAT Registration Number, Date/Time, Invoice ID.
     - Table with Item Name, SKU/Barcode, Qty, Unit Price, Line Total.
     - Financial breakdown: Subtotal, Discount, Taxable Amount, Tax Rate & Amount, Grand Total, Tendered, Change Due.
     - Footer: Thank you note, return policy, and bilingual Arabic/English labels if region is Gulf.
     - Output directory: `%LOCALAPPDATA%\BookstoreBilling\invoices\` (with project fallback).

5. Main Navigation Shell Integration (`MainShellController.java`):
   - Replace placeholder cards in `loadOrCreateView()` with real loaders for `PosBilling.fxml`, `InvoiceHistory.fxml`, and `Settings.fxml`.
   - Ensure F5, F12, F14, F15 keyboard shortcuts and sidebar buttons seamlessly navigate between all 4 full screens.

6. Clean Compilation and Automated Tests:
   - Run `.\mvnw.cmd clean test-compile` and `.\mvnw.cmd test` to ensure 100% clean build with all 299+ tests passing!
