# Requirement R2 Architecture Survey: Keyboard-First POS Checkout & Split Tender Payments

## 1. Observation

### 1.1 Existing POS Architecture and Controller
- **`PosBillingController.java` (`src/main/java/com/example/t/controller/PosBillingController.java`)**:
  - Manages active cart items via an in-memory `ObservableList<InvoiceItem> cartItems` (line 82).
  - Handles catalog searching and Enter key action in `handleSearchEnter` (lines 274–316): attempts exact SKU lookup in `ProductDao`, then filtered catalog match, then selects first catalog table item.
  - Recalculates financial totals in `recalculateOrder` (lines 426–463) using `BillingService.calculateInvoiceItems`.
  - Performs checkout in `handleCheckout` (lines 470–532): validates single payment method (`paymentMethodCombo`), ensures tendered cash is `>= currentNetTotal` if payment method is "Cash", creates `Invoice` and `List<InvoiceItem>`, calls `invoiceDao.createInvoice(invoice, itemsToSave)` inside an atomic transaction, generates PDF if selected, and resets cart.
  - Currently lacks:
    1. Dedicated hotkey handling for F1–F12 keys (only a static text label on `btnCheckout`: `text="✔ COMPLETE CHECKOUT [F10]"` in `PosBilling.fxml:190`).
    2. Continuous scanner wedge buffering (keystrokes directly target whatever UI control currently has focus).
    3. Split tender payment capabilities (only a single `ComboBox<String> paymentMethodCombo` with Cash, Credit Card, Debit Card at lines 69, 203).
    4. Multi-cart hold/resume mechanism (only a single `cartItems` list exists; clearing it drops all items).
    5. Line-item notes or item-level discounts (only whole-order `discountPercentField` at line 62).
    6. Gift receipt generation option (only full tax invoice PDF at line 509).

- **`MainShellController.java` (`src/main/java/com/example/t/controller/MainShellController.java`)**:
  - Registers Scene-level keyboard navigation in `setupKeyShortcuts(Scene scene)` (lines 290–308):
    ```java
    scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
        KeyCode code = event.getCode();
        if (code == KeyCode.F5) {
            navigateTo(ViewType.POS_BILLING);
            event.consume();
        } else if (code == KeyCode.F12) {
            navigateTo(ViewType.STOCK_MANAGEMENT);
            event.consume();
        } else if (code == KeyCode.F14 || (event.isControlDown() && code == KeyCode.H)) {
            navigateTo(ViewType.INVOICE_HISTORY);
            event.consume();
        } else if (code == KeyCode.F15 || (event.isControlDown() && code == KeyCode.S)) {
            navigateTo(ViewType.STORE_SETTINGS);
            event.consume();
        }
    });
    ```
  - Crucial observation: `MainShellController` consumes `KeyCode.F5` and `KeyCode.F12` at the `Scene` capturing phase. Any key consumed by `scene.addEventFilter` never reaches child views or controllers unless explicitly coordinated.

### 1.2 Persistence and Data Models
- **`Invoice.java` (`src/main/java/com/example/t/Invoice.java`)**:
  - Encapsulates `invoiceId`, `orderNum`, `customerName`, `invoiceDate`, `products`, `orderTotal`, `discountPercentage`, `discount`, `netPrice` (lines 20–29).
  - Lacks payment breakdown properties, line notes, or gift receipt flags.
- **`InvoiceItem.java` (`src/main/java/com/example/t/model/InvoiceItem.java`)**:
  - Encapsulates `itemId`, `invoiceId`, `productId`, `productName`, `sku`, `quantity`, `unitPrice`, `lineTotal` (lines 14–22).
  - Lacks item-level discount percentage/amount and notes.
- **`InvoiceDao.java` (`src/main/java/com/example/t/dao/InvoiceDao.java`)**:
  - `createInvoice(Invoice invoice, List<InvoiceItem> items, Connection conn)` (lines 31–115):
    - Atomically decrements product stock in `products` (lines 37–48).
    - Inserts invoice record into `invoices` table with fixed hardcoded values: `tax_rate=0.0, tax_amount=0.0, payment_method='CASH', currency_code='USD', region='USA'` (lines 63–67).
    - Inserts line items into `invoice_items` (`invoice_id, product_id, quantity, unit_price, line_total`) (lines 92–104).
- **`SchemaMigrationManager.java` & `V1__init_schema.sql` (`src/main/resources/com/example/t/db/V1__init_schema.sql`)**:
  - Current schema version is `1`.
  - Tables: `schema_migrations`, `categories`, `products`, `invoices`, `invoice_items`, `store_settings`.
  - Invoices table contains: `invoice_id`, `invoice_number`, `customer_name`, `customer_phone`, `invoice_date`, `subtotal`, `discount_percent`, `discount_amount`, `tax_rate`, `tax_amount`, `net_total`, `cash_tendered`, `change_due`, `payment_method`, `currency_code`, `region`.
  - No `invoice_payments` table exists yet.
  - `invoice_items` lacks `discount_percent`, `discount_amount`, and `notes`.

### 1.3 Mathematical and Calculation Engines
- **`BillingService.java` (`src/main/java/com/example/t/service/BillingService.java`)**:
  - Calculates subtotal, order discount amount, taxable amount, tax amount, net total, and change due with `BigDecimal` `HALF_UP` 2-decimal rounding (lines 105–152).
  - Assumes a single cash tender amount (`cashTendered`) and calculates single `changeDue = cashTendered - netTotal`.
  - Does not support split tender math across cash, card, and store credit.
  - Does not support item-level discounts.
- **`CalculationOracle.java` (`src/test/java/com/example/t/e2e/harness/CalculationOracle.java`)**:
  - Mirrors `BillingService` math precisely in test harness.

### 1.4 Receipt and Document Printing Engine
- **`PdfInvoiceService.java` (`src/main/java/com/example/t/service/PdfInvoiceService.java`)**:
  - Renders A4 or 80mm thermal PDF invoices via OpenPDF 1.3.40 (lines 141–357).
  - Displays full price breakdown: Unit Price, Line Total, Subtotal, Discount, Tax, Grand Total.
  - Does not currently support gift receipts (which must omit prices and show exchange/gift policy).

---

## 2. Logic Chain

### 2.1 Keyboard Shortcut Architecture (F1–F12 Zero-Touch Operation)
1. **The Problem**: In high-throughput bookstore retail (rush hours, textbook seasons), requiring the cashier to grab the mouse to adjust quantities, apply discounts, or complete checkout slows throughput significantly and causes RSI. Cashiers need full keyboard operation without losing text field focus or breaking mouse capability.
2. **JavaFX Event Dispatch Mechanics**:
   - The JavaFX event dispatch chain consists of capturing phase (`EventFilter`) followed by bubbling phase (`EventHandler`).
   - If an event filter is attached to `Scene` or the POS root `BorderPane`:
     - Standard typing (characters, numbers, punctuation) in a `TextField` MUST NOT be consumed.
     - Function keys `F1`–`F12` do not correspond to printable text.
     - Intercepting `KeyEvent.KEY_PRESSED` for function keys at the root container or Scene level allows instant trigger of POS actions and calling `event.consume()`, which safely prevents the focused `TextField` from receiving an unwanted keystroke.
3. **Coordination with `MainShellController`**:
   - `MainShellController` currently consumes `F5` (POS) and `F12` (Stock) globally at the Scene level.
   - To prevent conflict:
     - `MainShellController` should check whether the active view's controller is `PosBillingController` and allow POS-specific hotkeys to take precedence or execute contextually.
     - Alternatively, POS-specific F-key mappings can use non-conflicting F-keys or `MainShellController` can delegate F-keys to the active view if the view consumes them.
4. **Target POS Hotkey Mapping**:
   | Hotkey | Action | Implementation Mechanism |
   |---|---|---|
   | **F1** | Focus Product / Barcode Search | `searchField.requestFocus()`, select all text |
   | **F2** | Edit Quantity / Step Quantity | Focus quantity edit dialog/spinner or increment selected cart item |
   | **F3** | Apply Discount (Cart / Line) | Focus `discountPercentField` or open Item Discount popup |
   | **F4** | Customer Search / Name | Focus `customerNameField` |
   | **F5** | Add Line-Item Note | Open modal/popover to attach note to selected item |
   | **F6** | Park Active Cart (Hold Order) | Snapshot cart to parked carts list, clear active cart |
   | **F7** | Resume Parked Cart (Recall) | Open cart selection popover / cycle active carts |
   | **F8** | Payment Tender Mode / Split Tender | Focus payment breakdown panel / open Split Tender dialog |
   | **F9** | Exact Cash Tender | Set cash tender to exact `currentNetTotal` |
   | **F10** | Complete Checkout | Trigger `handleCheckout(null)` |
   | **F11** | Toggle Gift Receipt | Toggle `giftReceiptCheckBox.setSelected(!isSelected())` |
   | **F12** | Clear / Void Cart | Trigger `handleClearCart(null)` with confirmation |
   | **Escape** | Cancel / Return to Search | Clear selection, close popups, refocus `searchField` |
   | **Delete** | Remove Line Item | Remove selected row from `cartTable` |

---

### 2.2 Barcode Scanner Wedge Input Buffering
1. **The Problem**:
   - Hardware USB/Bluetooth barcode scanners act as keyboard wedges (HID devices).
   - If a cashier scans a book while focus happens to be in `customerNameField`, `discountPercentField`, or an unrelated control, the raw barcode digits get typed into that field, corrupting customer data and failing to add the book to the cart.
2. **Hardware Timing vs Human Typing Discrimination**:
   - Hardware barcode scanners emit the full barcode character sequence in a rapid hardware burst: 5ms to 25ms per character (total scan time 30ms–70ms for 13 digits), terminated by an `Enter` (`\r` or `\n`) keystroke.
   - Human typists average 120ms–250ms per keystroke; even record-speed burst typists exceed 60ms per character on non-words.
   - Setting an inter-keystroke threshold of **35ms to 45ms** (or an average keystroke duration $\le 40\text{ ms}$ over $\ge 4$ characters) provides a reliable discriminant.
3. **Buffering Architecture (`BarcodeScannerBuffer`)**:
   - Attached as an event filter on `KeyEvent.KEY_TYPED` and `KeyEvent.KEY_PRESSED` at the POS root node or Scene:
     - Buffers incoming characters with timestamps (`firstKeystrokeTime`, `lastKeystrokeTime`).
     - If the interval between successive keystrokes exceeds 80ms, the buffer resets (human typing).
     - When `KeyCode.ENTER` or `\r` is encountered:
       - If `buffer.length() >= 4` AND `(now - firstKeystrokeTime) / buffer.length() <= 45ms`:
         - Identified as a hardware scanner burst!
         - `event.consume()` to prevent submitting whatever form control had focus.
         - If focus was on a text control (e.g. `customerNameField`), strip the stray scanned characters or revert text.
         - Invoke `addProductBySkuOrBarcode(scannedBarcode)` directly.
         - Clear buffer and refocus `searchField`.
       - If duration $> 45\text{ ms}$: treat as ordinary human Enter key press, pass through unconsumed.

---

### 2.3 Split Tender Payment Logic & Database Schema Needs
1. **The Problem**:
   - Customers frequently split payments across multiple tenders: e.g. $20.00 cash + $45.50 card, or $15.00 store credit voucher + remaining balance on debit card.
   - The current schema only allows a single `payment_method` string and assumes `cash_tendered` covers the whole order.
2. **Mathematical Calculation Model**:
   - Let $N$ be the order Net Total.
   - Tenders are a list of `PaymentTender(TenderType type, double amountApplied, double tenderedAmount, String reference)`.
   - Rules:
     - `CARD` and `STORE_CREDIT`: `amountApplied` cannot exceed remaining unpaid balance ($N - \sum a_{prev}$). No cashback given on electronic payments.
     - `CASH`: Customer may tender an amount $t \ge a$. The applied amount is $a = \min(t, \text{remainingBalance})$, and change due is $c = t - a$.
     - Total paid = $\sum a_i$.
     - Remaining balance = $\max(0.0, N - \sum a_i)$.
     - Checkout is valid only when remaining balance is `0.00`.
3. **Database Schema Needs (`V2` Migration)**:
   - To support 1-to-many payments without breaking existing schema contracts, introduce an `invoice_payments` table in SQLite:
     ```sql
     CREATE TABLE IF NOT EXISTS invoice_payments (
         payment_id INTEGER PRIMARY KEY AUTOINCREMENT,
         invoice_id INTEGER NOT NULL REFERENCES invoices(invoice_id) ON DELETE CASCADE,
         payment_type TEXT NOT NULL, -- 'CASH', 'CARD', 'STORE_CREDIT'
         amount REAL NOT NULL,
         tendered_amount REAL DEFAULT 0.0,
         change_due REAL DEFAULT 0.0,
         reference_info TEXT,
         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
     );
     CREATE INDEX IF NOT EXISTS idx_invoice_payments_invoice ON invoice_payments(invoice_id);
     ```
   - In `invoices` table:
     - Maintain backward compatibility by setting `payment_method = 'SPLIT'` when multiple tenders exist (or `CASH`/`CARD`/`STORE_CREDIT` if single tender).
     - Store total cash tendered across all cash payments in `cash_tendered`, and total change returned in `change_due`.
     - Existing queries in `InvoiceDao`, CSV sync, and sales reports remain 100% functional.

---

### 2.4 Park and Resume Multiple Active Carts (Hold Order)
1. **The Problem**:
   - In a bookstore, customers frequently step away from the register to find an additional title or grab an forgotten wallet while other customers wait in line.
   - Forcing the cashier to clear the cart or hold up the line causes customer friction.
2. **Data Structure (`ParkedCart`)**:
   ```java
   public class ParkedCart {
       private final String cartId;                  // e.g. "CART-1", UUID
       private final String displayName;             // e.g. "Hold #1: John (3 items • $45.00)"
       private final LocalDateTime parkedAt;
       private final String customerName;
       private final List<InvoiceItem> items;        // Snapshot copy of cart items
       private final double discountPercent;
       private final String notes;
       private final boolean giftReceipt;
       private final List<PaymentTender> partialTenders;
   }
   ```
3. **UI Switching Mechanism**:
   - In `PosBillingController`:
     - Maintain `ObservableList<ParkedCart> parkedCarts = FXCollections.observableArrayList()`.
     - In `PosBilling.fxml`:
       - Add a Parked Carts Tab Bar or dropdown selector above the cart table.
       - Display active cart indicator, e.g. `[Active Cart: Order #1 (4 items)] [Hold (F6)] [Parked Carts (2) (F7)]`.
   - **Park Workflow (`F6`)**:
     - Validates that active cart has at least 1 item.
     - Copies current cart items, customer name, discount percent, notes, and gift receipt flag into a new `ParkedCart`.
     - Adds to `parkedCarts`.
     - Clears active cart, resets fields, shows notification toast: "Cart parked as Hold #X. Ready for next customer."
   - **Resume Workflow (`F7`)**:
     - If multiple carts are parked, opens a quick-pick modal or cycles through parked carts.
     - Restores items, customer name, discounts, and recalculates totals.
     - Removes resumed cart from `parkedCarts`.

---

### 2.5 Line-Item Notes, Discounts (Item % vs Cart %), and Gift Receipts
1. **Line-Item vs Cart Discounts**:
   - **Item-level discount**: Applied directly to an individual line item (e.g. 20% off damaged book or promotional sticker).
     $$\text{lineTotal} = \text{round}\left(\text{quantity} \times \text{unitPrice} \times \left(1 - \frac{\text{itemDiscount}\%}{100}\right)\right)$$
   - **Cart-level discount**: Applied across the total taxable subtotal of all items:
     $$\text{subtotal} = \sum \text{lineTotal}_i$$
     $$\text{cartDiscountAmount} = \text{round}\left(\text{subtotal} \times \frac{\text{cartDiscount}\%}{100}\right)$$
     $$\text{taxableAmount} = \text{subtotal} - \text{cartDiscountAmount}$$
   - Both levels must be supported simultaneously without double counting.
2. **Line-Item Notes**:
   - Added to `InvoiceItem` (`notesProperty`) and persisted in `invoice_items` (`notes TEXT`).
   - Used for special notes: "Signed by author", "Slight shelf wear", "Gift wrapped".
3. **Gift Receipts**:
   - Checkbox / Toggle: `[ ] Print Gift Receipt [F11]`.
   - In `PdfInvoiceService`:
     - Render a specialized receipt layout:
       - Displays: Store Name, Address, Phone, Date, Invoice #, Barcode, Item Name, SKU, and Quantity.
       - **Strictly suppresses**: Unit Price, Line Total, Subtotal, Discount, Tax, Grand Total, and Payment Tender details.
       - Includes gift policy: "Gift Receipt: Valid for exchange or store credit within 30 days of purchase."

---

## 3. Caveats
1. **Hardware Keyboard Wedge Variability**: Different barcode scanner manufacturers configure suffixes differently by default (some send CR `\r`, some LF `\n`, some CRLF `\r\n`, and a few send TAB `\t`). The buffer parser must accept `\r`, `\n`, or `KeyCode.ENTER` as valid terminators.
2. **MainShellController Shortcut Interception**: Existing unit tests in `MainShellControllerTest` and `MasterNavigationShellEmpiricalChallengeTest` explicitly test that F5 switches to POS and F12 switches to Stock Management. Enhancements to POS shortcuts must not break those existing navigation tests.
3. **Test Suite Baseline**: One test in `FinalAcceptanceCriteriaVerificationTest` (`testAcceptanceCriterion6_MultiRegionalCurrencyAndTaxCalculationSwitching:528`) currently fails due to an Arabic currency symbol assertion expectation in header text, unrelated to POS checkout. All 397 other tests pass cleanly.

---

## 4. Conclusion
Requirement R2 (Keyboard-First POS Checkout & Split Tender Payments) can be cleanly implemented with high performance and zero regressions by:
1. Adding a `KeyEventHandler`/`EventFilter` to `PosBillingController` handling F1–F12 quick actions with a visible shortcut helper bar.
2. Implementing an asynchronous `BarcodeScannerBuffer` with a 40ms inter-keystroke threshold and Enter terminator.
3. Introducing SQLite schema migration `V2` adding `invoice_payments` table, and `discount_percent`, `discount_amount`, `notes` columns to `invoice_items`.
4. Updating `BillingService` with pure math methods for split tenders and line-item discounts while maintaining full backward compatibility for existing methods.
5. Implementing `ParkedCart` in-memory collection with F6 (Park) / F7 (Resume) and UI cart switcher.
6. Adding a gift receipt generation mode in `PdfInvoiceService` that suppresses financial amounts.

---

## 5. Verification Method

### 5.1 Independent Verification Commands
- Compile test code:
  ```powershell
  .\mvnw.cmd test-compile
  ```
- Run billing and POS test suites:
  ```powershell
  .\mvnw.cmd test -Dtest=PosBillingControllerTest,PosBillingEmpiricalChallengeTest,PosBillingE2ETest,BillingServiceTest
  ```
- Run full test suite:
  ```powershell
  .\mvnw.cmd test
  ```

### 5.2 Key Files and Artifacts to Inspect
- `src/main/java/com/example/t/controller/PosBillingController.java`
- `src/main/resources/com/example/t/fxml/PosBilling.fxml`
- `src/main/java/com/example/t/service/BillingService.java`
- `src/main/java/com/example/t/model/InvoiceItem.java`
- `src/main/java/com/example/t/Invoice.java`
- `src/main/java/com/example/t/dao/InvoiceDao.java`
- `src/main/java/com/example/t/database/SchemaMigrationManager.java`
- `src/main/java/com/example/t/service/PdfInvoiceService.java`
- `src/main/java/com/example/t/controller/MainShellController.java`

### 5.3 Invalidation Conditions
- Any proposed change breaking existing `BillingService.calculate()` mathematical results or rounding precision.
- Consuming F5 or F12 in a way that causes `MainShellControllerTest.testKeyboardShortcuts` to fail.
- Database migration that breaks existing V1 SQLite database files or fails to migrate existing columns.
