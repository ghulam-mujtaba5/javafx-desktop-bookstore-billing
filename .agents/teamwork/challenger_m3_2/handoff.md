# Empirical Challenge Handoff Report: Milestone 3 — Stock Management & Inventory

**Agent**: `challenger_m3_2`  
**Role**: Empirical Challenger (`critic`, `specialist`)  
**Working Directory**: `e:\javafx-desktop-bookstore-billing\.agents\teamwork\challenger_m3_2`  
**Parent Agent**: `orchestrator_1` (`a53fb0a7-174c-42cf-8f32-366cc200af43`)  
**Milestone**: Milestone 3 (Inventory & Stock Management)  
**Target Repository**: `e:\javafx-desktop-bookstore-billing`  
**Timestamp**: `2026-10-19T20:19:00Z`  
**Verdict**: **APPROVE** (Hard Handoff)

---

## 1. Observation

### 1.1 Mandate & Target Deliverables
The orchestrator assigned `challenger_m3_2` to empirically challenge the Stock Management filtering and inventory operations delivered by `worker_m3`:
1. Dynamic search filter resilience: Regex meta-characters (`[.*+?^${}()|]`), special SQL wildcards/injection characters, Unicode, Arabic script, and null/whitespace search strings.
2. Extreme restock quantities (1,000,000 units), zero/negative restock validation, and non-existent product ID handling.
3. Verification that restocking never introduces duplicate product IDs (empirical check on database uniqueness).
4. Low-stock badge boundary test: Verify transitions when quantity is exactly `min_stock_threshold`, `min_stock_threshold + 1`, `1`, `0`, and negative.

### 1.2 Inspection of Implementation Code
1. **Dynamic Search Filter**:
   In `src/main/java/com/example/t/controller/StockManagementController.java:235-266`:
   ```java
   String search = searchField != null && searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
   ...
   if (!search.isEmpty()) {
       boolean matchName = product.getProductName() != null && product.getProductName().toLowerCase().contains(search);
       boolean matchSku = product.getSku() != null && product.getSku().toLowerCase().contains(search);
       boolean matchAuthor = product.getAuthor() != null && product.getAuthor().toLowerCase().contains(search);
       boolean matchPublisher = product.getPublisher() != null && product.getPublisher().toLowerCase().contains(search);
       return matchName || matchSku || matchAuthor || matchPublisher;
   }
   ```
   The predicate uses literal substring search `String.contains()` rather than `String.matches()`, guarding against `PatternSyntaxException`. It guards against `null` search fields and `null` entity properties.
   In `src/main/java/com/example/t/dao/ProductDao.java:84-101`:
   ```java
   String sql = "SELECT p.*, c.name AS category_name FROM products p " +
           "LEFT JOIN categories c ON p.category_id = c.category_id " +
           "WHERE p.name LIKE ? OR p.sku LIKE ? OR p.author LIKE ? " +
           "ORDER BY p.name ASC;";
   try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
       String wildcard = "%" + (query != null ? query.trim() : "") + "%";
       pstmt.setString(1, wildcard);
       ...
   ```
   The database query relies on parameterized `PreparedStatement`, preventing SQL injection.

2. **Restock Validation & Extreme Quantities**:
   In `src/main/java/com/example/t/dao/ProductDao.java:242-256`:
   ```java
   public boolean restockProduct(int productId, int quantity, Connection conn) throws SQLException {
       if (conn == null) {
           return restockProduct(productId, quantity);
       }
       if (quantity <= 0) {
           throw new IllegalArgumentException("Quantity to restock must be positive: " + quantity);
       }
       String sql = "UPDATE products SET stock_quantity = stock_quantity + ?, updated_at = CURRENT_TIMESTAMP " +
               "WHERE product_id = ?;";
       try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
           pstmt.setInt(1, quantity);
           pstmt.setInt(2, productId);
           return pstmt.executeUpdate() > 0;
       }
   }
   ```
   Non-positive quantities (`quantity <= 0`) are rejected at the DAO boundary with `IllegalArgumentException`. The update query modifies only the targeted `product_id`.

3. **Low-Stock Badge Transitions**:
   In `src/main/java/com/example/t/controller/StockManagementController.java:167-208`:
   ```java
   colStockStatus.setCellValueFactory(c -> {
       Product p = c.getValue();
       if (p.getQuantity() <= 0) {
           return new SimpleStringProperty("OUT OF STOCK");
       } else if (p.getQuantity() <= p.getMinStockThreshold()) {
           return new SimpleStringProperty("LOW STOCK");
       } else {
           return new SimpleStringProperty("IN STOCK");
       }
   });
   ```
   The status evaluation follows clean non-overlapping tiers: `quantity <= 0` ("OUT OF STOCK"), `quantity <= minStockThreshold` ("LOW STOCK"), and `quantity > minStockThreshold` ("IN STOCK").

### 1.3 Empirical Test Execution & Results
`challenger_m3_2` created the dedicated adversarial test class:
`src/test/java/com/example/t/StockManagementEmpiricalChallengeTest.java` (638 lines, 17 test cases).

The test suite executed with the following empirical output:
```powershell
.\mvnw.cmd test -Dtest=StockManagementEmpiricalChallengeTest
```
*Result*:
```
[INFO] Running com.example.t.StockManagementEmpiricalChallengeTest
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 16.73 s -- in com.example.t.StockManagementEmpiricalChallengeTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

Full regression run across the entire project repository:
```powershell
.\mvnw.cmd test
```
*Result*:
```
[INFO] Results:
[INFO] 
[INFO] Tests run: 282, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  42.595 s
```

---

## 2. Logic Chain

1. **Dynamic Search Filter Resilience**:
   - *Observation*: Tested with 17 regex metacharacter sequences (`[.*+?^${}()|]`, `.*`, `(`, `)`, `[`, `]`, `{`, `}`, `\`, `^`, `$`, `|`, `+`, `?`, `*`, `[a-z]+`, `^(?=.*[A-Z]).+$`). Tested with 10 SQL injection and XML/HTML payloads (`' OR '1'='1`, `'; DROP TABLE products; --`, `%_%%`, `<script>alert(1)</script>`). Tested with Arabic script ("كتاب الأصول في التجارة والإدارة", author "نجيب محفوظ", publisher "دار الشروق"), German umlauts ("Über"), French accents ("Édition"), Japanese Kana/Kanji ("プログラミング"), and Unicode emojis ("📚", "✏️").
   - *Logic*: Because `StockManagementController` uses `String.contains()` rather than regex pattern compilation, and guards against `null` strings on both the search field and product entity properties, searching regex meta-characters never raises `PatternSyntaxException`. The SQLite DAO query parameterizes `LIKE ?`, ensuring SQL injection payloads are treated as literal text. Products containing Arabic script and Unicode characters are preserved accurately and filtered in real time.
   - *Inference*: The dynamic search filter is completely resilient against adversarial inputs and international multilingual catalogs.

2. **Extreme & Invalid Restock Handling**:
   - *Observation*: Restocked 1,000,000 units on product ID 1, followed by 500,000 units. Attempted restocking with 0, -1, -10, -500, -100,000, and `Integer.MIN_VALUE`. Attempted restocking non-existent product ID 999,999.
   - *Logic*: SQLite columns and Java 32-bit signed integers support numbers up to 2.14 billion. Restocking 1,000,000 units increments stock from 50 to 1,000,050 without overflow, and KPI total units reflects the updated inventory. Non-positive restock attempts strictly trigger `IllegalArgumentException("Quantity to restock must be positive: ...")` at the DAO boundary, and database queries confirm stock levels remained completely unmodified. Restocking a non-existent ID returns `false` without crashing or creating ghost records.
   - *Inference*: Inventory quantity arithmetic is sound, validated, and safe against invalid or corrupted inputs.

3. **Database Uniqueness & Duplicate ID Bug Elimination**:
   - *Observation*: Executed 50 sequential restock operations on product ID 1. Executed batch restock across all seeded catalog items. Queried SQLite database using `SELECT product_id, COUNT(*) AS cnt FROM products GROUP BY product_id HAVING cnt > 1;` and compared `SELECT COUNT(DISTINCT product_id)` against `SELECT COUNT(*)`. Checked `controller.getMasterData()` and `productTable.getItems()`.
   - *Logic*: The legacy semester project bug where `UpdateStockScreen` appended a new duplicate product instance to `data` on restock has been completely eliminated. `ProductDao.restockProduct()` executes an in-place `UPDATE products SET stock_quantity = stock_quantity + ? WHERE product_id = ?;`. The SQLite query returned 0 duplicate rows, and `COUNT(DISTINCT product_id) == COUNT(*) == initialCatalogSize`.
   - *Inference*: Product primary key uniqueness is strictly preserved across all restock operations.

4. **Low-Stock Badge Boundary Transitions**:
   - *Observation*: Evaluated badge status property and cell factory UI rendering across boundary quantities: `min_stock_threshold + 1` (11), `min_stock_threshold` (10), `min_stock_threshold - 1` (9), `1`, `0`, `-5`, and `min_stock_threshold = 0`. Tested low-stock checkbox filter inclusion/exclusion and KPI reactive updates.
   - *Logic*:
     - When `quantity == threshold + 1` (11 > 10): Status is `"IN STOCK"`. Low-stock filter excludes this product.
     - When `quantity == threshold` (10 == 10): Status transitions immediately to `"LOW STOCK"`. Low-stock filter includes this product.
     - When `quantity == 1`: Status is `"LOW STOCK"`. Low-stock filter includes this product.
     - When `quantity == 0`: Status transitions to `"OUT OF STOCK"`. Low-stock filter includes this product.
     - When `quantity < 0`: Status is `"OUT OF STOCK"`.
     - When `min_stock_threshold == 0`: `quantity = 1` yields `"IN STOCK"`; `quantity = 0` yields `"OUT OF STOCK"`.
     - Cell factory applies corresponding CSS classes: `.badge-out-of-stock` (red), `.badge-low-stock` (amber), `.badge-in-stock` (green). Restocking a low-stock item reactively decrements `kpiLowStockCount` by 1.
   - *Inference*: Boundary transitions are mathematically exact, visually correct, and reactively synchronized.

---

## 3. Caveats

1. **Headless Execution Environment**:
   - JavaFX UI components require platform initialization. Unit tests initialize JavaFX via `Platform.startup(() -> {})` to guarantee clean execution under Maven Surefire in headless environments.
2. **Concurrent Multi-User POS Billing**:
   - The desktop POS is currently designed as a single-station embedded SQLite application. Multi-terminal concurrent checkout race conditions are outside the Milestone 3 scope and will be evaluated in Milestone 4.

---

## 4. Conclusion

**Verdict: APPROVE**

Milestone 3 Stock Management and inventory persistence have successfully passed all adversarial empirical challenges:
- Dynamic search filtering handles regex meta-characters, SQL injection strings, Arabic script, Unicode/emojis, and null inputs without exception.
- Inventory restocking cleanly supports high quantities (1M units) and strictly validates against zero or negative amounts.
- Primary key uniqueness is 100% verified; restocking never introduces duplicate product IDs.
- Low-stock badge boundaries transition exactly at `threshold + 1`, `threshold`, `1`, and `0`.
- The full test suite passes with **282 tests, 0 failures, 0 errors, and 0 skipped**.

---

## 5. Verification Method

To independently verify this empirical evaluation:

1. **Run the Milestone 3 Challenger Test Suite**:
   ```powershell
   .\mvnw.cmd test -Dtest=StockManagementEmpiricalChallengeTest
   ```
   *Expected Outcome*:
   ```
   [INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
   [INFO] BUILD SUCCESS
   ```

2. **Run All Milestone 3 Unit, Integration, and Challenge Tests**:
   ```powershell
   .\mvnw.cmd test "-Dtest=MainShellControllerTest,StockManagementControllerTest,Milestone3IntegrationTest,StockManagementEmpiricalChallengeTest"
   ```
   *Expected Outcome*:
   ```
   [INFO] Tests run: 43, Failures: 0, Errors: 0, Skipped: 0
   [INFO] BUILD SUCCESS
   ```

3. **Run Full Clean Repository Test Suite**:
   ```powershell
   .\mvnw.cmd test
   ```
   *Expected Outcome*:
   ```
   [INFO] Tests run: 282, Failures: 0, Errors: 0, Skipped: 0
   [INFO] BUILD SUCCESS
   ```

4. **Inspect Challenge Test Implementation**:
   - Path: `src/test/java/com/example/t/StockManagementEmpiricalChallengeTest.java`

5. **Invalidation Conditions**:
   - If searching regex characters (`[.*+?^${}()|]`) throws `PatternSyntaxException`.
   - If calling `ProductDao.restockProduct(id, 0)` or `restockProduct(id, -1)` does not throw `IllegalArgumentException`.
   - If running `SELECT product_id, COUNT(*) FROM products GROUP BY product_id HAVING COUNT(*) > 1` returns any rows after restocking.
   - If product with `quantity == min_stock_threshold` displays `"IN STOCK"` instead of `"LOW STOCK"`.
