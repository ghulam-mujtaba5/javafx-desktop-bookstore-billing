# Milestone 1.1-C Investigation Report: Split Tender Payments & Billing Persistence

## 1. Observation

### 1.1 Existing Data Models and Enums
- **`PaymentType.java` (`src/main/java/com/example/t/model/PaymentType.java:6-39`)**:
  - Enums: `CASH("Cash")`, `CARD("Card / Electronic")`, `STORE_CREDIT("Store Credit / Voucher")`.
  - Provides `fromString(String text)` with fuzzy fallback mapping:
    - `"CREDIT"` + `"STORE"` $\rightarrow$ `STORE_CREDIT`
    - `"CARD"` or `"DEBIT"` or `"CREDIT"` $\rightarrow$ `CARD`
    - Empty or default $\rightarrow$ `CASH`

- **`PaymentTender.java` (`src/main/java/com/example/t/model/PaymentTender.java:6-95`)**:
  - Encapsulates:
    ```java
    private PaymentType paymentType;
    private double amount;          // Amount applied toward the invoice balance
    private double tenderedAmount;  // Amount handed by customer (e.g. $50 on $35 balance)
    private double changeDue;       // Change returned (e.g. $15)
    private String referenceInfo;   // Optional card auth #, voucher code, notes
    ```
  - Includes constructors:
    - `PaymentTender(PaymentType, double amount)`
    - `PaymentTender(PaymentType, double amount, double tenderedAmount, double changeDue)`
    - `PaymentTender(PaymentType, double amount, double tenderedAmount, double changeDue, String referenceInfo)`
    - `PaymentTender(String, double, double, double, String)`

- **`InvoicePayment.java` (`src/main/java/com/example/t/model/InvoicePayment.java:8-122`)**:
  - Model mapped to SQLite table `invoice_payments`:
    - `paymentId` (PK), `invoiceId` (FK), `paymentType` (TEXT), `amount` (REAL), `tenderedAmount` (REAL), `changeDue` (REAL), `referenceInfo` (TEXT), `createdAt` (Timestamp).
  - Provides `toPaymentTender()` conversion method (line 107).

### 1.2 Existing Database Schema and Migration
- **`V2__v1_1_enhancements.sql` (`src/main/resources/com/example/t/db/V2__v1_1_enhancements.sql:28-41`)**:
  - Created table and index:
    ```sql
    CREATE TABLE IF NOT EXISTS invoice_payments (
        payment_id INTEGER PRIMARY KEY AUTOINCREMENT,
        invoice_id INTEGER NOT NULL REFERENCES invoices(invoice_id) ON DELETE CASCADE,
        payment_type TEXT NOT NULL,
        amount REAL NOT NULL,
        tendered_amount REAL DEFAULT 0.0,
        change_due REAL DEFAULT 0.0,
        reference_info TEXT,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
    CREATE INDEX IF NOT EXISTS idx_invoice_payments_invoice ON invoice_payments(invoice_id);
    ```
  - Migration is active and verified by `SchemaMigrationV2Test` and `SchemaMigrationV2AdversarialTest`.

- **`InvoicePaymentDao.java` (`src/main/java/com/example/t/dao/InvoicePaymentDao.java:14-158`)**:
  - Already implemented with transactional methods:
    - `savePayment(InvoicePayment payment, Connection conn)`
    - `savePayments(int invoiceId, List<InvoicePayment> payments, Connection conn)` (uses `pstmt.addBatch()` and `pstmt.executeBatch()`)
    - `getPaymentsByInvoiceId(int invoiceId, Connection conn)`
    - `getTotalPaidForInvoice(int invoiceId, Connection conn)`
    - `deletePaymentsByInvoiceId(int invoiceId, Connection conn)`
  - Verified by `InvoicePaymentDaoTest`: 3 tests run, 0 failures (verified via `mvnw.cmd test -Dtest=InvoicePaymentDaoTest` in 1.2s).

### 1.3 Existing Billing Calculation Engine
- **`BillingService.java` (`src/main/java/com/example/t/service/BillingService.java:61-152`)**:
  - Provides `calculate(List<OrderItem> items, double discountPercent, double taxRate, double cashTendered)`:
    - Line 132: `subtotal = roundMoney(rawSubtotal)`
    - Line 135: `discountAmount = roundMoney(subtotal * (discountPercent / 100.0))`
    - Line 138: `taxableAmount = roundMoney(subtotal - discountAmount)`
    - Line 141: `taxAmount = roundMoney(taxableAmount * (taxRate / 100.0))`
    - Line 144: `netTotal = roundMoney(taxableAmount + taxAmount)`
    - Line 147: `changeDue = roundMoney(cashTendered - netTotal)`
  - Only models a single cash tender (`cashTendered`). Does not support:
    - Multiple tenders (split across Cash, Card, Store Credit)
    - Tracking remaining unpaid balance
    - Differentiating cash change vs card non-cashback rules

### 1.4 Existing `InvoiceDao` Persistence
- **`InvoiceDao.java` (`src/main/java/com/example/t/dao/InvoiceDao.java:31-115`)**:
  - `createInvoice(Invoice invoice, List<InvoiceItem> items, Connection conn)`:
    - Line 37-49: Decrements product stock in `products` table.
    - Line 63-67: Inserts into `invoices` with hardcoded defaults:
      ```java
      String invSql = "INSERT INTO invoices (invoice_number, customer_name, invoice_date, subtotal, " +
              "discount_percent, discount_amount, tax_rate, tax_amount, net_total, cash_tendered, " +
              "change_due, payment_method, currency_code, region) " +
              "VALUES (?, ?, ?, ?, ?, ?, 0.0, 0.0, ?, ?, 0.0, 'CASH', 'USD', 'USA');";
      ```
    - Line 91-105: Inserts line items into `invoice_items`.
    - **Gap**: Does NOT persist payment tenders into `invoice_payments`, hardcodes `payment_method='CASH'`, `tax_rate=0.0`, `tax_amount=0.0`, `change_due=0.0`.

### 1.5 Existing POS Billing Controller & UI
- **`PosBillingController.java` (`src/main/java/com/example/t/controller/PosBillingController.java:518-580`)**:
  - Reads single tender from `paymentMethodCombo` and `cashTenderedField` (lines 524-527).
  - Validates `cashTendered >= currentNetTotal` if payment method is "Cash" (lines 528-533).
  - Calls `invoiceDao.createInvoice(invoice, itemsToSave)` (line 550).
  - Lacks split tender collection, validation, or dialog dispatch.
- **`PosBilling.fxml` (`src/main/resources/com/example/t/fxml/PosBilling.fxml:199-230`)**:
  - UI only contains a single `ComboBox paymentMethodCombo`, single `TextField cashTenderedField`, quick cash buttons (+10, +20, etc.), and `changeDueLabel`.

---

## 2. Logic Chain

### 2.1 Split Tender Payment Math & Validation

#### Mathematical Formalism
Let $N$ be the order Net Total (after all line items, line discounts, cart discounts, and regional taxes have been rounded to 2 decimal places using `RoundingMode.HALF_UP`).

A split payment is defined as an ordered sequence of $M$ payment tenders:
$$T = \langle T_1, T_2, \dots, T_M \rangle$$
Where each tender $T_k$ has:
- `paymentType` $\in \{\text{CASH}, \text{CARD}, \text{STORE\_CREDIT}\}$
- `tenderedAmount` $t_k > 0.00$
- `amount` (applied) $a_k \ge 0.00$
- `changeDue` $c_k \ge 0.00$
- `referenceInfo` (optional string, e.g. card transaction auth code or gift voucher #)

Let $R_k$ be the remaining balance due immediately before tender $T_k$ is applied, with initial state $R_0 = N$.

#### 1. Cash Tender Rules (`PaymentType.CASH`)
Cash is currency-in-hand and may exceed the remaining balance, producing change.
- Case 1: Over-tender ($t_k \ge R_k$):
  $$a_k = R_k$$
  $$c_k = \text{roundMoney}(t_k - R_k)$$
  $$R_{k+1} = 0.00$$
  *Example*: Customer owes $R_k = \$35.00$ remaining balance, tenders $t_k = \$50.00$ cash.
  $\rightarrow a_k = \$35.00$, $c_k = \$15.00$, $R_{k+1} = \$0.00$.
- Case 2: Under-tender / Partial cash ($0 < t_k < R_k$):
  $$a_k = t_k$$
  $$c_k = 0.00$$
  $$R_{k+1} = \text{roundMoney}(R_k - t_k)$$
  *Example*: Customer owes $R_k = \$100.00$, tenders $t_k = \$20.00$ cash.
  $\rightarrow a_k = \$20.00$, $c_k = \$0.00$, $R_{k+1} = \$80.00$.

#### 2. Card / Electronic & Store Credit Rules (`CARD`, `STORE_CREDIT`)
In retail POS accounting, non-cash payment gateways and store vouchers cannot give cash change (no cashback on credit card or voucher).
- Strict Rule: $0 < t_k \le R_k$.
- If $t_k > R_k$: Rejection condition!
  $$\text{throw IllegalArgumentException}(type + \text{ tender ("} + t_k + \text{") cannot exceed remaining balance of "} + R_k + \text{". Cashback is not permitted on non-cash tenders."})$$
- When valid ($0 < t_k \le R_k$):
  $$a_k = t_k$$
  $$c_k = 0.00$$
  $$R_{k+1} = \text{roundMoney}(R_k - t_k)$$
  *Example*: Customer owes $R_k = \$35.00$. Customer presents card for $\$35.00$.
  $\rightarrow a_k = \$35.00$, $c_k = \$0.00$, $R_{k+1} = \$0.00$.

#### 3. Aggregate Split Calculations & Checkout Invariant
After all $M$ tenders are applied:
1. Total Applied Amount:
   $$A = \sum_{k=1}^M a_k$$
2. Total Cash Tendered:
   $$T_\text{cash} = \sum_{k: \text{type}_k = \text{CASH}} t_k$$
3. Total Change Due:
   $$C = \sum_{k=1}^M c_k = \sum_{k: \text{type}_k = \text{CASH}} c_k$$
4. Final Remaining Balance:
   $$R_\text{final} = \text{roundMoney}(\max(0.0, N - A))$$
5. Checkout Validity Invariant:
   A transaction can ONLY be completed if:
   $$R_\text{final} == 0.00 \quad (\text{or } |R_\text{final}| < 0.001)$$
   If $R_\text{final} > 0.00$, checkout is blocked with an error:
   `"Cannot complete checkout: remaining balance of $X.XX must be settled."`

#### 4. Proposed `BillingService` API
Add the following static class and methods to `BillingService.java`:
```java
public static class SplitTenderResult {
    private final double netTotal;
    private final double totalApplied;
    private final double totalCashTendered;
    private final double totalChangeDue;
    private final double remainingBalance;
    private final boolean fullyPaid;
    private final List<PaymentTender> tenders;

    public SplitTenderResult(double netTotal, double totalApplied, double totalCashTendered,
                             double totalChangeDue, double remainingBalance, List<PaymentTender> tenders) {
        this.netTotal = netTotal;
        this.totalApplied = totalApplied;
        this.totalCashTendered = totalCashTendered;
        this.totalChangeDue = totalChangeDue;
        this.remainingBalance = remainingBalance;
        this.fullyPaid = remainingBalance <= 0.0001 && totalApplied >= netTotal - 0.0001;
        this.tenders = Collections.unmodifiableList(new ArrayList<>(tenders));
    }

    public double getNetTotal() { return netTotal; }
    public double getTotalApplied() { return totalApplied; }
    public double getTotalCashTendered() { return totalCashTendered; }
    public double getTotalChangeDue() { return totalChangeDue; }
    public double getRemainingBalance() { return remainingBalance; }
    public boolean isFullyPaid() { return fullyPaid; }
    public List<PaymentTender> getTenders() { return tenders; }
}

public static SplitTenderResult calculateSplitTender(double netTotal, List<PaymentTender> rawTenders) {
    if (netTotal < 0.0) {
        throw new IllegalArgumentException("Net total cannot be negative: " + netTotal);
    }
    double remaining = roundMoney(netTotal);
    double totalApplied = 0.0;
    double totalCashTendered = 0.0;
    double totalChangeDue = 0.0;
    List<PaymentTender> processed = new ArrayList<>();

    if (rawTenders != null) {
        for (PaymentTender t : rawTenders) {
            if (t == null) continue;
            double tendered = roundMoney(t.getTenderedAmount());
            if (tendered <= 0.0) {
                throw new IllegalArgumentException("Tender amount must be greater than zero: " + tendered);
            }

            PaymentType type = t.getPaymentType() != null ? t.getPaymentType() : PaymentType.CASH;
            double applied;
            double change;

            if (type == PaymentType.CASH) {
                totalCashTendered = roundMoney(totalCashTendered + tendered);
                if (tendered >= remaining) {
                    applied = remaining;
                    change = roundMoney(tendered - remaining);
                    remaining = 0.0;
                } else {
                    applied = tendered;
                    change = 0.0;
                    remaining = roundMoney(remaining - tendered);
                }
                totalChangeDue = roundMoney(totalChangeDue + change);
            } else {
                // CARD or STORE_CREDIT: cannot exceed remaining balance
                if (tendered > remaining + 0.0001) {
                    throw new IllegalArgumentException(String.format(
                            "%s tender (%.2f) cannot exceed remaining balance of %.2f",
                            type.getDisplayName(), tendered, remaining));
                }
                applied = tendered;
                change = 0.0;
                remaining = roundMoney(remaining - applied);
            }

            totalApplied = roundMoney(totalApplied + applied);
            processed.add(new PaymentTender(type, applied, tendered, change, t.getReferenceInfo()));
        }
    }

    return new SplitTenderResult(roundMoney(netTotal), totalApplied, totalCashTendered,
            totalChangeDue, remaining, processed);
}

public static PaymentTender createTender(PaymentType type, double tenderedAmount, double currentRemaining, String ref) {
    if (tenderedAmount <= 0.0) {
        throw new IllegalArgumentException("Tender amount must be positive: " + tenderedAmount);
    }
    double roundedTendered = roundMoney(tenderedAmount);
    double roundedRemaining = roundMoney(currentRemaining);

    if (type == PaymentType.CASH) {
        double applied = roundMoney(Math.min(roundedTendered, roundedRemaining));
        double change = roundMoney(Math.max(0.0, roundedTendered - roundedRemaining));
        return new PaymentTender(PaymentType.CASH, applied, roundedTendered, change, ref);
    } else {
        if (roundedTendered > roundedRemaining + 0.0001) {
            throw new IllegalArgumentException(String.format(
                    "%s tender ($%.2f) exceeds remaining balance of $%.2f",
                    type.getDisplayName(), roundedTendered, roundedRemaining));
        }
        return new PaymentTender(type, roundedTendered, roundedTendered, 0.0, ref);
    }
}
```

---

### 2.2 Persistence Integration in `InvoiceDao`

#### Transactional Mechanics
All modifications must occur inside a single atomic SQLite transaction on one `Connection`:
1. `conn.setAutoCommit(false);`
2. Decrement inventory counts in `products` (fail immediately if insufficient stock).
3. Insert invoice header in `invoices` with generated `invoice_id`.
4. Insert line items into `invoice_items` using batch execution.
5. Insert payment tenders into `invoice_payments` using batch execution (`InvoicePaymentDao.savePayments`).
6. `conn.commit();`
7. On any exception: `conn.rollback();` in catch block.

#### Backward Compatibility & Header Column Mapping
The `invoices` table columns must be mapped as follows when payments are recorded:

| `invoices` Column | Single Tender (Cash) | Single Tender (Card / Credit) | Multiple Tenders (Split) | Legacy Caller (`payments == null`) |
|---|---|---|---|---|
| `payment_method` | `'CASH'` | `'CARD'` or `'STORE_CREDIT'` | `'SPLIT'` | `'CASH'` (or `invoice.getPaymentMethod()`) |
| `cash_tendered` | `tenderedAmount` | `0.00` | $\sum_{k \in \text{CASH}} t_k$ | `invoice.getCashTendered()` (or `netPrice`) |
| `change_due` | `changeDue` | `0.00` | $\sum_{k \in \text{CASH}} c_k$ | `invoice.getChangeDue()` (or `0.00`) |
| `net_total` | `invoice.getNetPrice()` | `invoice.getNetPrice()` | `invoice.getNetPrice()` | `invoice.getNetPrice()` |
| `tax_rate` | from invoice/calc | from invoice/calc | from invoice/calc | `0.00` |
| `tax_amount` | from invoice/calc | from invoice/calc | from invoice/calc | `0.00` |
| `currency_code` | from settings | from settings | from settings | `'USD'` |
| `region` | from settings | from settings | from settings | `'USA'` |

#### Automated Default Tender for Legacy Invoices
If a caller uses the legacy method `createInvoice(Invoice invoice, List<InvoiceItem> items)` without supplying a payments list:
- `InvoiceDao` automatically synthesizes a single default `InvoicePayment`:
  ```java
  InvoicePayment defaultPayment = new InvoicePayment(
      invoiceId,
      invoice.getPaymentMethod() != null ? invoice.getPaymentMethod() : "CASH",
      invoice.getNetPrice(),
      invoice.getCashTendered() > 0 ? invoice.getCashTendered() : invoice.getNetPrice(),
      invoice.getChangeDue(),
      "Automatic single tender persistence"
  );
  ```
- This guarantees that 100% of invoices stored in SQLite have corresponding `invoice_payments` child records, making reporting, reconciliation, and audit queries completely uniform!

#### Method Overload Signatures in `InvoiceDao.java`
```java
// Existing 2-arg method: preserved for 100% backward compatibility
public int createInvoice(Invoice invoice, List<InvoiceItem> items) throws SQLException {
    return createInvoice(invoice, items, (List<InvoicePayment>) null);
}

public int createInvoice(Invoice invoice, List<InvoiceItem> items, Connection conn) throws SQLException {
    return createInvoice(invoice, items, (List<InvoicePayment>) null, conn);
}

// New overloaded methods supporting split tender persistence
public int createInvoice(Invoice invoice, List<InvoiceItem> items, List<InvoicePayment> payments) throws SQLException {
    try (Connection conn = DatabaseManager.getConnection()) {
        return createInvoice(invoice, items, payments, conn);
    }
}

public int createInvoice(Invoice invoice, List<InvoiceItem> items, List<InvoicePayment> payments, Connection conn) throws SQLException {
    boolean autoCommit = conn.getAutoCommit();
    conn.setAutoCommit(false);
    try {
        // 1. Atomically decrement stock
        if (items != null) {
            String decSql = "UPDATE products SET stock_quantity = stock_quantity - ?, updated_at = CURRENT_TIMESTAMP " +
                    "WHERE product_id = ? AND stock_quantity >= ?;";
            try (PreparedStatement decStmt = conn.prepareStatement(decSql)) {
                for (InvoiceItem item : items) {
                    decStmt.setInt(1, item.getQuantity());
                    decStmt.setInt(2, item.getProductId());
                    decStmt.setInt(3, item.getQuantity());
                    int updated = decStmt.executeUpdate();
                    if (updated == 0) {
                        throw new SQLException("Insufficient stock for product ID: " + item.getProductId());
                    }
                }
            }
        }

        // 2. Resolve payment method, cash tendered, and change due
        String paymentMethod;
        double cashTendered;
        double changeDue;

        if (payments != null && !payments.isEmpty()) {
            if (payments.size() > 1) {
                paymentMethod = "SPLIT";
            } else {
                paymentMethod = payments.get(0).getPaymentType() != null ? payments.get(0).getPaymentType().toUpperCase() : "CASH";
            }
            cashTendered = payments.stream()
                    .filter(p -> "CASH".equalsIgnoreCase(p.getPaymentType()))
                    .mapToDouble(InvoicePayment::getTenderedAmount)
                    .sum();
            changeDue = payments.stream()
                    .filter(p -> "CASH".equalsIgnoreCase(p.getPaymentType()))
                    .mapToDouble(InvoicePayment::getChangeDue)
                    .sum();
        } else {
            paymentMethod = invoice.getPaymentMethod() != null ? invoice.getPaymentMethod() : "CASH";
            cashTendered = invoice.getCashTendered() > 0 ? invoice.getCashTendered() : invoice.getNetPrice();
            changeDue = invoice.getChangeDue();
        }

        // 3. Insert invoice header
        int invoiceId;
        String invNum = "INV-" + System.currentTimeMillis();
        String customer = invoice.getCustomerName() != null && !invoice.getCustomerName().trim().isEmpty()
                ? invoice.getCustomerName().trim() : "Walk-in Customer";
        LocalDate invDate = invoice.getInvoiceDate() != null ? invoice.getInvoiceDate() : LocalDate.now();
        String dateStr = invDate.toString() + " 12:00:00";

        String invSql = "INSERT INTO invoices (invoice_number, customer_name, invoice_date, subtotal, " +
                "discount_percent, discount_amount, tax_rate, tax_amount, net_total, cash_tendered, " +
                "change_due, payment_method, currency_code, region) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

        try (PreparedStatement invStmt = conn.prepareStatement(invSql, Statement.RETURN_GENERATED_KEYS)) {
            invStmt.setString(1, invNum);
            invStmt.setString(2, customer);
            invStmt.setString(3, dateStr);
            invStmt.setDouble(4, invoice.getOrderTotal());
            invStmt.setDouble(5, invoice.getDiscountPercentage());
            invStmt.setDouble(6, invoice.getDiscount());
            invStmt.setDouble(7, invoice.getTaxRate());
            invStmt.setDouble(8, invoice.getTaxAmount());
            invStmt.setDouble(9, invoice.getNetPrice());
            invStmt.setDouble(10, cashTendered);
            invStmt.setDouble(11, changeDue);
            invStmt.setString(12, paymentMethod);
            invStmt.setString(13, invoice.getCurrencyCode() != null ? invoice.getCurrencyCode() : "USD");
            invStmt.setString(14, invoice.getRegion() != null ? invoice.getRegion() : "USA");
            invStmt.executeUpdate();

            try (ResultSet rs = invStmt.getGeneratedKeys()) {
                if (rs.next()) {
                    invoiceId = rs.getInt(1);
                    invoice.setInvoiceId(invoiceId);
                    invoice.setOrderNum(invoiceId);
                } else {
                    throw new SQLException("Failed to obtain generated invoice ID.");
                }
            }
        }

        // 4. Insert line items
        if (items != null && !items.isEmpty()) {
            String itemSql = "INSERT INTO invoice_items (invoice_id, product_id, quantity, unit_price, line_total) " +
                    "VALUES (?, ?, ?, ?, ?);";
            try (PreparedStatement itemStmt = conn.prepareStatement(itemSql)) {
                for (InvoiceItem item : items) {
                    itemStmt.setInt(1, invoiceId);
                    itemStmt.setInt(2, item.getProductId());
                    itemStmt.setInt(3, item.getQuantity());
                    itemStmt.setDouble(4, item.getUnitPrice());
                    itemStmt.setDouble(5, item.getLineTotal());
                    itemStmt.addBatch();
                }
                itemStmt.executeBatch();
            }
        }

        // 5. Insert invoice payments
        InvoicePaymentDao paymentDao = new InvoicePaymentDao();
        if (payments != null && !payments.isEmpty()) {
            paymentDao.savePayments(invoiceId, payments, conn);
        } else {
            InvoicePayment defaultPayment = new InvoicePayment(invoiceId, paymentMethod,
                    invoice.getNetPrice(), cashTendered, changeDue, "Default Single Tender");
            paymentDao.savePayment(defaultPayment, conn);
        }

        conn.commit();
        return invoiceId;
    } catch (SQLException e) {
        conn.rollback();
        throw e;
    } finally {
        conn.setAutoCommit(autoCommit);
    }
}
```

---

### 2.3 UI Integration Design in POS Billing

#### Architecture: Dedicated `SplitTenderDialog` vs Inline Controls
1. **The POS Ergonomics Challenge**:
   - 90% of transactions are simple single-tender payments (100% Cash or 100% Card). Replacing the main POS checkout panel with a complex tender table adds cognitive load and mouse clicks to routine checkouts.
   - For split payments (10% of transactions), the cashier needs an uncluttered view of remaining balance, tender history, card reference numbers, and quick cash options.
2. **Winning Design**:
   - Keep the quick single payment controls in `PosBilling.fxml` (Payment Method ComboBox with "Cash", "Card", "Store Credit", and "Split Payment...").
   - Add a dedicated `[ ⚡ Split Payment (F8) ]` button directly in the payment panel.
   - Selecting "Split Payment..." in the ComboBox OR clicking `[ Split Payment (F8) ]` OR pressing `F8` anywhere on the POS screen opens `SplitTenderDialog`.

#### Visual Layout of `SplitTenderDialog`
The dialog is styled with AtlantaFX Fluent styling:
```
+-----------------------------------------------------------------------------------------+
|                                  SPLIT TENDER PAYMENT                                   |
+-----------------------------------------------------------------------------------------+
| [ Total Order: $100.00 ]  [ Total Paid: $45.00 ]  [ REMAINING: $55.00 ]  [ Change: $0 ] |
+-----------------------------------------------------------------------------------------+
| Applied Payment Tenders:                                                                |
| +----+----------------------+-----------------+---------------+------------+----------+ |
| | #  | Tender Type          | Amount Applied  | Tendered      | Change Due | Ref Info | |
| +----+----------------------+-----------------+---------------+------------+----------+ |
| | 1  | Store Credit Voucher | $ 15.00         | $ 15.00       | $ 0.00     | V-998    | |
| | 2  | Card / Electronic    | $ 30.00         | $ 30.00       | $ 0.00     | Visa4242 | |
| +----+----------------------+-----------------+---------------+------------+----------+ |
| [ 🗑 Remove Selected Tender (Delete) ]                                                  |
+-----------------------------------------------------------------------------------------+
| Add Next Tender:                                                                        |
| Tender Type:  (o) Cash    ( ) Card    ( ) Store Credit                                  |
| Amount:       [ $ 55.00                    ]  <- Pre-filled with exact remaining balance!|
| Quick Cash:   [ Exact ]  [ +10 ]  [ +20 ]  [ +50 ]  [ +100 ]                            |
| Ref / Note:   [ Auth # or Voucher ID       ]                                            |
|                                                                                         |
| [ + Add Tender Line (Enter) ]                                                           |
+-----------------------------------------------------------------------------------------+
| Status: Remaining balance of $55.00 must be paid before checkout. (Red/Green badge)     |
|                                                                                         |
| [ Cancel (Esc) ]                                   [ ✔ Complete Split Checkout (F10) ]  |
+-----------------------------------------------------------------------------------------+
```

#### Key UI/UX Behaviors & Zero-Touch Keyboard Operation:
1. **Auto-Filling Remaining Balance**:
   - Whenever a tender is added or removed, the "Amount" input field automatically updates to `remainingBalance`.
   - If a customer says "Put the rest on my card", the cashier simply presses `(Card)` $\rightarrow$ `[Enter]` $\rightarrow$ `[F10]`. Exactly 3 keypresses!
2. **Real-time Change & Badge Feedback**:
   - If cashier selects `Cash` and types `$50.00` on a `$35.00` remaining balance:
     - Real-time label updates: "Applied: $35.00 | Change Due: $15.00".
     - Badge switches from Red `Remaining: $35.00` to Green `Paid in Full! Change: $15.00`.
3. **Checkout Button Enabling Condition**:
   - `btnCompleteSplitCheckout.disableProperty().bind(remainingBalanceProperty.greaterThan(0.0001))`.
   - Impossible for a cashier to prematurely finalize an unpaid split order.
4. **Checkout Execution**:
   - Clicking `Complete Split Checkout` passes the validated `List<PaymentTender>` back to `PosBillingController`.
   - `PosBillingController` converts tenders to `InvoicePayment`s, calls `invoiceDao.createInvoice(invoice, items, payments)`, prompts receipt printing with split payment breakdown, and resets POS state.

---

### 2.4 Unit Testing Strategy

#### Suite 1: Pure Math Engine (`SplitTenderBillingTest.java`)
Test class targeting `BillingService.calculateSplitTender` and `BillingService.createTender`:
1. `testExactCashTender`: $100 net total, $100 cash $\rightarrow$ applied $100, change $0, remaining $0, fully paid.
2. `testOverTenderCashWithChange`: $35 net total, $50 cash tendered $\rightarrow$ applied $35, change $15, remaining $0, fully paid.
3. `testUnderTenderCashLeavesRemaining`: $100 net total, $30 cash $\rightarrow$ applied $30, change $0, remaining $70, `isFullyPaid() == false`.
4. `testCardTenderExact`: $50 remaining, $50 card $\rightarrow$ applied $50, change $0, remaining $0, fully paid.
5. `testCardOverTenderRejected`: $35 remaining, customer attempts $40 card $\rightarrow$ throws `IllegalArgumentException` ("Card tender cannot exceed remaining balance").
6. `testStoreCreditOverTenderRejected`: $20 remaining, customer attempts $25 voucher $\rightarrow$ throws `IllegalArgumentException` ("Store Credit tender cannot exceed remaining balance").
7. `testMultiTenderMixedBasket`:
   - Net total: $150.00
   - Tender 1: $25.00 Store Credit $\rightarrow$ remaining $125.00
   - Tender 2: $75.00 Card $\rightarrow$ remaining $50.00
   - Tender 3: $100.00 Cash $\rightarrow$ applied $50.00, change $50.00, remaining $0.00, fully paid!
   - Assert: total applied = $150.00, total cash tendered = $100.00, total change due = $50.00.
8. `testZeroOrNegativeTenderRejected`: $0.00 or -$5.00 tendered $\rightarrow$ throws `IllegalArgumentException`.
9. `testPennyRoundingPrecision`:
   - Net total: $100.00 split 3 ways: $33.33 Card 1, $33.33 Card 2, $33.34 Cash.
   - Assert: remaining is exactly $0.00, no floating-point pennies lost.
10. `testEmptyTenderList`: Net total $50.00, empty list $\rightarrow$ remaining $50.00, `isFullyPaid() == false`.

#### Suite 2: Persistence Integration (`InvoiceSplitPaymentDaoTest.java`)
Test class targeting `InvoiceDao` with `invoice_payments` table in temp SQLite database:
1. `testCreateInvoiceWithSplitPaymentsAtomicCommit`:
   - 2 products in catalog (stock = 10 each).
   - Order with 2 items (qty 2 each).
   - Split tenders: $20.00 Cash (tendered $30, change $10), $40.00 Card, $15.00 Store Credit. Total: $75.00.
   - Call `invoiceDao.createInvoice(invoice, items, payments)`.
   - Assert:
     - Invoice created with ID $> 0$.
     - `invoices.payment_method == 'SPLIT'`.
     - `invoices.cash_tendered == 30.00`, `invoices.change_due == 10.00`.
     - `invoice_payments` table contains 3 records with correct FK `invoice_id`, types, and amounts.
     - Product stocks decremented to 8.
2. `testAtomicRollbackOnPaymentFailure`:
   - Cause payment insertion to fail (e.g. simulated DB error or constraint violation).
   - Assert:
     - Transaction rolls back completely.
     - Product stocks remain 10.
     - No invoice record in `invoices`.
     - No records in `invoice_items` or `invoice_payments`.
3. `testInvoicePaymentsCascadeDelete`:
   - Create invoice with 3 payments.
   - Call `invoiceDao.deleteInvoice(invoiceId)`.
   - Assert: `invoice_payments` records with that `invoice_id` are deleted automatically by SQLite cascade.
4. `testBackwardCompatibilityLegacyCreateInvoice`:
   - Call legacy `invoiceDao.createInvoice(invoice, items)`.
   - Assert:
     - Invoice created.
     - `invoices.payment_method == 'CASH'`.
     - 1 record exists in `invoice_payments` for that invoice with type `'CASH'`, preserving universal payment record coverage.
5. `testFindInvoiceReconstructsSplitPayments`:
   - Call `invoicePaymentDao.getPaymentsByInvoiceId(invoiceId)`.
   - Convert payments to `PaymentTender`s and verify `BillingService.calculateSplitTender` reconstructs the original split breakdown.

---

## 3. Caveats
1. **Cashback Invariant**:
   - In commercial retail accounting and PCI-DSS merchant agreements, merchants cannot issue cash change on credit/debit card transactions or promotional store credits. Our math strictly enforces this by throwing `IllegalArgumentException` if `tenderedAmount > remainingBalance` for non-cash tenders.
2. **Partial Payments vs Layaway Accounts**:
   - Milestone 1.1-C addresses Split Tender POS payments (immediate settlement where $R_\text{final} = 0.00$). It does not implement deferred store accounts / layaway. Checkout must strictly require the balance to be $0.00.
3. **Foreign Keys in SQLite**:
   - `ON DELETE CASCADE` is enforced only when `PRAGMA foreign_keys = ON;`. `DatabaseManager.getConnection()` already issues this pragma upon connection acquisition.

---

## 4. Conclusion
1. **Math & Validation**:
   - `BillingService` should be enhanced with `SplitTenderResult`, `calculateSplitTender(double, List<PaymentTender>)`, and `createTender(...)`.
   - Cash allows over-tendering with automatic change calculation ($a_k = \min(t_k, R_k), c_k = t_k - a_k$).
   - Card and Store Credit strictly require $t_k \le R_k$ ($c_k = 0.00$).
   - Checkout is valid if and only if $R_\text{final} == 0.00$.
2. **Persistence**:
   - `InvoiceDao` should add `createInvoice(Invoice, List<InvoiceItem>, List<InvoicePayment>, Connection)` executing stock decrement, invoice insertion, item batch insertion, and payment batch insertion within the same atomic transaction.
   - `invoices.payment_method` will store `'SPLIT'` for multi-tender orders, while legacy calls default to `'CASH'` and auto-synthesize an `invoice_payments` record for 100% database consistency.
3. **UI Integration**:
   - A dedicated `SplitTenderDialog` triggered by `F8` or "Split Payment..." in POS provides real-time remaining balance calculations, quick cash chips, and zero-touch keyboard operation (Enter to add tender, F10 to complete).
4. **Testing**:
   - Two targeted test suites (`SplitTenderBillingTest.java` and `InvoiceSplitPaymentDaoTest.java`) will provide comprehensive mathematical, boundary, precision, and atomic rollback verification.

---

## 5. Verification Method

### 5.1 Independent Test Commands
- Compile project and tests:
  ```powershell
  .\mvnw.cmd test-compile
  ```
- Run existing payment DAO tests:
  ```powershell
  .\mvnw.cmd test -Dtest=InvoicePaymentDaoTest
  ```
- Run existing billing and POS tests:
  ```powershell
  .\mvnw.cmd test -Dtest=BillingServiceTest,PosBillingControllerTest
  ```
- Run new Milestone 1.1-C test suites once implemented:
  ```powershell
  .\mvnw.cmd test -Dtest=SplitTenderBillingTest,InvoiceSplitPaymentDaoTest
  ```
- Run entire regression suite:
  ```powershell
  .\mvnw.cmd test
  ```

### 5.2 Key Files to Inspect
- `src/main/java/com/example/t/service/BillingService.java`
- `src/main/java/com/example/t/model/PaymentTender.java`
- `src/main/java/com/example/t/model/InvoicePayment.java`
- `src/main/java/com/example/t/dao/InvoiceDao.java`
- `src/main/java/com/example/t/dao/InvoicePaymentDao.java`
- `src/main/java/com/example/t/controller/PosBillingController.java`
- `src/main/resources/com/example/t/fxml/PosBilling.fxml`
- `src/test/java/com/example/t/service/SplitTenderBillingTest.java`
- `src/test/java/com/example/t/dao/InvoiceSplitPaymentDaoTest.java`

### 5.3 Invalidation Conditions
- Any change causing non-cash tenders (Card / Store Credit) to produce cash change or accept amounts higher than the remaining balance.
- Any change allowing split checkout to succeed when remaining balance $> 0.00$.
- Any failure to roll back product stock or invoice header if `invoice_payments` insertion throws `SQLException`.
- Any breakage of existing 2-arg `createInvoice(Invoice, List<InvoiceItem>)` callers or regression in the existing 398 test suite.
