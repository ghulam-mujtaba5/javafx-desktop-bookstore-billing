# Milestone 1.1-C Architecture & Implementation Strategy Report: Park/Resume, Line Notes, Discounts & Gift Receipts

**Author**: Explorer 3  
**Working Directory**: `e:\javafx-desktop-bookstore-billing\.agents\teamwork\explorer_v1_1_m1c_3`  
**Target Milestone**: M1.1-C (Keyboard-First POS & Checkout Enhancements)  
**Parent Orchestrator**: `36ceb3be-97cf-46d3-97ed-e1b4ef0f155d`  

---

## 1. Observation

### 1.1 Existing POS Cart and Controller State
- **`PosBillingController.java` (`src/main/java/com/example/t/controller/PosBillingController.java`)**:
  - Cart items are managed via `private final ObservableList<InvoiceItem> cartItems = FXCollections.observableArrayList();` (line 115).
  - A minimal, rudimentary hold mechanism exists at lines 71–83 and 726–766:
    - Uses a private static inner class `HeldOrder` (`customerName`, `items`, `discountPercent`, `timestamp`).
    - Stored in a raw `private final List<HeldOrder> heldOrders = new ArrayList<>();` (line 83).
    - `handleHoldCart` (lines 726–743): adds a `HeldOrder` to `heldOrders`, clears `cartItems`, updates `btnRecallOrder.setText("▶ Recall Cart (" + heldOrders.size() + ")")`.
    - `handleRecallCart` (lines 746–766): pops the latest cart in strict LIFO order (`heldOrders.remove(heldOrders.size() - 1)`), restores items and customer name.
    - **Deficiencies**:
      1. Not backed by a first-class `ParkedCart` domain model in `com.example.t.model`.
      2. No public `ObservableList<ParkedCart>` to enable visual cart tabs, ComboBox pickers, or UI data bindings.
      3. Items in `HeldOrder` are shallow-copied (`new ArrayList<>(items)`), meaning each `InvoiceItem` reference is shared with the caller; mutating an item mutates the held snapshot.
      4. Does not preserve customer phone, line notes, item-level discounts, or gift receipt flag.
      5. Strict LIFO retrieval prevents cashiers from selecting a specific customer's cart when 3+ carts are parked.
- **`PosBilling.fxml` (`src/main/resources/com/example/t/fxml/PosBilling.fxml`)**:
  - Lines 107–108 define:
    ```xml
    <Button fx:id="btnHoldOrder" onAction="#handleHoldCart" styleClass="chip-btn" text="⏸ Hold Cart" />
    <Button fx:id="btnRecallOrder" onAction="#handleRecallCart" styleClass="chip-btn" text="▶ Recall Cart (0)" />
    ```
  - Lines 117–128 define `cartTable` columns:
    ```xml
    <TableColumn fx:id="colCartSku" prefWidth="95.0" text="SKU" />
    <TableColumn fx:id="colCartName" prefWidth="220.0" text="Product Name" />
    <TableColumn fx:id="colCartPrice" prefWidth="90.0" text="Unit Price" />
    <TableColumn fx:id="colCartQty" prefWidth="70.0" text="Qty" />
    <TableColumn fx:id="colCartTotal" prefWidth="95.0" text="Total" />
    ```
  - Missing UI controls:
    1. No line-level discount percentage column or editor.
    2. No line-item note column or note attachment button.
    3. No gift receipt toggle (`[ ] Print Gift Receipt [F11]`).

### 1.2 Database Schema and DAO Layer
- **`V2__v1_1_enhancements.sql` (`src/main/resources/com/example/t/db/V2__v1_1_enhancements.sql`)**:
  - Migration V2 is already written and active (`SchemaMigrationManager.CURRENT_SCHEMA_VERSION = 2`):
    ```sql
    -- 4. Line-item notes and discounts in invoice_items
    ALTER TABLE invoice_items ADD COLUMN discount_percent REAL DEFAULT 0.0;
    ALTER TABLE invoice_items ADD COLUMN discount_amount REAL DEFAULT 0.0;
    ALTER TABLE invoice_items ADD COLUMN notes TEXT;
    ```
- **`InvoiceItem.java` (`src/main/java/com/example/t/model/InvoiceItem.java`)**:
  - Currently contains only: `itemId`, `invoiceId`, `productId`, `productName`, `sku`, `quantity`, `unitPrice`, and `lineTotal` (lines 14–22).
  - Lacks `discountPercent`, `discountAmount`, and `notes` properties, getters, setters, and serialization.
- **`InvoiceDao.java` (`src/main/java/com/example/t/dao/InvoiceDao.java`)**:
  - Line 92: `INSERT INTO invoice_items (invoice_id, product_id, quantity, unit_price, line_total) VALUES (?, ?, ?, ?, ?);` does not persist `discount_percent`, `discount_amount`, or `notes`.
  - Line 222: `getInvoiceItems(...)` maps only the initial 8 columns, leaving the new V2 columns unpopulated.

### 1.3 Mathematical and Billing Engine
- **`BillingService.java` (`src/main/java/com/example/t/service/BillingService.java`)**:
  - `OrderItem` (lines 21–59) holds: `productId`, `name`, `category`, `quantity`, `unitPrice`, `sku`.
  - Line calculation: `getLineSubtotal() = roundMoney(unitPrice * quantity)` (line 57).
  - Whole-order calculation (lines 126–152):
    ```java
    double rawSubtotal = 0.0;
    for (OrderItem item : items) {
        rawSubtotal += item.getUnitPrice() * item.getQuantity();
    }
    double subtotal = roundMoney(rawSubtotal);
    double discountAmount = roundMoney(subtotal * (discountPercent / 100.0));
    double taxableAmount = roundMoney(subtotal - discountAmount);
    double taxAmount = roundMoney(taxableAmount * (taxRate / 100.0));
    double netTotal = roundMoney(taxableAmount + taxAmount);
    ```
  - Currently assumes discount applies exclusively at the order/cart level. No item-level discounting compounding logic exists.

### 1.4 PDF Receipt Engine
- **`PdfInvoiceService.java` (`src/main/java/com/example/t/service/PdfInvoiceService.java`)**:
  - Uses OpenPDF 1.3.40 (`com.lowagie.text.*` and `com.lowagie.text.pdf.*`).
  - Supports A4 and 80mm thermal formats (`Rectangle(226.77f, 600f)`).
  - Renders 5-column item table (`Item Description`, `SKU`, `Qty`, `Unit Price`, `Line Total`).
  - Renders full financial breakdown (`Subtotal`, `Discount`, `Taxable Amount`, `Sales Tax / VAT`, `Grand Total`).
  - Contains no method for price-suppressed gift receipts.

---

## 2. Logic Chain

### 2.1 Multi-Cart Park and Resume Architecture

```
                                  [Active Cart in POS]
                                (cartItems: ObservableList)
                                     │            ▲
                 Hold Action (F6)    │            │  Resume Action (F7)
              (Deep Copy Snapshot)   ▼            │  (Restore State)
                                ┌─────────────────────────┐
                                │ ObservableList<ParkedCart>
                                │  - ParkedCart #1 (John) │
                                │  - ParkedCart #2 (Jane) │
                                └─────────────────────────┘
                                          │
                                          ▼
                                UI Badge: "▶ Resume (2) [F7]"
                                Modal Picker on F7 if size > 1
```

#### Step 1: `ParkedCart` Domain Model (`com.example.t.model.ParkedCart`)
To ensure complete state preservation and isolation, `ParkedCart` must be a self-contained domain model:
- **Attributes**:
  - `String cartId`: Unique identifier (e.g. `HOLD-1718029381`, or `UUID.randomUUID().toString()`).
  - `String displayName`: Human-readable label (e.g. `"Hold #1: John Doe (3 items • $45.00)"`).
  - `LocalDateTime parkedAt`: Exact timestamp of park action.
  - `String customerName`: Customer name at time of hold.
  - `String customerPhone`: Customer phone number (optional contact).
  - `List<InvoiceItem> items`: Deep-copied list of line items (independent copies with identical quantity, unit price, line discount, and line notes).
  - `double discountPercent`: Cart-level discount percentage active when parked.
  - `String notes`: Cart-level cashier note (e.g. "Customer stepped out to get wallet").
  - `boolean giftReceipt`: State of gift receipt checkbox.
- **Deep-Copy Isolation Guarantee**:
  `InvoiceItem` contains JavaFX properties. Merely adding active `InvoiceItem` references to `ParkedCart` would cause mutations in active cart to mutate the held cart.
  `ParkedCart` must implement a static copy constructor or cloning method:
  ```java
  public static InvoiceItem copyItem(InvoiceItem src) {
      InvoiceItem copy = new InvoiceItem(
          src.getItemId(), src.getInvoiceId(), src.getProductId(),
          src.getProductName(), src.getSku(), src.getQuantity(),
          src.getUnitPrice(), src.getLineTotal(),
          src.getDiscountPercent(), src.getDiscountAmount(), src.getNotes()
      );
      return copy;
  }
  ```
- **Convenience Methods**:
  - `int getTotalQuantity()`: Sum of `item.getQuantity()` across items.
  - `double getEstimatedSubtotal()`: Sum of `item.getLineTotal()` across items.
  - `String getFormattedTime()`: Formatted as `hh:mm a` (e.g. `02:45 PM`).
  - `String toString()`: Returns `displayName` for clean rendering in JavaFX `ChoiceDialog` or `ComboBox`.

#### Step 2: In-Memory Store in `PosBillingController`
Replace the private inner `HeldOrder` and raw `List<HeldOrder>` with:
```java
private final ObservableList<ParkedCart> parkedCarts = FXCollections.observableArrayList();

public ObservableList<ParkedCart> getParkedCarts() {
    return parkedCarts;
}
```
**Benefits**:
1. Clean programmatic inspection by unit tests.
2. Direct binding to visual controls: `btnRecallOrder.textProperty().bind(...)` or via change listeners.
3. Multiple carts can be held without upper limit.

#### Step 3: F6 / F7 Workflow Execution
- **Hold Cart (`F6`)**:
  1. Guard: If `cartItems.isEmpty()`, display warning: `"Cannot hold an empty cart."`
  2. Snapshot: Clone each item via `ParkedCart.copyItem(item)`.
  3. Extract active metadata: `customerNameField`, `discountPercentField`, notes, and `giftReceiptCheckBox`.
  4. Generate `displayName`: `"Hold #" + (parkedCarts.size() + 1) + ": " + customer + " (" + totalQty + " items)"`.
  5. Add to `parkedCarts.add(new ParkedCart(...))`.
  6. Reset active cart: `cartItems.clear()`, set `customerNameField` to `"Walk-in Customer"`, `discountPercentField` to `"0.0"`, `cashTenderedField` to `"0.00"`.
  7. Recalculate order (`recalculateOrder()`).
  8. Update UI button text: `btnRecallOrder.setText("▶ Resume Cart (" + parkedCarts.size() + ") [F7]")`.
  9. Show notification toast: `"Cart parked as Hold #X. Ready for next customer."`
- **Resume Cart (`F7`)**:
  1. Guard: If `parkedCarts.isEmpty()`, show info: `"No parked carts available."`
  2. Guard: If `!cartItems.isEmpty()`, show warning: `"Active cart not empty. Please hold or complete the current cart before resuming."`
  3. Cart Selection:
     - If `parkedCarts.size() == 1`: Auto-select the single cart (`parkedCarts.remove(0)`).
     - If `parkedCarts.size() > 1`: Display a `ChoiceDialog<ParkedCart>` displaying all parked carts. The cashier selects the desired cart; if cancelled, the operation safely aborts.
     *(Overload `resumeCart(int index)` or `resumeCart(ParkedCart cart)` provided for headless/automated unit testing).*
  4. State Restoration:
     - `cartItems.setAll(cart.getItems().stream().map(ParkedCart::copyItem).toList())`
     - Restore `customerNameField`, `discountPercentField`, and `giftReceiptCheckBox`.
     - Trigger `recalculateOrder()`.
  5. Update UI badge: `btnRecallOrder.setText("▶ Resume Cart (" + parkedCarts.size() + ") [F7]")`.
  6. Notification: `"Restored order for '" + cart.getCustomerName() + "'."`

---

### 2.2 Line-Item Notes & Item-Level Discounts Architecture

#### Step 1: Upgrading `InvoiceItem.java`
Extend `InvoiceItem` with JavaFX properties corresponding to the V2 migration columns:
```java
private final DoubleProperty discountPercent;
private final DoubleProperty discountAmount;
private final StringProperty notes;
```
1. Add full constructor:
   ```java
   public InvoiceItem(int itemId, int invoiceId, int productId, String productName,
                      String sku, int quantity, double unitPrice, double lineTotal,
                      double discountPercent, double discountAmount, String notes)
   ```
2. Retain all existing constructors for 100% backward compatibility, defaulting `discountPercent = 0.0`, `discountAmount = 0.0`, and `notes = ""`.
3. Update `recalculateLineTotal()`:
   ```java
   public void recalculateLineTotal() {
       double raw = getQuantity() * getUnitPrice();
       double disc = roundMoney(raw * (getDiscountPercent() / 100.0));
       setDiscountAmount(disc);
       setLineTotal(roundMoney(raw - disc));
   }
   ```
4. Property setters (`setQuantity`, `setUnitPrice`, `setDiscountPercent`) trigger `recalculateLineTotal()`.

#### Step 2: Database Persistence in `InvoiceDao.java`
- Update `createInvoice(Invoice invoice, List<InvoiceItem> items, Connection conn)`:
  ```java
  String itemSql = "INSERT INTO invoice_items " +
      "(invoice_id, product_id, quantity, unit_price, line_total, discount_percent, discount_amount, notes) " +
      "VALUES (?, ?, ?, ?, ?, ?, ?, ?);";
  // Bind parameters:
  itemStmt.setInt(1, invoiceId);
  itemStmt.setInt(2, item.getProductId());
  itemStmt.setInt(3, item.getQuantity());
  itemStmt.setDouble(4, item.getUnitPrice());
  itemStmt.setDouble(5, item.getLineTotal());
  itemStmt.setDouble(6, item.getDiscountPercent());
  itemStmt.setDouble(7, item.getDiscountAmount());
  itemStmt.setString(8, item.getNotes() != null ? item.getNotes() : "");
  ```
- Update `getInvoiceItems(int invoiceId, Connection conn)`:
  Map `rs.getDouble("discount_percent")`, `rs.getDouble("discount_amount")`, `rs.getString("notes")` into each `InvoiceItem`.

#### Step 3: Compounding & Discount Interaction in `BillingService.java`
How line-item discounts compound with cart-level discounts without double-counting:

```
[Item 1: 1 x $100.00, 20% Line Disc] ──► Line Total: $80.00 (Disc: $20.00)
[Item 2: 1 x $50.00,   0% Line Disc] ──► Line Total: $50.00 (Disc: $0.00)
                                                   ────────
                         Net Subtotal before Cart: $130.00
                                                   │
                10% Cart-Level Discount ───────────┴──► Cart Disc Amt: $13.00
                                                   ────────
                                    Taxable Base: $117.00
                                                   │
                         10% Sales Tax / VAT ──────┴──► Tax Amount:    $11.70
                                                   ────────
                                      Grand Total: $128.70
```

1. **Mathematical Definition**:
   - For each line item $i \in \{1, \dots, n\}$:
     $$\text{grossSubtotal}_i = q_i \times p_i$$
     $$\text{itemDiscountAmount}_i = \text{roundMoney}\left(\text{grossSubtotal}_i \times \frac{d_i}{100.0}\right)$$
     $$\text{lineTotal}_i = \text{roundMoney}(\text{grossSubtotal}_i - \text{itemDiscountAmount}_i)$$
   - Order Net Subtotal:
     $$\text{subtotal} = \text{roundMoney}\left(\sum_{i=1}^n \text{lineTotal}_i\right)$$
   - Cart-Level Discount:
     $$\text{cartDiscountAmount} = \text{roundMoney}\left(\text{subtotal} \times \frac{D_\text{cart}}{100.0}\right)$$
   - Taxable Base:
     $$\text{taxableAmount} = \text{roundMoney}(\text{subtotal} - \text{cartDiscountAmount})$$
   - Tax Amount:
     $$\text{taxAmount} = \text{roundMoney}\left(\text{taxableAmount} \times \frac{T_\text{rate}}{100.0}\right)$$
   - Net Total:
     $$\text{netTotal} = \text{roundMoney}(\text{taxableAmount} + \text{taxAmount})$$
2. **Audit & Customer Savings Guarantee**:
   Total savings granted to customer:
   $$\text{Total Savings} = \left(\sum_{i=1}^n \text{itemDiscountAmount}_i\right) + \text{cartDiscountAmount}$$
   - **Zero Regression**: When all item discounts are 0%, `subtotal` equals gross subtotal, and calculation is 100% identical to legacy behavior.
   - **No Double Discount Bug**: Cart discount is applied to the post-line-discount net price, strictly satisfying the single-application guarantee established in `DiscountCalculationE2ETest.testSingleApplicationGuarantee`.

#### Step 4: UI Interaction in `PosBilling.fxml` & `PosBillingController.java`
- In `PosBilling.fxml`:
  - Add a dedicated `colCartDiscount` (`"Disc %"`, 75px) column to `cartTable`.
  - Add action buttons in the cart toolbar:
    - `<Button fx:id="btnItemDiscount" onAction="#handleItemDiscount" styleClass="chip-btn" text="🏷 Item Disc [F3]" />`
    - `<Button fx:id="btnItemNote" onAction="#handleItemNote" styleClass="chip-btn" text="📝 Note [F5]" />`
- In `PosBillingController.java`:
  - `handleItemDiscount(ActionEvent event)`:
    - Verifies an item in `cartTable` is selected.
    - Displays a `TextInputDialog` pre-filled with current `discountPercent`.
    - Validates input $0.0 \le \text{pct} \le 100.0$.
    - Calls `selectedItem.setDiscountPercent(pct)`.
    - Invokes `recalculateOrder()` and `cartTable.refresh()`.
  - `handleItemNote(ActionEvent event)`:
    - Verifies selection.
    - Displays a `TextInputDialog` pre-filled with current `notes`.
    - Updates `selectedItem.setNotes(noteText)`.
    - Cell factory on `colCartName` displays item name with small secondary subtitle `📝 [note]` when present.

---

### 2.3 Price-Suppressed Gift Receipt Architecture

```
┌────────────────────────────────────────────────────────┐
│               GM Bookstore & Stationery                │
│                 123 Main Street, Suite 4B              │
│                     Phone: 0300 0000000                │
│                                                        │
│                     GIFT RECEIPT                       │
│                   إيصال هدية                           │
│                                                        │
│  Invoice #: INV-101               Customer: Recipient  │
│  Date: 2026-10-21                 Cashier: Register #1 │
│                                                        │
│                 ||| | ||||| || |||||||                 │
│                        INV-101                         │
│                                                        │
│  Item Description                 SKU            Qty   │
│  ────────────────────────────────────────────────────  │
│  Clean Code (JDK 21)              BK-001          1    │
│  Premium Gel Ink Pen (Pack of 3)  ST-002          2    │
│  ────────────────────────────────────────────────────  │
│                                                        │
│    Gift Receipt: Exchangeable within 30 days with      │
│      receipt. Items must be in original condition.     │
│                                                        │
│   Generated by POS • 2026-10-21 14:30:00               │
└────────────────────────────────────────────────────────┘
```

#### Step 1: OpenPDF Layout & Strict Price Suppression
Add to `PdfInvoiceService.java`:
```java
public Path generateGiftReceipt(Invoice invoice, List<InvoiceItem> items, StoreSettings settings) throws IOException {
    String filename = "gift_receipt_" + (invoice.getInvoiceId() > 0 ? invoice.getInvoiceId() : System.currentTimeMillis()) + ".pdf";
    Path targetPath = getInvoicesDirectory().resolve(filename);
    return generateGiftReceipt(invoice, items, settings, false, targetPath);
}

public Path generateGiftReceipt(Invoice invoice, List<InvoiceItem> items, StoreSettings settings,
                               boolean thermal80mm, Path targetFile) throws IOException
```

#### Step 2: Suppression & Display Matrix

| Element | Standard Invoice | Gift Receipt | Suppression Rule |
|---|---|---|---|
| **Store Header** | Store Name, Address, Phone | Store Name, Address, Phone | Retained for store identification |
| **Title** | `TAX INVOICE` / `فاتورة ضريبية` | `GIFT RECEIPT` / `إيصال هدية` | Distinct title; indicates gift exchange status |
| **Invoice Meta** | Invoice #, Date, Payment Method | Invoice #, Date, Customer | Retained for transaction cross-reference |
| **Vector Barcode** | Optional / Text | **Prominent Code-128 Barcode** | Rendered via OpenPDF `Barcode128` of invoice number |
| **Items Table** | 5 cols (`Item, SKU, Qty, Price, Total`) | **3 cols (`Item Description, SKU, Quantity`)** | **Unit Price and Line Total columns completely removed** |
| **Financial Summary** | Subtotal, Discounts, Taxable, Tax, Grand Total | **NONE** | **Table omitted entirely. Zero currency symbols or dollar amounts** |
| **Payment Breakdown** | Cash, Card, Change Due | **NONE** | **Suppressed completely** |
| **Exchange Policy** | Standard return terms (7 days) | **30-day Gift Exchange Policy** | "Gift Receipt: Exchangeable within 30 days with receipt. Items must be in original condition." |

#### Step 3: Vector Barcode Generation via OpenPDF `Barcode128`
Using native OpenPDF capabilities without third-party dependencies:
```java
try {
    Barcode128 barcode = new Barcode128();
    barcode.setCode(invNum);
    barcode.setCodeType(Barcode128.CODE128);
    barcode.setFont(null); // Bars only; number printed cleanly below
    com.lowagie.text.Image barcodeImage = barcode.createImageWithBarcode(writer.getDirectContent(), null, null);
    barcodeImage.setAlignment(Element.ALIGN_CENTER);
    barcodeImage.scalePercent(thermal80mm ? 90f : 110f);
    barcodeImage.setSpacingBefore(thermal80mm ? 6f : 10f);
    barcodeImage.setSpacingAfter(thermal80mm ? 6f : 10f);
    document.add(barcodeImage);
} catch (Exception e) {
    Logger.warn("Barcode rendering fallback: " + e.getMessage());
}
```

#### Step 4: UI Trigger in POS
- Add `<CheckBox fx:id="giftReceiptCheckBox" text="🎁 Print Gift Receipt [F11]" />` to `PosBilling.fxml`.
- In `handleCheckout`:
  ```java
  if (giftReceiptCheckBox != null && giftReceiptCheckBox.isSelected() && pdfInvoiceService != null) {
      try {
          Path giftPdf = pdfInvoiceService.generateGiftReceipt(invoice, itemsToSave, settings);
          NotificationUtil.showInfo("Gift Receipt Generated", "Gift receipt saved to: " + giftPdf.getFileName());
      } catch (IOException e) {
          NotificationUtil.showWarning("Gift Receipt Warning", "Failed to generate gift receipt: " + e.getMessage());
      }
  }
  ```

---

### 2.4 Unit Testing Strategy

```
                                [Unit & E2E Test Suite]
                                          │
       ┌──────────────────────────────────┼──────────────────────────────────┐
       ▼                                  ▼                                  ▼
[ParkedCartTest.java]            [GiftReceiptTest.java]           [LineDiscountNotesTest.java]
• Model immutability             • %PDF- Header & size            • Item discount % math
• Deep-copy snapshot isolation   • Zero price/currency leak       • Compounding with cart disc
• F6 park workflow & field reset • Code-128 barcode check         • Note preservation in SQLite
• F7 multi-cart resume selection • 30-day policy text assert      • TableView refresh validation
```

#### 1. `ParkedCartTest.java` (`src/test/java/com/example/t/ParkedCartTest.java`)
- `testParkedCartModelGettersAndDisplay()`: Verify model field storage and formatted label string.
- `testSnapshotDeepCopyIsolation()`: Mutating an `InvoiceItem` in the source list after creating a `ParkedCart` must not alter items inside `ParkedCart`.
- `testControllerHoldCartEmptiesCartAndIncrementsBadge()`: Calling `handleHoldCart` clears `cartItems`, creates a `ParkedCart`, and updates recall count badge.
- `testControllerHoldEmptyCartBlocked()`: Verify guard prevents holding cart when `cartItems.isEmpty()`.
- `testControllerResumeRestoresExactItemsAndCustomer()`: Verify all items, quantities, prices, discounts, and customer name are restored into active cart.
- `testResumeBlockedWhenActiveCartNotEmpty()`: Verify guard protects against overwriting an active cart.
- `testMultiCartHoldingAndSelection()`: Hold 3 carts successively; verify `parkedCarts.size() == 3` and selective recall by cart ID/index.

#### 2. `GiftReceiptTest.java` (`src/test/java/com/example/t/GiftReceiptTest.java`)
- `testGiftReceiptPdfHeaderAndCreation()`: Validates `%PDF-` magic header and file existence.
- `testPriceSuppressionVerification()`:
  - Generates gift receipt for an invoice with subtotal $120.00, discount $20.00, tax $8.25, and total $108.25.
  - Extracts text from generated PDF using OpenPDF `PdfTextExtractor`:
    ```java
    PdfReader reader = new PdfReader(giftPdf.toString());
    PdfTextExtractor extractor = new PdfTextExtractor(reader);
    String text = extractor.getTextFromPage(1);
    reader.close();
    ```
  - Asserts that text **does NOT contain**: `"$"`, `"£"`, `"€"`, `"120.00"`, `"20.00"`, `"108.25"`, `"Subtotal"`, `"Grand Total"`, `"Unit Price"`, `"Line Total"`, `"Sales Tax"`, `"VAT"`.
- `testMandatoryGiftReceiptElements()`:
  - Asserts that text **contains**: `"GIFT RECEIPT"`, Store Name, Invoice Number (`"INV-"`), product descriptions, quantities, and `"Exchangeable within 30 days with receipt"`.
- `testGiftReceipt80mmThermal()`: Generates 80mm format and validates layout constraints.
- `testBilingualGiftReceipt()`: Applies `RegionalPreset.GULF_UAE`, verifies Arabic `"إيصال هدية"` is present.

#### 3. `LineDiscountNotesTest.java` (`src/test/java/com/example/t/LineDiscountNotesTest.java`)
- `testLineItemDiscountMath()`: Verify item with qty 2 @ $50 with 15% discount calculates line total $85.00 and discount amount $15.00.
- `testLineDiscountCompoundsCleanlyWithCartDiscount()`: Verify item discounts reduce the taxable subtotal before cart discount, ensuring zero double-discounting.
- `testInvoiceDaoPersistsLineDiscountAndNotes()`: Save invoice with item discount and note `"First Edition"`; retrieve from database and assert values match.

---

## 3. Caveats

1. **JavaFX Thread Concurrency & Dialogs in Headless Test Harness**:
   - `TextInputDialog` and `ChoiceDialog` call `showAndWait()`, which can block when executed in automated headless CI test runs (e.g. TestFX / Monocle / `Platform.startup()`).
   - **Mitigation**: Expose programmatic controller methods:
     - `public void parkCurrentCart(String note)`
     - `public boolean resumeParkedCart(ParkedCart cart)`
     - `public boolean resumeParkedCartByIndex(int index)`
     - `public void applyItemDiscount(InvoiceItem item, double percent)`
     - `public void setItemNote(InvoiceItem item, String note)`
     The GUI action handlers (`handleHoldCart`, `handleRecallCart`, `handleItemDiscount`, `handleItemNote`) simply wrap these programmatic methods with UI dialogs. This allows 100% test coverage without UI modal hangs.
2. **OpenPDF Barcode Sizing on 80mm Thermal Receipt**:
   - 80mm thermal receipts have a narrow printable width (~202pt). An overly dense Code-128 barcode for long invoice numbers (`INV-1718029381923`) can overflow the margin if scaled $> 100\%$.
   - **Mitigation**: Scale barcode image to $85\%$–$90\%$ on 80mm thermal layouts and center align.
3. **Arabic Font Rendering in Gift Receipts**:
   - OpenPDF requires TTF font resolution with `BaseFont.IDENTITY_H` for Arabic text. `PdfInvoiceService` already contains `resolveFont(...)` with system Arabic font lookup (`Arial`, `Tahoma`, `Segoe UI`), which must be reused for the bilingual gift receipt header (`"إيصال هدية"`).
4. **Active Cart Recall Conflict Handling**:
   - If a cashier presses F7 while an active cart has items, popping a held cart could erase the customer's cart. A prompt or warning dialog must guard this action.

---

## 4. Conclusion

Milestone 1.1-C (Park/Resume, Line Notes, Discounts & Gift Receipts) has a clean, robust, and mathematically sound path forward:
1. **Multi-Cart Park/Resume**: Introduce `com.example.t.model.ParkedCart` with deep-copy item cloning, upgrade `PosBillingController` to use `ObservableList<ParkedCart>`, wire F6 (Hold) and F7 (Resume) with visual badge updates and multi-cart picker.
2. **Line Notes & Discounts**: Equip `InvoiceItem.java` with properties for `discountPercent`, `discountAmount`, and `notes`, update `InvoiceDao` to persist into the V2 migration columns, and implement pure compound discount math in `BillingService`.
3. **Price-Suppressed Gift Receipt**: Implement `PdfInvoiceService.generateGiftReceipt(...)` with Code-128 vector barcode, complete omission of prices/currency/taxes, 30-day exchange policy, and POS checkout trigger (F11).
4. **Unit Tests**: Full test suite (`ParkedCartTest.java`, `GiftReceiptTest.java`, `LineDiscountNotesTest.java`) ensuring 100% behavioral and empirical verification.

---

## 5. Verification Method

### 5.1 Compilation and Verification Commands
- Compile test code:
  ```powershell
  .\mvnw.cmd test-compile
  ```
- Run targeted POS and billing unit test suites:
  ```powershell
  .\mvnw.cmd test -Dtest=ParkedCartTest,GiftReceiptTest,LineDiscountNotesTest,PosBillingControllerTest,BillingServiceTest
  ```
- Run full regression test suite:
  ```powershell
  .\mvnw.cmd test
  ```

### 5.2 Key Files and Artifacts to Inspect
- `src/main/java/com/example/t/model/ParkedCart.java` (New domain model)
- `src/main/java/com/example/t/model/InvoiceItem.java` (Properties for discount %/amt and notes)
- `src/main/java/com/example/t/dao/InvoiceDao.java` (Persistence of V2 line item columns)
- `src/main/java/com/example/t/service/BillingService.java` (Compound discount calculations)
- `src/main/java/com/example/t/service/PdfInvoiceService.java` (Gift receipt generation)
- `src/main/java/com/example/t/controller/PosBillingController.java` (F6, F7, F3, F5, F11 actions and state)
- `src/main/resources/com/example/t/fxml/PosBilling.fxml` (Cart table columns, discount & note buttons, gift checkbox)
- `src/test/java/com/example/t/ParkedCartTest.java` (Hold/resume test suite)
- `src/test/java/com/example/t/GiftReceiptTest.java` (Price suppression & barcode test suite)

### 5.3 Invalidation Conditions
- Any price, total, tax rate, or currency symbol appearing on the generated gift receipt PDF.
- Holding a cart resulting in shared item references that mutate when active cart is altered.
- Cart-level discount being applied to gross prices rather than net prices after line-item discounts (violating single-application guarantee).
- Inability to hold more than 1 cart or select between multiple held carts.
