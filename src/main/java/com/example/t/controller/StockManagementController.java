package com.example.t.controller;

import com.example.t.NotificationUtil;
import com.example.t.Product;
import com.example.t.dao.CategoryDao;
import com.example.t.dao.ProductDao;
import com.example.t.dao.SettingsDao;
import com.example.t.database.DatabaseManager;
import com.example.t.model.BookMetadata;
import com.example.t.model.Category;
import com.example.t.model.StoreSettings;
import com.example.t.service.CsvSyncService;
import com.example.t.service.isbn.IsbnValidator;
import com.example.t.service.isbn.MetadataService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Controller for the complete Product & Stock Management Screen.
 * Provides real-time inventory TableView, dynamic search & category filtering,
 * low-stock threshold warning indicators, and Add/Edit/Restock/Delete workflows.
 */
public class StockManagementController {

    @FXML private TableView<Product> productTable;
    @FXML private TableColumn<Product, Integer> colId;
    @FXML private TableColumn<Product, String> colSku;
    @FXML private TableColumn<Product, String> colName;
    @FXML private TableColumn<Product, String> colCategory;
    @FXML private TableColumn<Product, String> colAuthor;
    @FXML private TableColumn<Product, String> colPublisher;
    @FXML private TableColumn<Product, String> colEdition;
    @FXML private TableColumn<Product, Double> colPurchasePrice;
    @FXML private TableColumn<Product, Double> colPrice;
    @FXML private TableColumn<Product, Integer> colQuantity;
    @FXML private TableColumn<Product, Integer> colMinStock;
    @FXML private TableColumn<Product, String> colStockStatus;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> categoryFilterComboBox;
    @FXML private CheckBox lowStockFilterCheckBox;

    @FXML private Label kpiTotalProducts;
    @FXML private Label kpiTotalUnits;
    @FXML private Label kpiLowStockCount;
    @FXML private Label tableCountLabel;
    @FXML private Label currencyNoteLabel;

    @FXML private Button btnAddProduct;
    @FXML private Button btnEditProduct;
    @FXML private Button btnRestockProduct;
    @FXML private Button btnDeleteProduct;
    @FXML private Button btnRefresh;
    @FXML private Button btnExportCsv;

    private ProductDao productDao;
    private CategoryDao categoryDao;
    private SettingsDao settingsDao;
    private MetadataService metadataService;
    private StoreSettings currentSettings;

    private final ObservableList<Product> masterData = FXCollections.observableArrayList();
    private FilteredList<Product> filteredData;

    @FXML
    public void initialize() {
        if (productDao == null) productDao = new ProductDao();
        if (categoryDao == null) categoryDao = new CategoryDao();
        if (settingsDao == null) settingsDao = new SettingsDao();

        loadStoreSettings();
        setupTableColumns();
        setupFiltering();
        loadCategories();
        loadProducts();
    }

    // --- Dependency Injection for Testing ---

    public void setProductDao(ProductDao productDao) {
        this.productDao = productDao;
    }

    public void setCategoryDao(CategoryDao categoryDao) {
        this.categoryDao = categoryDao;
    }

    public void setSettingsDao(SettingsDao settingsDao) {
        this.settingsDao = settingsDao;
    }

    public void setMetadataService(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    public MetadataService getMetadataService() {
        if (metadataService == null) {
            metadataService = new MetadataService();
        }
        return metadataService;
    }

    private void loadStoreSettings() {
        try {
            if (settingsDao != null) {
                currentSettings = settingsDao.getSettings();
            }
        } catch (SQLException e) {
            currentSettings = StoreSettings.defaultSettings();
        }
        if (currentSettings == null) {
            currentSettings = StoreSettings.defaultSettings();
        }
        if (currencyNoteLabel != null) {
            currencyNoteLabel.setText("Active Currency: " + currentSettings.getCurrencyCode() + " (" + currentSettings.getCurrencySymbol() + ")");
        }
    }

    private void setupTableColumns() {
        if (productTable == null) return;

        productTable.setPlaceholder(new Label("No products found in catalog. Click '+ Add Product' to get started."));

        colId.setCellValueFactory(c -> c.getValue().productIdProperty().asObject());
        colSku.setCellValueFactory(c -> c.getValue().skuProperty());
        colName.setCellValueFactory(c -> c.getValue().productNameProperty());
        colCategory.setCellValueFactory(c -> c.getValue().categoryProperty());
        colAuthor.setCellValueFactory(c -> c.getValue().authorProperty());
        colPublisher.setCellValueFactory(c -> c.getValue().publisherProperty());
        colEdition.setCellValueFactory(c -> c.getValue().editionProperty());

        String symbol = currentSettings != null ? currentSettings.getCurrencySymbol() : "$";

        colPurchasePrice.setCellValueFactory(c -> c.getValue().purchasePriceProperty().asObject());
        colPurchasePrice.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%s%.2f", symbol, item));
                }
            }
        });

        colPrice.setCellValueFactory(c -> c.getValue().priceProperty().asObject());
        colPrice.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%s%.2f", symbol, item));
                }
            }
        });

        colQuantity.setCellValueFactory(c -> c.getValue().quantityProperty().asObject());
        colMinStock.setCellValueFactory(c -> c.getValue().minStockThresholdProperty().asObject());

        // Low-stock badge / visual indicator
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

        colStockStatus.setCellFactory(column -> new TableCell<>() {
            private final Label badge = new Label();
            {
                badge.setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    badge.setText(item);
                    badge.getStyleClass().clear();
                    badge.getStyleClass().add("badge");
                    if ("OUT OF STOCK".equals(item)) {
                        badge.getStyleClass().add("badge-out-of-stock");
                        badge.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c; -fx-padding: 3px 8px; -fx-background-radius: 10px; -fx-font-weight: bold; -fx-font-size: 10px;");
                    } else if ("LOW STOCK".equals(item)) {
                        badge.getStyleClass().add("badge-low-stock");
                        badge.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #b45309; -fx-padding: 3px 8px; -fx-background-radius: 10px; -fx-font-weight: bold; -fx-font-size: 10px;");
                    } else {
                        badge.getStyleClass().add("badge-in-stock");
                        badge.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #15803d; -fx-padding: 3px 8px; -fx-background-radius: 10px; -fx-font-weight: bold; -fx-font-size: 10px;");
                    }
                    setGraphic(badge);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    setAlignment(Pos.CENTER);
                }
            }
        });
    }

    private void setupFiltering() {
        filteredData = new FilteredList<>(masterData, p -> true);

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilter());
        }
        if (categoryFilterComboBox != null) {
            categoryFilterComboBox.valueProperty().addListener((obs, oldVal, newVal) -> applyFilter());
        }
        if (lowStockFilterCheckBox != null) {
            lowStockFilterCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> applyFilter());
        }

        SortedList<Product> sortedData = new SortedList<>(filteredData);
        if (productTable != null) {
            sortedData.comparatorProperty().bind(productTable.comparatorProperty());
            productTable.setItems(sortedData);
        }
    }

    /**
     * Applies search query, category selection, and low-stock filter predicates simultaneously.
     */
    public void applyFilter() {
        String search = searchField != null && searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        String selectedCategory = categoryFilterComboBox != null ? categoryFilterComboBox.getValue() : null;
        boolean lowStockOnly = lowStockFilterCheckBox != null && lowStockFilterCheckBox.isSelected();

        filteredData.setPredicate(product -> {
            // 1. Low stock filter
            if (lowStockOnly && product.getQuantity() > product.getMinStockThreshold()) {
                return false;
            }

            // 2. Category filter
            if (selectedCategory != null && !selectedCategory.isBlank() && !"All Categories".equalsIgnoreCase(selectedCategory)) {
                if (product.getCategory() == null || !product.getCategory().equalsIgnoreCase(selectedCategory)) {
                    return false;
                }
            }

            // 3. Search query
            if (!search.isEmpty()) {
                boolean matchName = product.getProductName() != null && product.getProductName().toLowerCase().contains(search);
                boolean matchSku = product.getSku() != null && product.getSku().toLowerCase().contains(search);
                boolean matchAuthor = product.getAuthor() != null && product.getAuthor().toLowerCase().contains(search);
                boolean matchPublisher = product.getPublisher() != null && product.getPublisher().toLowerCase().contains(search);
                return matchName || matchSku || matchAuthor || matchPublisher;
            }

            return true;
        });

        updateSummaryStatistics();
    }

    public void loadCategories() {
        if (categoryFilterComboBox == null) return;
        ObservableList<String> categories = FXCollections.observableArrayList();
        categories.add("All Categories");

        try {
            if (categoryDao != null) {
                List<Category> list = categoryDao.getAllCategories();
                for (Category cat : list) {
                    if (cat.getName() != null && !cat.getName().isBlank()) {
                        categories.add(cat.getName());
                    }
                }
            }
        } catch (SQLException e) {
            categories.addAll("Books", "Stationery", "Magazines", "Office Supplies");
        }

        categoryFilterComboBox.setItems(categories);
        categoryFilterComboBox.setValue("All Categories");
    }

    public void loadProducts() {
        try {
            if (productDao != null) {
                List<Product> products = productDao.getAllProducts();
                masterData.setAll(products);
                updateSummaryStatistics();
            }
        } catch (SQLException e) {
            NotificationUtil.showError("Database Error", "Failed to load products: " + e.getMessage());
        }
    }

    private void updateSummaryStatistics() {
        int totalProducts = masterData.size();
        int totalUnits = masterData.stream().mapToInt(Product::getQuantity).sum();
        long lowStockCount = masterData.stream().filter(p -> p.getQuantity() <= p.getMinStockThreshold()).count();

        if (kpiTotalProducts != null) kpiTotalProducts.setText(String.valueOf(totalProducts));
        if (kpiTotalUnits != null) kpiTotalUnits.setText(String.valueOf(totalUnits));
        if (kpiLowStockCount != null) kpiLowStockCount.setText(String.valueOf(lowStockCount));

        if (tableCountLabel != null) {
            int showing = filteredData != null ? filteredData.size() : totalProducts;
            tableCountLabel.setText("Showing " + showing + " of " + totalProducts + " products (" + lowStockCount + " items with low-stock warnings)");
        }
    }

    // --- Action Handlers ---

    @FXML
    private void handleAddProduct(ActionEvent event) {
        showProductDialog(null);
    }

    @FXML
    private void handleEditProduct(ActionEvent event) {
        Product selected = getSelectedProduct();
        if (selected == null) {
            NotificationUtil.showWarning("No Product Selected", "Please select a product from the table to edit.");
            return;
        }
        showProductDialog(selected);
    }

    @FXML
    private void handleRestockProduct(ActionEvent event) {
        Product selected = getSelectedProduct();
        if (selected == null) {
            NotificationUtil.showWarning("No Product Selected", "Please select a product from the table to restock.");
            return;
        }
        showRestockDialog(selected);
    }

    @FXML
    private void handleDeleteProduct(ActionEvent event) {
        Product selected = getSelectedProduct();
        if (selected == null) {
            NotificationUtil.showWarning("No Product Selected", "Please select a product from the table to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete Product: " + selected.getProductName());
        confirm.setContentText("Are you sure you want to permanently delete this product?\n\nSKU: "
                + selected.getSku() + "\nCurrent Stock: " + selected.getQuantity());

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                boolean deleted = productDao.delete(selected.getProductId());
                if (deleted) {
                    masterData.remove(selected);
                    updateSummaryStatistics();
                    NotificationUtil.showSuccess("Deleted", "Product '" + selected.getProductName() + "' deleted successfully.");
                } else {
                    NotificationUtil.showError("Delete Failed", "Could not delete product from database.");
                }
            } catch (SQLException e) {
                NotificationUtil.showError("Database Error", "Failed to delete product: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleRefresh(ActionEvent event) {
        loadProducts();
        NotificationUtil.showInfo("Refreshed", "Inventory table refreshed from database.");
    }

    @FXML
    private void handleExportCsv(ActionEvent event) {
        try {
            Path exportPath = DatabaseManager.getExportsDirectory().resolve("products_export.csv");
            CsvSyncService.exportProductsToFile(masterData, exportPath);
            NotificationUtil.showSuccess("CSV Export Successful",
                    "Exported " + masterData.size() + " products to:\n" + exportPath.toAbsolutePath());
        } catch (IOException e) {
            NotificationUtil.showError("Export Error", "Failed to export products to CSV: " + e.getMessage());
        }
    }

    // --- Product Add/Edit Dialog ---

    public void showProductDialog(Product existing) {
        boolean isEdit = existing != null;
        Dialog<Product> dialog = new Dialog<>();
        dialog.setTitle(isEdit ? "Edit Product Details" : "Add New Product");
        dialog.setHeaderText(isEdit ? "Update information for: " + existing.getProductName() : "Add a new Book or Stationery product to the catalog");

        ButtonType saveButtonType = new ButtonType(isEdit ? "Save Changes" : "Add Product", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 24, 10, 24));

        ComboBox<String> categoryCombo = new ComboBox<>();
        categoryCombo.getItems().addAll("Books", "Stationery", "Magazines", "Office Supplies");
        categoryCombo.setValue(isEdit ? existing.getCategory() : "Books");

        TextField skuField = new TextField(isEdit ? existing.getSku() : "BK-" + System.currentTimeMillis() % 100000);
        TextField nameField = new TextField(isEdit ? existing.getProductName() : "");
        nameField.setPromptText("Enter full product title / merchandise name");

        TextField authorField = new TextField(isEdit ? existing.getAuthor() : "");
        authorField.setPromptText("Author (required for Books)");

        TextField publisherField = new TextField(isEdit ? existing.getPublisher() : "");
        publisherField.setPromptText("Publisher / Brand");

        TextField editionField = new TextField(isEdit ? existing.getEdition() : "");
        editionField.setPromptText("Edition or ISBN / Barcode");

        Label isbnStatusLabel = new Label();
        isbnStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");
        ImageView coverPreview = new ImageView();
        coverPreview.setFitWidth(50);
        coverPreview.setFitHeight(70);
        coverPreview.setPreserveRatio(true);

        Runnable performIsbnLookup = () -> {
            String text = editionField.getText();
            if (text == null || text.isBlank()) {
                text = skuField.getText();
            }
            if (text != null && IsbnValidator.isValid(text)) {
                String clean = IsbnValidator.clean(text);
                isbnStatusLabel.setText("Valid " + (clean.length() == 13 ? "ISBN-13" : "ISBN-10") + ". Fetching metadata...");
                isbnStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-accent-emphasis;");
                getMetadataService().lookupAsync(clean).thenAccept(opt -> Platform.runLater(() -> {
                    if (opt.isPresent()) {
                        BookMetadata meta = opt.get();
                        if (nameField.getText() == null || nameField.getText().isBlank() || (!isEdit && nameField.getText().trim().isEmpty())) {
                            nameField.setText(meta.getTitle() + (meta.getSubtitle() != null && !meta.getSubtitle().isBlank() ? ": " + meta.getSubtitle() : ""));
                        }
                        if (authorField.getText() == null || authorField.getText().isBlank() || (!isEdit && authorField.getText().trim().isEmpty())) {
                            if (meta.getAuthors() != null) authorField.setText(meta.getAuthors());
                        }
                        if (publisherField.getText() == null || publisherField.getText().isBlank() || (!isEdit && publisherField.getText().trim().isEmpty())) {
                            if (meta.getPublisher() != null) publisherField.setText(meta.getPublisher());
                        }
                        categoryCombo.setValue("Books");
                        isbnStatusLabel.setText("Enriched: " + meta.getTitle() + " (" + meta.getSource() + ")");
                        isbnStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-success-fg;");

                        if (meta.getCoverImagePath() != null && !meta.getCoverImagePath().isBlank()) {
                            try {
                                coverPreview.setImage(new Image(new File(meta.getCoverImagePath()).toURI().toString(), true));
                            } catch (Exception ignored) {}
                        } else if (meta.getCoverImageUrl() != null && !meta.getCoverImageUrl().isBlank()) {
                            try {
                                coverPreview.setImage(new Image(meta.getCoverImageUrl(), true));
                            } catch (Exception ignored) {}
                        }
                    } else {
                        isbnStatusLabel.setText("Valid ISBN (" + (clean.length() == 13 ? "ISBN-13" : "ISBN-10") + "), offline/not found");
                        isbnStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-fg-muted;");
                    }
                }));
            } else if (text != null && !text.isBlank() && text.trim().length() >= 9) {
                isbnStatusLabel.setText("Invalid ISBN checksum");
                isbnStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-danger-fg;");
            } else {
                isbnStatusLabel.setText("");
            }
        };

        editionField.textProperty().addListener((obs, oldVal, newVal) -> performIsbnLookup.run());
        skuField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (editionField.getText() == null || editionField.getText().isBlank()) {
                performIsbnLookup.run();
            }
        });

        if (isEdit && existing != null && existing.getEdition() != null && IsbnValidator.isValid(existing.getEdition())) {
            performIsbnLookup.run();
        }

        TextField purchasePriceField = new TextField(isEdit ? String.valueOf(existing.getPurchasePrice()) : "0.00");
        TextField priceField = new TextField(isEdit ? String.valueOf(existing.getPrice()) : "0.00");
        TextField quantityField = new TextField(isEdit ? String.valueOf(existing.getQuantity()) : "10");
        TextField minStockField = new TextField(isEdit ? String.valueOf(existing.getMinStockThreshold()) : "5");

        // Dynamic adjustment for Stationery vs Books
        categoryCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isBook = "Books".equalsIgnoreCase(newVal);
            authorField.setDisable(!isBook);
            editionField.setPromptText(isBook ? "Edition or ISBN" : "Barcode / Model");
            if (!isEdit && !isBook && skuField.getText().startsWith("BK-")) {
                skuField.setText("ST-" + System.currentTimeMillis() % 100000);
            } else if (!isEdit && isBook && skuField.getText().startsWith("ST-")) {
                skuField.setText("BK-" + System.currentTimeMillis() % 100000);
            }
        });

        int row = 0;
        grid.add(new Label("Category:"), 0, row);
        grid.add(categoryCombo, 1, row++);

        grid.add(new Label("SKU / Barcode:"), 0, row);
        grid.add(skuField, 1, row++);

        grid.add(new Label("Product Name: *"), 0, row);
        grid.add(nameField, 1, row++);

        grid.add(new Label("Author (Books):"), 0, row);
        grid.add(authorField, 1, row++);

        grid.add(new Label("Publisher / Brand:"), 0, row);
        grid.add(publisherField, 1, row++);

        HBox editionBox = new HBox(8, editionField, coverPreview);
        editionBox.setAlignment(Pos.CENTER_LEFT);
        VBox editionContainer = new VBox(2, editionBox, isbnStatusLabel);

        grid.add(new Label("Edition / ISBN:"), 0, row);
        grid.add(editionContainer, 1, row++);

        grid.add(new Label("Purchase Cost:"), 0, row);
        grid.add(purchasePriceField, 1, row++);

        grid.add(new Label("Selling Price: *"), 0, row);
        grid.add(priceField, 1, row++);

        grid.add(new Label("Stock Quantity: *"), 0, row);
        grid.add(quantityField, 1, row++);

        grid.add(new Label("Min Stock Threshold:"), 0, row);
        grid.add(minStockField, 1, row++);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                String name = nameField.getText() != null ? nameField.getText().trim() : "";
                if (name.isEmpty()) {
                    NotificationUtil.showError("Validation Error", "Product Name is required.");
                    return null;
                }

                double purchasePrice;
                double price;
                int quantity;
                int minStock;

                try {
                    purchasePrice = Double.parseDouble(purchasePriceField.getText().trim());
                    price = Double.parseDouble(priceField.getText().trim());
                    quantity = Integer.parseInt(quantityField.getText().trim());
                    minStock = Integer.parseInt(minStockField.getText().trim());
                } catch (NumberFormatException e) {
                    NotificationUtil.showError("Validation Error", "Please enter valid numeric values for prices and stock counts.");
                    return null;
                }

                if (price < 0 || purchasePrice < 0 || quantity < 0 || minStock < 0) {
                    NotificationUtil.showError("Validation Error", "Prices and stock quantities cannot be negative.");
                    return null;
                }

                String sku = skuField.getText() != null ? skuField.getText().trim() : "";
                String category = categoryCombo.getValue();
                String author = authorField.getText() != null ? authorField.getText().trim() : "";
                String publisher = publisherField.getText() != null ? publisherField.getText().trim() : "";
                String edition = editionField.getText() != null ? editionField.getText().trim() : "";

                if (isEdit) {
                    existing.setSku(sku);
                    existing.setProductName(name);
                    existing.setCategory(category);
                    existing.setAuthor(author);
                    existing.setPublisher(publisher);
                    existing.setEdition(edition);
                    existing.setPurchasePrice(purchasePrice);
                    existing.setPrice(price);
                    existing.setQuantity(quantity);
                    existing.setMinStockThreshold(minStock);
                    return existing;
                } else {
                    return new Product(0, name, category, sku, author, publisher, edition,
                            purchasePrice, price, quantity, minStock);
                }
            }
            return null;
        });

        Optional<Product> result = dialog.showAndWait();
        result.ifPresent(product -> {
            try {
                if (isEdit) {
                    boolean ok = productDao.update(product);
                    if (ok) {
                        productTable.refresh();
                        updateSummaryStatistics();
                        NotificationUtil.showSuccess("Product Updated", "Updated: " + product.getProductName());
                    } else {
                        NotificationUtil.showError("Update Failed", "Database update failed.");
                    }
                } else {
                    int newId = productDao.save(product);
                    if (newId > 0) {
                        masterData.add(product);
                        updateSummaryStatistics();
                        NotificationUtil.showSuccess("Product Added", "Successfully added '" + product.getProductName() + "' (ID: " + newId + ")");
                    }
                }
            } catch (SQLException e) {
                NotificationUtil.showError("Database Error", "Failed to save product: " + e.getMessage());
            }
        });
    }

    // --- Restock Dialog ---

    public void showRestockDialog(Product product) {
        Dialog<Integer> dialog = new Dialog<>();
        dialog.setTitle("Restock Inventory");
        dialog.setHeaderText("Add additional stock for: " + product.getProductName() + " (SKU: " + product.getSku() + ")");

        ButtonType restockButtonType = new ButtonType("Confirm Restock", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(restockButtonType, ButtonType.CANCEL);

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));

        Label currentStockLabel = new Label("Current Stock: " + product.getQuantity() + " units (Min Threshold: " + product.getMinStockThreshold() + ")");
        currentStockLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        TextField addQtyField = new TextField("10");
        addQtyField.setPromptText("Enter units to add (e.g. 10, 50, 100)");

        content.getChildren().addAll(currentStockLabel, new Label("Quantity to Add:"), addQtyField);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(button -> {
            if (button == restockButtonType) {
                try {
                    int addQty = Integer.parseInt(addQtyField.getText().trim());
                    if (addQty <= 0) {
                        NotificationUtil.showError("Invalid Quantity", "Restock quantity must be greater than zero.");
                        return null;
                    }
                    return addQty;
                } catch (NumberFormatException e) {
                    NotificationUtil.showError("Invalid Input", "Please enter a valid whole number.");
                    return null;
                }
            }
            return null;
        });

        Optional<Integer> result = dialog.showAndWait();
        result.ifPresent(qtyToAdd -> {
            try {
                boolean ok = productDao.restockProduct(product.getProductId(), qtyToAdd);
                if (ok) {
                    product.setQuantity(product.getQuantity() + qtyToAdd);
                    productTable.refresh();
                    updateSummaryStatistics();
                    NotificationUtil.showSuccess("Restocked", "Added " + qtyToAdd + " units to " + product.getProductName() + ". New stock: " + product.getQuantity());
                } else {
                    NotificationUtil.showError("Restock Failed", "Database failed to restock item.");
                }
            } catch (SQLException e) {
                NotificationUtil.showError("Database Error", "Failed to restock: " + e.getMessage());
            }
        });
    }

    private Product getSelectedProduct() {
        return productTable != null ? productTable.getSelectionModel().getSelectedItem() : null;
    }

    // --- Getters for Access & Testing ---

    public ObservableList<Product> getMasterData() {
        return masterData;
    }

    public FilteredList<Product> getFilteredData() {
        return filteredData;
    }

    public TableView<Product> getProductTable() {
        return productTable;
    }

    public TextField getSearchField() {
        return searchField;
    }

    public ComboBox<String> getCategoryFilterComboBox() {
        return categoryFilterComboBox;
    }

    public CheckBox getLowStockFilterCheckBox() {
        return lowStockFilterCheckBox;
    }

    public Label getKpiTotalProducts() {
        return kpiTotalProducts;
    }

    public Label getKpiTotalUnits() {
        return kpiTotalUnits;
    }

    public Label getKpiLowStockCount() {
        return kpiLowStockCount;
    }
}
