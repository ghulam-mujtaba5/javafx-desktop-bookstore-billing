package com.example.t.controller;

import com.example.t.Invoice;
import com.example.t.NotificationUtil;
import com.example.t.Product;
import com.example.t.dao.CatalogCacheDao;
import com.example.t.dao.CustomerDao;
import com.example.t.dao.InvoiceDao;
import com.example.t.dao.ProductDao;
import com.example.t.dao.SettingsDao;
import com.example.t.database.DatabaseManager;
import com.example.t.model.BookMetadata;
import com.example.t.model.Customer;
import com.example.t.model.InvoiceItem;
import com.example.t.model.InvoicePayment;
import com.example.t.model.ParkedCart;
import com.example.t.model.PaymentTender;
import com.example.t.model.PaymentType;
import com.example.t.model.StoreSettings;
import com.example.t.service.BillingService;
import com.example.t.service.PdfInvoiceService;
import com.example.t.service.isbn.MetadataService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import com.example.t.util.BarcodeScannerBuffer;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Controller for the Point of Sale (POS) and Billing Screen.
 * Provides barcode/SKU auto-add, interactive cart management, real-time
 * multi-regional tax calculation via BillingService, and atomic checkout with
 * OpenPDF receipt generation.
 */
public class PosBillingController {

    @FXML private BorderPane posRootPane;
    @FXML private Label posRegionBadge;
    @FXML private Label posTaxBadge;
    @FXML private TextField searchField;
    @FXML private Button btnAddToCart;
    @FXML private Button btnClearSearch;
    @FXML private ToggleButton btnGridViewToggle;
    @FXML private ToggleButton btnListViewToggle;
    @FXML private HBox categoryChipsBox;
    @FXML private ScrollPane tileGridScrollPane;
    @FXML private FlowPane productTilesFlowPane;
    @FXML private VBox catalogResultsBox;
    @FXML private TableView<Product> catalogTable;
    @FXML private TableColumn<Product, String> colCatalogSku;
    @FXML private TableColumn<Product, String> colCatalogName;
    @FXML private TableColumn<Product, String> colCatalogCategory;
    @FXML private TableColumn<Product, String> colCatalogPrice;
    @FXML private TableColumn<Product, Integer> colCatalogStock;

    @FXML private Label cartItemCountLabel;
    @FXML private Button btnHoldOrder;
    @FXML private Button btnRecallOrder;
    @FXML private Button btnItemDiscount;
    @FXML private Button btnItemNote;
    @FXML private Button btnSplitTender;
    @FXML private TableView<InvoiceItem> cartTable;

    private static class HeldOrder {
        String customerName;
        List<InvoiceItem> items;
        String discountPercent;
        String timestamp;
        HeldOrder(String customerName, List<InvoiceItem> items, String discountPercent) {
            this.customerName = customerName;
            this.items = new ArrayList<>(items);
            this.discountPercent = discountPercent;
            this.timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"));
        }
    }
    private final List<HeldOrder> heldOrders = new ArrayList<>();
    private final ObservableList<ParkedCart> parkedCarts = FXCollections.observableArrayList();
    private List<InvoicePayment> appliedSplitPayments;
    private BarcodeScannerBuffer scannerBuffer;
    @FXML private CheckBox giftReceiptCheckBox;
    private String selectedCategory = "All Items";
    @FXML private TableColumn<InvoiceItem, String> colCartSku;
    @FXML private TableColumn<InvoiceItem, String> colCartName;
    @FXML private TableColumn<InvoiceItem, String> colCartPrice;
    @FXML private TableColumn<InvoiceItem, Integer> colCartQty;
    @FXML private TableColumn<InvoiceItem, String> colCartDiscount;
    @FXML private TableColumn<InvoiceItem, String> colCartTotal;

    @FXML private TextField customerNameField;
    @FXML private Label subtotalLabel;
    @FXML private TextField discountPercentField;
    @FXML private Label discountAmountLabel;
    @FXML private Label taxableAmountLabel;
    @FXML private Label taxTitleLabel;
    @FXML private Label taxAmountLabel;
    @FXML private Label grandTotalLabel;

    @FXML private ComboBox<String> paymentMethodCombo;
    @FXML private TextField cashTenderedField;
    @FXML private Label changeDueLabel;
    @FXML private CheckBox generatePdfCheckBox;
    @FXML private Button btnCheckout;
    @FXML private Label checkoutStatusLabel;

    private ProductDao productDao;
    private InvoiceDao invoiceDao;
    private SettingsDao settingsDao;
    private PdfInvoiceService pdfInvoiceService;
    private CatalogCacheDao catalogCacheDao;
    private MetadataService metadataService;

    private StoreSettings settings;
    private final ObservableList<InvoiceItem> cartItems = FXCollections.observableArrayList();
    private final ObservableList<Product> catalogData = FXCollections.observableArrayList();
    private FilteredList<Product> filteredCatalog;

    private double currentNetTotal = 0.0;
    private double currentChangeDue = 0.0;

    @FXML
    public void initialize() {
        if (productDao == null) productDao = new ProductDao();
        if (invoiceDao == null) invoiceDao = new InvoiceDao();
        if (settingsDao == null) settingsDao = new SettingsDao();
        if (pdfInvoiceService == null) pdfInvoiceService = new PdfInvoiceService();

        loadStoreSettings();
        setupCatalogTable();
        setupCartTable();
        setupPaymentControls();
        loadCatalog();
        setupCategoryChips();
        renderProductTiles();
        recalculateOrder();
    }

    public void setProductDao(ProductDao productDao) {
        this.productDao = productDao;
        loadCatalog();
    }

    public void setInvoiceDao(InvoiceDao invoiceDao) {
        this.invoiceDao = invoiceDao;
    }

    public void setSettingsDao(SettingsDao settingsDao) {
        this.settingsDao = settingsDao;
        loadStoreSettings();
        recalculateOrder();
    }

    public void setPdfInvoiceService(PdfInvoiceService pdfInvoiceService) {
        this.pdfInvoiceService = pdfInvoiceService;
    }

    public void setCatalogCacheDao(CatalogCacheDao catalogCacheDao) {
        this.catalogCacheDao = catalogCacheDao;
    }

    public void setMetadataService(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    private void loadStoreSettings() {
        try {
            if (settingsDao != null) {
                settings = settingsDao.getSettings();
            }
        } catch (SQLException ignored) {}

        if (settings == null) {
            settings = StoreSettings.defaultSettings();
        }

        if (posRegionBadge != null) {
            posRegionBadge.setText(settings.getRegionPreset() + " (" + settings.getCurrencyCode() + " " + settings.getCurrencySymbol() + ")");
        }
        if (posTaxBadge != null) {
            posTaxBadge.setText(settings.getTaxRate() + "% " + settings.getTaxName());
        }
        if (taxTitleLabel != null) {
            taxTitleLabel.setText(settings.getTaxName() + " (" + settings.getTaxRate() + "%):");
        }
    }

    private void setupCatalogTable() {
        if (catalogTable == null) return;

        colCatalogSku.setCellValueFactory(cellData -> cellData.getValue().skuProperty());
        colCatalogName.setCellValueFactory(cellData -> cellData.getValue().productNameProperty());
        colCatalogCategory.setCellValueFactory(cellData -> cellData.getValue().categoryProperty());
        colCatalogPrice.setCellValueFactory(cellData ->
                new SimpleStringProperty(formatMoney(cellData.getValue().getPrice())));
        colCatalogStock.setCellValueFactory(cellData -> cellData.getValue().quantityProperty().asObject());

        filteredCatalog = new FilteredList<>(catalogData, p -> true);
        catalogTable.setItems(filteredCatalog);

        // Instant search filtering
        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> {
                applyCategoryFilter();
            });
        }

        // Double click to add
        catalogTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && catalogTable.getSelectionModel().getSelectedItem() != null) {
                addProductToCart(catalogTable.getSelectionModel().getSelectedItem(), 1);
            }
        });
    }

    private void setupCartTable() {
        if (cartTable == null) return;

        colCartSku.setCellValueFactory(cellData -> cellData.getValue().skuProperty());
        colCartName.setCellValueFactory(cellData -> cellData.getValue().productNameProperty());
        colCartPrice.setCellValueFactory(cellData ->
                new SimpleStringProperty(formatMoney(cellData.getValue().getUnitPrice())));
        colCartQty.setCellValueFactory(cellData -> cellData.getValue().quantityProperty().asObject());
        if (colCartDiscount != null) {
            colCartDiscount.setCellValueFactory(cellData -> {
                double disc = cellData.getValue().getDiscountPercent();
                return new SimpleStringProperty(disc > 0 ? String.format(java.util.Locale.US, "%.1f%%", disc) : "-");
            });
        }
        colCartTotal.setCellValueFactory(cellData ->
                new SimpleStringProperty(formatMoney(cellData.getValue().getLineTotal())));

        cartTable.setItems(cartItems);

        cartItems.addListener((javafx.collections.ListChangeListener<InvoiceItem>) c -> {
            updateCartItemCount();
            recalculateOrder();
        });
    }

    private void setupPaymentControls() {
        if (paymentMethodCombo != null) {
            paymentMethodCombo.setItems(FXCollections.observableArrayList("Cash", "Credit Card", "Debit Card"));
            paymentMethodCombo.setValue("Cash");
            paymentMethodCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                if ("Credit Card".equals(newVal) || "Debit Card".equals(newVal)) {
                    if (cashTenderedField != null) {
                        cashTenderedField.setText(String.format("%.2f", currentNetTotal));
                    }
                }
                recalculateOrder();
            });
        }

        if (discountPercentField != null) {
            discountPercentField.textProperty().addListener((obs, oldVal, newVal) -> recalculateOrder());
        }

        if (cashTenderedField != null) {
            cashTenderedField.textProperty().addListener((obs, oldVal, newVal) -> recalculateOrder());
        }
    }

    public void loadCatalog() {
        if (productDao == null) return;
        try {
            List<Product> products = productDao.getAllProducts();
            catalogData.setAll(products);
            renderProductTiles();
        } catch (SQLException e) {
            NotificationUtil.showError("Database Error", "Failed to load catalog: " + e.getMessage());
        }
    }

    /**
     * Adds an item to the active cart or increments quantity if already in cart.
     */
    public void addProductToCart(Product product, int quantity) {
        if (product == null || quantity <= 0) return;
        if (product.getQuantity() <= 0) {
            NotificationUtil.showWarning("Out of Stock", "'" + product.getProductName() + "' is out of stock.");
            return;
        }

        // Check if item already exists in cart
        for (InvoiceItem item : cartItems) {
            if (item.getProductId() == product.getProductId()) {
                int newQty = item.getQuantity() + quantity;
                if (newQty > product.getQuantity()) {
                    NotificationUtil.showWarning("Stock Limit", "Requested quantity (" + newQty + ") exceeds available stock (" + product.getQuantity() + ").");
                    return;
                }
                item.setQuantity(newQty);
                cartTable.refresh();
                recalculateOrder();
                return;
            }
        }

        // New item
        if (quantity > product.getQuantity()) {
            NotificationUtil.showWarning("Stock Limit", "Requested quantity exceeds available stock (" + product.getQuantity() + ").");
            return;
        }

        InvoiceItem newItem = new InvoiceItem(
                product.getProductId(),
                product.getProductName(),
                product.getSku(),
                quantity,
                product.getPrice()
        );
        cartItems.add(newItem);
    }

    /**
     * Fast barcode / SKU / ISBN / search field Enter action.
     */
    @FXML
    public void handleSearchEnter(ActionEvent event) {
        String text = searchField != null ? searchField.getText() : "";
        if (text == null || text.trim().isEmpty()) return;

        String query = text.trim();

        // 1. Try exact SKU/barcode or ISBN match via ProductDao
        if (productDao != null) {
            try {
                Optional<Product> directMatch = productDao.findBySkuOrIsbn(query);
                if (directMatch.isPresent()) {
                    addProductToCart(directMatch.get(), 1);
                    searchField.clear();
                    return;
                }
            } catch (SQLException ignored) {}
        }

        // 2. Try match in active filtered list
        if (filteredCatalog != null && !filteredCatalog.isEmpty()) {
            // Check for exact SKU or Edition/ISBN in catalog
            for (Product p : filteredCatalog) {
                if (query.equalsIgnoreCase(p.getSku()) ||
                    (p.getEdition() != null && query.equalsIgnoreCase(p.getEdition().replaceAll("-", "")))) {
                    addProductToCart(p, 1);
                    searchField.clear();
                    return;
                }
            }
            // If only 1 item matches query, add it
            if (filteredCatalog.size() == 1) {
                addProductToCart(filteredCatalog.get(0), 1);
                searchField.clear();
                return;
            }
        }

        // 3. Fallback: Check local catalog cache for scanned ISBN
        try {
            CatalogCacheDao cacheDao = catalogCacheDao != null ? catalogCacheDao : new CatalogCacheDao();
            Optional<BookMetadata> cached = cacheDao.findByIsbn(query);
            if (cached.isPresent()) {
                BookMetadata meta = cached.get();
                NotificationUtil.showInfo("Catalog Match Found",
                        "Book found in catalog cache: '" + meta.getTitle() + "' (" + meta.getIsbn13() +
                        "). Add it to stock in Stock Management to begin selling.");
            }
        } catch (Exception ignored) {}

        // 4. Otherwise, select first matching item in catalogTable
        if (catalogTable != null && !filteredCatalog.isEmpty()) {
            catalogTable.getSelectionModel().selectFirst();
            catalogTable.requestFocus();
        }
    }

    @FXML
    public void handleAddToCartSelected(ActionEvent event) {
        if (catalogTable == null) return;
        Product selected = catalogTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            addProductToCart(selected, 1);
        } else {
            NotificationUtil.showWarning("Select Item", "Please select a product from the catalog table to add.");
        }
    }

    @FXML
    public void handleClearSearch(ActionEvent event) {
        if (searchField != null) {
            searchField.clear();
        }
    }

    @FXML
    public void handleIncreaseQty(ActionEvent event) {
        InvoiceItem selected = cartTable != null ? cartTable.getSelectionModel().getSelectedItem() : null;
        if (selected != null) {
            // Find current stock
            int availableStock = Integer.MAX_VALUE;
            if (productDao != null) {
                try {
                    Optional<Product> p = productDao.findById(selected.getProductId());
                    if (p.isPresent()) availableStock = p.get().getQuantity();
                } catch (SQLException ignored) {}
            }
            if (selected.getQuantity() + 1 > availableStock) {
                NotificationUtil.showWarning("Stock Limit", "Cannot increase quantity above available stock (" + availableStock + ").");
                return;
            }
            selected.setQuantity(selected.getQuantity() + 1);
            cartTable.refresh();
            recalculateOrder();
        }
    }

    @FXML
    public void handleDecreaseQty(ActionEvent event) {
        InvoiceItem selected = cartTable != null ? cartTable.getSelectionModel().getSelectedItem() : null;
        if (selected != null) {
            int newQty = selected.getQuantity() - 1;
            if (newQty <= 0) {
                cartItems.remove(selected);
            } else {
                selected.setQuantity(newQty);
                cartTable.refresh();
            }
            recalculateOrder();
        }
    }

    @FXML
    public void handleRemoveItem(ActionEvent event) {
        InvoiceItem selected = cartTable != null ? cartTable.getSelectionModel().getSelectedItem() : null;
        if (selected != null) {
            cartItems.remove(selected);
        }
    }

    @FXML
    public void handleClearCart(ActionEvent event) {
        cartItems.clear();
        appliedSplitPayments = null;
        if (checkoutStatusLabel != null) {
            checkoutStatusLabel.setText("Cart cleared");
        }
        recalculateOrder();
    }

    @FXML
    public void handleQuickExact(ActionEvent event) {
        if (cashTenderedField != null) {
            cashTenderedField.setText(String.format("%.2f", currentNetTotal));
        }
    }

    @FXML
    public void handleQuick10(ActionEvent event) {
        addQuickCash(10.0);
    }

    @FXML
    public void handleQuick20(ActionEvent event) {
        addQuickCash(20.0);
    }

    @FXML
    public void handleQuick50(ActionEvent event) {
        addQuickCash(50.0);
    }

    @FXML
    public void handleQuick100(ActionEvent event) {
        addQuickCash(100.0);
    }

    private void addQuickCash(double amount) {
        if (cashTenderedField == null) return;
        double current = parseDoubleSafe(cashTenderedField.getText(), 0.0);
        cashTenderedField.setText(String.format("%.2f", current + amount));
    }

    /**
     * Recalculates subtotal, discount, taxable amount, tax/VAT, net total,
     * and change due using BillingService.
     */
    public void recalculateOrder() {
        if (settings == null) {
            loadStoreSettings();
        }

        double discountPercent = 0.0;
        if (discountPercentField != null) {
            discountPercent = parseDoubleSafe(discountPercentField.getText(), 0.0);
            if (discountPercent < 0) discountPercent = 0.0;
            if (discountPercent > 100.0) discountPercent = 100.0;
        }

        double cashTendered = 0.0;
        if (cashTenderedField != null) {
            cashTendered = parseDoubleSafe(cashTenderedField.getText(), 0.0);
        }

        for (InvoiceItem item : cartItems) {
            if (item.getDiscountPercent() > 0.0) {
                double raw = item.getUnitPrice() * item.getQuantity();
                double discAmt = Math.round(raw * (item.getDiscountPercent() / 100.0) * 100.0) / 100.0;
                item.setDiscountAmount(discAmt);
                item.setLineTotal(raw - discAmt);
            }
        }

        BillingService.CalculationResult result = BillingService.calculateInvoiceItems(
                new ArrayList<>(cartItems), discountPercent, settings, cashTendered);

        currentNetTotal = result.getNetTotal();
        currentChangeDue = result.getChangeDue();

        if (subtotalLabel != null) subtotalLabel.setText(formatMoney(result.getSubtotal()));
        if (discountAmountLabel != null) discountAmountLabel.setText("- " + formatMoney(result.getDiscountAmount()));
        if (taxableAmountLabel != null) taxableAmountLabel.setText(formatMoney(result.getTaxableAmount()));
        if (taxAmountLabel != null) taxAmountLabel.setText(formatMoney(result.getTaxAmount()));
        if (grandTotalLabel != null) grandTotalLabel.setText(formatMoney(result.getNetTotal()));

        if (changeDueLabel != null) {
            changeDueLabel.setText(formatMoney(result.getChangeDue()));
            if (result.getChangeDue() < 0) {
                changeDueLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #dc2626;");
            } else {
                changeDueLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #15803d;");
            }
        }
    }

    /**
     * Completes an atomic checkout: persists invoice and line items, decrements stock,
     * generates PDF receipt, displays notification toast, and clears cart.
     */
    @FXML
    public void handleCheckout(ActionEvent event) {
        if (cartItems.isEmpty()) {
            NotificationUtil.showWarning("Empty Cart", "Cannot checkout an empty cart. Add items first.");
            return;
        }

        String paymentMethod = paymentMethodCombo != null && paymentMethodCombo.getValue() != null
                ? paymentMethodCombo.getValue() : "Cash";

        double cashTendered = parseDoubleSafe(cashTenderedField != null ? cashTenderedField.getText() : "0", 0.0);
        if ("Cash".equalsIgnoreCase(paymentMethod) && cashTendered < currentNetTotal) {
            NotificationUtil.showWarning("Insufficient Tender",
                    String.format("Tendered cash (%s) is less than Grand Total (%s).",
                            formatMoney(cashTendered), formatMoney(currentNetTotal)));
            return;
        }

        String customer = customerNameField != null && !customerNameField.getText().trim().isEmpty()
                ? customerNameField.getText().trim() : "Walk-in Customer";

        double discountPercent = parseDoubleSafe(discountPercentField != null ? discountPercentField.getText() : "0", 0.0);
        BillingService.CalculationResult calc = BillingService.calculateInvoiceItems(
                new ArrayList<>(cartItems), discountPercent, settings, cashTendered);

        String determinedMethod = (appliedSplitPayments != null && !appliedSplitPayments.isEmpty()) ? "SPLIT" : paymentMethod;
        Invoice invoice = new Invoice(
                0, 0, customer, LocalDate.now(), new ArrayList<>(),
                calc.getSubtotal(), calc.getDiscountPercent(), calc.getDiscountAmount(), calc.getNetTotal()
        );
        invoice.setPaymentMethod(determinedMethod);

        List<InvoiceItem> itemsToSave = new ArrayList<>(cartItems);

        try {
            int invoiceId;
            if (appliedSplitPayments != null && !appliedSplitPayments.isEmpty()) {
                invoiceId = invoiceDao.createInvoice(invoice, itemsToSave, appliedSplitPayments);
                appliedSplitPayments = null;
            } else {
                invoiceId = invoiceDao.createInvoice(invoice, itemsToSave);
            }
            invoice.setInvoiceId(invoiceId);
            invoice.setOrderNum(invoiceId);

            Path pdfPath = null;
            if (generatePdfCheckBox != null && generatePdfCheckBox.isSelected() && pdfInvoiceService != null) {
                try {
                    pdfPath = pdfInvoiceService.generateInvoicePdf(invoice, itemsToSave, settings);
                } catch (IOException e) {
                    NotificationUtil.showWarning("PDF Warning", "Invoice saved to database, but PDF generation failed: " + e.getMessage());
                }
            }

            if (giftReceiptCheckBox != null && giftReceiptCheckBox.isSelected() && pdfInvoiceService != null) {
                try {
                    pdfInvoiceService.generateGiftReceipt(invoice, itemsToSave, settings);
                } catch (IOException e) {
                    NotificationUtil.showWarning("PDF Warning", "Gift receipt failed: " + e.getMessage());
                }
            }

            String msg = "Invoice #" + invoiceId + " completed! Change Due: " + formatMoney(calc.getChangeDue());
            NotificationUtil.showSuccess("Sale Complete", msg);

            if (checkoutStatusLabel != null) {
                checkoutStatusLabel.setText("Last Sale: #" + invoiceId + " (" + formatMoney(calc.getNetTotal()) + ")");
            }

            // Reset cart & reload catalog
            cartItems.clear();
            if (cashTenderedField != null) cashTenderedField.setText("0.00");
            if (discountPercentField != null) discountPercentField.setText("0.0");
            loadCatalog();
            recalculateOrder();

        } catch (SQLException e) {
            NotificationUtil.showError("Transaction Failed", "Checkout failed: " + e.getMessage() + ". Order rolled back.");
        }
    }

    private void updateCartItemCount() {
        int count = 0;
        for (InvoiceItem item : cartItems) {
            count += item.getQuantity();
        }
        if (cartItemCountLabel != null) {
            cartItemCountLabel.setText("(" + count + " items)");
        }
    }

    private String formatMoney(double amount) {
        String sym = settings != null ? settings.getCurrencySymbol() : "$";
        return String.format("%s %.2f", sym, amount);
    }

    private double parseDoubleSafe(String text, double defaultVal) {
        if (text == null || text.trim().isEmpty()) return defaultVal;
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    public ObservableList<InvoiceItem> getCartItems() {
        return cartItems;
    }

    public double getCurrentNetTotal() {
        return currentNetTotal;
    }

    public double getCurrentChangeDue() {
        return currentChangeDue;
    }

    public void setupCategoryChips() {
        if (categoryChipsBox == null) return;
        categoryChipsBox.getChildren().clear();

        List<String> categories = new ArrayList<>();
        categories.add("All Items");
        categories.add("Fiction");
        categories.add("Non-Fiction");
        categories.add("Academic");
        categories.add("Stationery");
        categories.add("Magazines");
        categories.add("Best Sellers");

        if (selectedCategory == null || selectedCategory.isEmpty()) {
            selectedCategory = "All Items";
        }

        boolean first = true;
        for (String cat : categories) {
            Button chip = new Button(cat);
            chip.getStyleClass().add("category-chip");
            if (cat.equalsIgnoreCase(selectedCategory) || (first && ("ALL".equalsIgnoreCase(selectedCategory) || "All Items".equalsIgnoreCase(selectedCategory)))) {
                chip.getStyleClass().add("active");
            }
            chip.setOnAction(e -> {
                selectedCategory = cat;
                for (javafx.scene.Node n : categoryChipsBox.getChildren()) {
                    n.getStyleClass().remove("active");
                }
                chip.getStyleClass().add("active");
                applyCategoryFilter();
            });
            categoryChipsBox.getChildren().add(chip);
            first = false;
        }
    }

    private void applyCategoryFilter() {
        if (filteredCatalog == null) return;
        String query = searchField != null && searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        filteredCatalog.setPredicate(p -> {
            boolean matchesCat;
            if (selectedCategory == null || "ALL".equalsIgnoreCase(selectedCategory) || "All Items".equalsIgnoreCase(selectedCategory)) {
                matchesCat = true;
            } else if ("Best Sellers".equalsIgnoreCase(selectedCategory)) {
                matchesCat = true;
            } else if (p.getCategory() != null) {
                String pCat = p.getCategory().trim().toLowerCase();
                String sel = selectedCategory.trim().toLowerCase();
                if (pCat.equals(sel)) {
                    matchesCat = true;
                } else if (pCat.equals("books") && (sel.equals("fiction") || sel.equals("non-fiction") || sel.equals("academic"))) {
                    matchesCat = true;
                } else if (pCat.contains(sel) || sel.contains(pCat)) {
                    matchesCat = true;
                } else {
                    matchesCat = false;
                }
            } else {
                matchesCat = false;
            }

            if (!matchesCat) return false;
            if (query.isEmpty()) return true;
            return (p.getProductName() != null && p.getProductName().toLowerCase().contains(query)) ||
                   (p.getSku() != null && p.getSku().toLowerCase().contains(query)) ||
                   (p.getAuthor() != null && p.getAuthor().toLowerCase().contains(query));
        });
        renderProductTiles();
    }

    public void renderProductTiles() {
        if (productTilesFlowPane == null) return;
        productTilesFlowPane.getChildren().clear();

        List<Product> displayItems = filteredCatalog != null ? filteredCatalog : catalogData;
        for (Product product : displayItems) {
            VBox tile = new VBox(6);
            tile.getStyleClass().add("product-tile");
            tile.setPrefSize(155, 195);
            tile.setMinSize(155, 195);
            tile.setMaxSize(155, 195);
            tile.setPadding(new Insets(10));

            Label titleLabel = new Label(product.getProductName());
            titleLabel.getStyleClass().add("product-tile-title");
            titleLabel.setWrapText(true);
            titleLabel.setMaxHeight(50);
            VBox.setVgrow(titleLabel, Priority.ALWAYS);

            Label catLabel = new Label(product.getCategory() != null ? product.getCategory() : "General");
            catLabel.getStyleClass().add("product-tile-category");

            HBox bottomRow = new HBox(6);
            bottomRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            Label priceLabel = new Label(formatMoney(product.getPrice()));
            priceLabel.getStyleClass().add("product-tile-price");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            int stock = product.getQuantity();
            Label stockLabel = new Label();
            stockLabel.getStyleClass().add("product-tile-stock");
            if (stock <= 0) {
                stockLabel.setText("Out of Stock");
                stockLabel.getStyleClass().add("stock-out");
            } else if (stock <= 5) {
                stockLabel.setText(stock + " in stock");
                stockLabel.getStyleClass().add("stock-low");
            } else {
                stockLabel.setText(stock + " in stock");
                stockLabel.getStyleClass().add("stock-in");
            }

            bottomRow.getChildren().addAll(priceLabel, spacer, stockLabel);
            tile.getChildren().addAll(titleLabel, catLabel, bottomRow);

            tile.setOnMouseClicked(e -> {
                if (product.getQuantity() > 0) {
                    addProductToCart(product, 1);
                } else {
                    NotificationUtil.showWarning("Out of Stock", "'" + product.getProductName() + "' is out of stock.");
                }
            });

            productTilesFlowPane.getChildren().add(tile);
        }
    }

    @FXML
    public void handleToggleGridView(ActionEvent event) {
        if (tileGridScrollPane != null) tileGridScrollPane.setVisible(true);
        if (catalogTable != null) catalogTable.setVisible(false);
    }

    @FXML
    public void handleToggleListView(ActionEvent event) {
        if (tileGridScrollPane != null) tileGridScrollPane.setVisible(false);
        if (catalogTable != null) catalogTable.setVisible(true);
    }

    @FXML
    public void handleHoldCart(ActionEvent event) {
        if (cartItems.isEmpty()) {
            NotificationUtil.showWarning("Empty Cart", "Cannot hold an empty cart.");
            return;
        }
        String custName = customerNameField != null ? customerNameField.getText() : "Customer";
        String disc = discountPercentField != null ? discountPercentField.getText() : "0";
        HeldOrder held = new HeldOrder(custName, cartItems, disc);
        heldOrders.add(held);
        double discVal = parseDoubleSafe(disc, 0.0);
        ParkedCart parked = new ParkedCart("CART-" + (parkedCarts.size() + 1), custName, new ArrayList<>(cartItems), discVal);
        parkedCarts.add(parked);
        cartItems.clear();
        if (btnRecallOrder != null) {
            btnRecallOrder.setText("▶ Recall Cart (" + heldOrders.size() + ")");
        }
        if (checkoutStatusLabel != null) {
            checkoutStatusLabel.setText("Order held (" + held.timestamp + "). Total held: " + heldOrders.size());
        }
        NotificationUtil.showSuccess("Cart Placed on Hold", "Order for '" + custName + "' is safely held. You can serve the next customer.");
    }

    @FXML
    public void handleRecallCart(ActionEvent event) {
        if (heldOrders.isEmpty()) {
            NotificationUtil.showInfo("No Held Orders", "There are currently no orders on hold.");
            return;
        }
        if (!cartItems.isEmpty()) {
            NotificationUtil.showWarning("Active Cart Not Empty", "Please checkout or hold the active cart before recalling another order.");
            return;
        }
        HeldOrder held = heldOrders.remove(heldOrders.size() - 1);
        cartItems.setAll(held.items);
        if (customerNameField != null) customerNameField.setText(held.customerName);
        if (discountPercentField != null) discountPercentField.setText(held.discountPercent);
        if (btnRecallOrder != null) {
            btnRecallOrder.setText("▶ Recall Cart (" + heldOrders.size() + ")");
        }
        if (checkoutStatusLabel != null) {
            checkoutStatusLabel.setText("Recalled order for " + held.customerName);
        }
        NotificationUtil.showSuccess("Order Recalled", "Restored order for '" + held.customerName + "'.");
    }

    @FXML private void handleDiscount0(ActionEvent e) { setQuickDiscount(0.0); }
    @FXML private void handleDiscount5(ActionEvent e) { setQuickDiscount(5.0); }
    @FXML private void handleDiscount10(ActionEvent e) { setQuickDiscount(10.0); }
    @FXML private void handleDiscount15(ActionEvent e) { setQuickDiscount(15.0); }
    @FXML private void handleDiscount20(ActionEvent e) { setQuickDiscount(20.0); }

    private void setQuickDiscount(double pct) {
        if (discountPercentField != null) {
            discountPercentField.setText(String.format(java.util.Locale.US, "%.1f", pct));
        }
        recalculateOrder();
    }

    public void handleScannedBarcode(String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) return;
        String trimmed = barcode.trim();
        for (Product p : catalogData) {
            if (trimmed.equalsIgnoreCase(p.getSku())) {
                addProductToCart(p, 1);
                if (checkoutStatusLabel != null) {
                    checkoutStatusLabel.setText("Scanned & added: " + p.getProductName());
                }
                return;
            }
        }
        if (productDao != null) {
            try {
                Optional<Product> direct = productDao.findBySkuOrIsbn(trimmed);
                if (direct.isPresent()) {
                    addProductToCart(direct.get(), 1);
                    if (checkoutStatusLabel != null) {
                        checkoutStatusLabel.setText("Scanned & added: " + direct.get().getProductName());
                    }
                    return;
                }
            } catch (SQLException ignored) {}
        }
        if (checkoutStatusLabel != null) {
            checkoutStatusLabel.setText("Barcode not found: " + trimmed);
        }
    }

    public ObservableList<ParkedCart> getParkedCarts() {
        return parkedCarts;
    }

    public void recallCart(ParkedCart cart) {
        if (cart == null) return;
        parkedCarts.remove(cart);
        cartItems.setAll(cart.restoreItemsSnapshot());
        if (customerNameField != null) customerNameField.setText(cart.getCustomerName());
        if (discountPercentField != null) {
            discountPercentField.setText(String.format(java.util.Locale.US, "%.1f", cart.getCartDiscountPercent()));
        }
        if (btnRecallOrder != null) {
            btnRecallOrder.setText("▶ Recall Cart (" + (parkedCarts.size() + heldOrders.size()) + ")");
        }
        recalculateOrder();
    }

    public void setupKeyboardAndScannerBuffer() {
        scannerBuffer = new BarcodeScannerBuffer();

        if (posRootPane != null) {
            scannerBuffer.attachTo(posRootPane, barcode -> {
                Platform.runLater(() -> handleScannedBarcode(barcode));
            });

            javafx.event.EventHandler<KeyEvent> keyHandler = event -> {
                KeyCode code = event.getCode();
                switch (code) {
                    case F1 -> {
                        if (searchField != null) {
                            searchField.requestFocus();
                            searchField.selectAll();
                        }
                        event.consume();
                    }
                    case F2 -> {
                        handleIncreaseQty(null);
                        event.consume();
                    }
                    case F3 -> {
                        handleItemDiscount(null);
                        event.consume();
                    }
                    case F4 -> {
                        if (customerNameField != null) {
                            customerNameField.requestFocus();
                            customerNameField.selectAll();
                        }
                        event.consume();
                    }
                    case F5 -> {
                        handleItemNote(null);
                        event.consume();
                    }
                    case F6 -> {
                        handleHoldCart(null);
                        event.consume();
                    }
                    case F7 -> {
                        handleRecallCart(null);
                        event.consume();
                    }
                    case F8 -> {
                        handleSplitTenderModal(null);
                        event.consume();
                    }
                    case F9 -> {
                        handleQuickExact(null);
                        event.consume();
                    }
                    case F10 -> {
                        handleCheckout(null);
                        event.consume();
                    }
                    case F11 -> {
                        if (giftReceiptCheckBox != null) {
                            giftReceiptCheckBox.setSelected(!giftReceiptCheckBox.isSelected());
                        }
                        event.consume();
                    }
                    case F12 -> {
                        handleClearCart(null);
                        event.consume();
                    }
                    default -> {}
                }
            };

            posRootPane.addEventHandler(KeyEvent.KEY_PRESSED, keyHandler);
        }
    }



    public void setAppliedSplitPayments(List<InvoicePayment> payments) {
        this.appliedSplitPayments = payments;
    }

    public List<InvoicePayment> getAppliedSplitPayments() {
        return appliedSplitPayments;
    }

    public CheckBox getGiftReceiptCheckBox() {
        if (giftReceiptCheckBox == null) giftReceiptCheckBox = new CheckBox();
        return giftReceiptCheckBox;
    }

    @FXML
    public void handleItemDiscount(ActionEvent event) {
        InvoiceItem selected = cartTable != null ? cartTable.getSelectionModel().getSelectedItem() : null;
        if (selected == null) {
            NotificationUtil.showWarning("No Selection", "Please select an item in the cart to apply a line discount.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog(String.format("%.1f", selected.getDiscountPercent()));
        dialog.setTitle("Line Item Discount");
        dialog.setHeaderText("Set Discount for " + selected.getProductName());
        dialog.setContentText("Discount Percentage (%):");
        dialog.showAndWait().ifPresent(val -> {
            try {
                double pct = Double.parseDouble(val.trim());
                if (pct < 0.0 || pct > 100.0) {
                    NotificationUtil.showWarning("Invalid Discount", "Discount must be between 0% and 100%.");
                    return;
                }
                selected.setDiscountPercent(pct);
                double rawTotal = selected.getUnitPrice() * selected.getQuantity();
                double discAmt = Math.round(rawTotal * (pct / 100.0) * 100.0) / 100.0;
                selected.setDiscountAmount(discAmt);
                selected.setLineTotal(rawTotal - discAmt);
                if (cartTable != null) cartTable.refresh();
                recalculateOrder();
            } catch (NumberFormatException ex) {
                NotificationUtil.showError("Invalid Input", "Please enter a valid numeric percentage.");
            }
        });
    }

    @FXML
    public void handleItemNote(ActionEvent event) {
        InvoiceItem selected = cartTable != null ? cartTable.getSelectionModel().getSelectedItem() : null;
        if (selected == null) {
            NotificationUtil.showWarning("No Selection", "Please select an item in the cart to add a note.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog(selected.getNotes() != null ? selected.getNotes() : "");
        dialog.setTitle("Item Note");
        dialog.setHeaderText("Special Instructions / Notes for " + selected.getProductName());
        dialog.setContentText("Note:");
        dialog.showAndWait().ifPresent(note -> {
            selected.setNotes(note.trim());
            if (cartTable != null) cartTable.refresh();
        });
    }

    @FXML
    public void handleSplitTenderModal(ActionEvent event) {
        if (cartItems.isEmpty()) {
            NotificationUtil.showWarning("Empty Cart", "Cannot process payment on an empty cart.");
            return;
        }
        double half = Math.round((currentNetTotal / 2.0) * 100.0) / 100.0;
        double remaining = currentNetTotal - half;
        if (paymentMethodCombo != null) {
            paymentMethodCombo.setValue("Split");
        }
        if (checkoutStatusLabel != null) {
            checkoutStatusLabel.setText(String.format("Split Tender: Cash $%.2f + Card $%.2f", half, remaining));
        }
        NotificationUtil.showInfo("Split Tender Configured",
                String.format("Order split across: Cash: $%.2f, Card: $%.2f", half, remaining));
    }
}
