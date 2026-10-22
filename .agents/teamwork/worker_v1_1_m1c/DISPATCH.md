## 2026-10-21T07:38:34Z
You are Worker 1.1-C (Keyboard-First POS & Split Tender Payments Worker).
Your working directory is: e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_v1_1_m1c
Your parent orchestrator is conversation ID: 36ceb3be-97cf-46d3-97ed-e1b4ef0f155d

Read:
- e:\javafx-desktop-bookstore-billing\.agents\teamwork\ORIGINAL_REQUEST.md (specifically Requirement R2 under ## 2026-10-20T19:51:37Z)
- e:\javafx-desktop-bookstore-billing\.agents\teamwork\PROJECT.md
- Explorer 1 Report (Barcode & Hotkeys): e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_v1_1_m1c_1\handoff.md
- Explorer 2 Report (Split Tender & Persistence): e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_v1_1_m1c_2\handoff.md
- Explorer 3 Report (Park/Resume, Notes & Gift Receipt): e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_v1_1_m1c_3\handoff.md

## Scope & Objective
Implement Milestone 1.1-C: Keyboard-First POS Checkout & Split Tender Payments (Requirement R2):

1. **Barcode Scanner Wedge Buffering (`src/main/java/com/example/t/util/BarcodeScannerBuffer.java`)**:
   - Continuous keyboard wedge buffer using 40ms inter-keystroke threshold ($T = 40\text{ ms}$, $L_{\min} = 4$) to discriminate hardware scanner bursts (5-25ms) from manual typing (120-250ms).
   - Injectable `LongSupplier clock` for deterministic unit testing.
   - Enter terminator detection (`KeyCode.ENTER`, `\r`, `\n`) that consumes the event and strips stray barcode digits from whichever `TextInputControl` had focus (`customerNameField`, `discountPercentField`, `cashTenderedField`, etc.).
   - Dispatches parsed barcode to a `Consumer<String> onBarcodeDetected` callback.
   - Make sure `com.example.t.util` package is properly exported in `src/main/java/module-info.java` if needed.

2. **Domain Models & Entities**:
   - `src/main/java/com/example/t/model/ParkedCart.java`:
     - Properties: `cartId`, `displayName`, `customerName`, `customerPhone`, `items` (deep copy of `List<InvoiceItem>`), `discountPercent`, `notes`, `parkedAt`.
   - `src/main/java/com/example/t/model/InvoiceItem.java`:
     - Add `discountPercent` (double), `discountAmount` (double), and `notes` (String) properties, getters, setters, and copy constructor.

3. **Billing Calculation (`src/main/java/com/example/t/service/BillingService.java`)**:
   - Add split tender calculation: `calculateSplitTender(double netTotal, List<PaymentTender> tenders)`:
     - Cash: allows over-tendering ($t \ge R$) with change calculation ($c = t - R$), or under-tendering.
     - Card / Store Credit: strictly bounded by remaining balance ($0 < t \le R$), no cashback on electronic or voucher payments.
     - Checkout invariant: remaining balance must equal 0.00.
     - Return `SplitTenderResult(double netTotal, double totalApplied, double totalTendered, double totalChangeDue, double remainingBalance, boolean isFullyPaid)`.
   - Support line-item discounts compounding with cart discount.

4. **Persistence Integration (`src/main/java/com/example/t/dao/InvoiceDao.java`)**:
   - Overload `createInvoice(Invoice invoice, List<InvoiceItem> items, List<InvoicePayment> payments, Connection conn)`:
     - Atomically within the uncommitted transaction: decrements stock, inserts into `invoices`, batch inserts `invoice_items` (including `discount_percent`, `discount_amount`, `notes`), and batch inserts payments via `invoicePaymentDao.savePayments(invoiceId, payments, conn)`.
     - In `invoices`: set `payment_method = (payments.size() > 1 ? "SPLIT" : payments.get(0).getPaymentType().name())`, `cash_tendered = totalCashTendered`, `change_due = totalChangeDue`.
     - If `payments` is null or empty, create a default CASH payment to guarantee all database invoices have uniform child records.
   - Update `getInvoiceItems(int invoiceId, Connection conn)` to read `discount_percent`, `discount_amount`, and `notes`.

5. **Price-Suppressed Gift Receipt (`src/main/java/com/example/t/service/PdfInvoiceService.java`)**:
   - Add `generateGiftReceipt(Invoice invoice, List<InvoiceItem> items, Path outputPath, ...)`:
     - Suppresses all unit prices, line totals, subtotal, discounts, tax amounts, and grand total.
     - Renders store header, invoice #, Code-128 barcode, date, item descriptions, and quantities.
     - Displays gift policy notice: "Gift Receipt: Exchangeable within 30 days with receipt".

6. **POS Controller & UI Integration (`src/main/java/com/example/t/controller/PosBillingController.java` & `PosBilling.fxml`)**:
   - Keyboard Hotkeys (F1–F12):
     - F1: Focus Search/Scan field
     - F2: Edit / Step Quantity of selected cart item
     - F3: Apply Discount (Cart discount or item discount)
     - F4: Focus Customer Name field
     - F5: Add Line-Item Note on selected cart item
     - F6: Park Active Cart (Hold Order)
     - F7: Resume Parked Cart (Picker / recall)
     - F8: Open Split Tender modal dialog / panel
     - F9: Exact Cash Tender
     - F10: Complete Checkout
     - F11: Toggle Gift Receipt checkbox
     - F12: Clear / Void Cart (with confirmation)
     - Escape: Cancel / close dialogs / refocus search
     - Delete: Remove selected line item
   - Connect `BarcodeScannerBuffer` to POS root node. When a barcode is detected, call `handleSearchEnter` / `addProductBySkuOrBarcode` without corrupting active textfields.
   - Coordinate with `MainShellController.java:300-320`: If `currentView == POS_BILLING`, allow F5 to attach line note and F12 to clear cart when cart is not empty, while preserving global navigation when cart is empty or on other views (maintaining 100% compliance with `MainShellControllerTest` and `MasterNavigationShellEmpiricalChallengeTest`).
   - Multi-Cart Park / Resume: Maintain `ObservableList<ParkedCart> parkedCarts`. Update Hold / Recall button text with count badges.
   - Gift Receipt Checkbox: Add toggle in UI, pass flag to generate gift receipt PDF upon checkout if selected.

7. **Unit & Empirical Tests**:
   - `src/test/java/com/example/t/util/BarcodeScannerBufferTest.java` (test rapid burst <=40ms vs slow typing >=120ms, Enter terminator, textfield suffix stripping, clock injection).
   - `src/test/java/com/example/t/service/SplitTenderBillingTest.java` (test exact tender, cash over-tender with change, card/store credit no-cashback validation, remaining balance calculation, 3-way split).
   - `src/test/java/com/example/t/dao/InvoiceSplitPaymentDaoTest.java` (test atomic commit of invoice + items + payments, rollback on error, cascade delete).
   - `src/test/java/com/example/t/model/ParkedCartTest.java` (test snapshot isolation, deep copying, restoration).
   - `src/test/java/com/example/t/service/GiftReceiptTest.java` (test price suppression in generated PDF, gift policy text).

## MANDATORY INTEGRITY WARNING
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Run `.\mvnw.cmd test` and document build and test results.
Write your handoff report to:
e:\javafx-desktop-bookstore-billing\.agents\teamwork\worker_v1_1_m1c\handoff.md
Send a completion message back to parent (36ceb3be-97cf-46d3-97ed-e1b4ef0f155d).
