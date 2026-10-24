package com.example.t.util;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.example.t.Invoice;
import com.example.t.Product;
import com.example.t.controller.MainShellController;
import com.example.t.controller.PosBillingController;
import com.example.t.dao.CategoryDao;
import com.example.t.dao.InvoiceDao;
import com.example.t.dao.ProductDao;
import com.example.t.dao.SettingsDao;
import com.example.t.database.DatabaseManager;
import com.example.t.model.Category;
import com.example.t.model.StoreSettings;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * High-Fidelity Real Store Screenshot Generator.
 * Renders the actual JavaFX application windows at pixel-perfect 1920x1080 resolution,
 * populated with realistic bookstore and stationery data, and outputs production screenshots.
 */
public class StoreScreenshotCaptureTest {

    private static Path tempDir;

    @BeforeAll
    public static void initPlatform() throws IOException {
        tempDir = Files.createTempDirectory("store_capture_db_");
        System.setProperty("bookstore.data.dir", tempDir.toAbsolutePath().toString());
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already initialized
        }
    }

    private void runOnFx(Runnable action) throws Exception {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                action.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        future.get(90, TimeUnit.SECONDS);
    }

    private void saveScreenshot(Scene scene, String filename) throws IOException {
        int width = (int) scene.getWidth();
        int height = (int) scene.getHeight();
        WritableImage fxImage = new WritableImage(width, height);
        scene.snapshot(fxImage);

        BufferedImage bImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        PixelReader reader = fxImage.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                bImage.setRGB(x, y, reader.getArgb(x, y));
            }
        }

        Path outputDir = Paths.get("target", "store_assets");
        Files.createDirectories(outputDir);
        File outputFile = outputDir.resolve(filename).toFile();
        ImageIO.write(bImage, "png", outputFile);
        System.out.println("Saved real screenshot: " + outputFile.getAbsolutePath() + " (" + width + "x" + height + ")");
        assertTrue(outputFile.exists() && outputFile.length() > 10000, "Screenshot file must exist and be valid PNG");
    }

    @Test
    public void captureRealAppScreenshots() throws Exception {
        // 1. Initialize SQLite schema & seed realistic data
        DatabaseManager.initializeDatabase();

        CategoryDao catDao = new CategoryDao();
        Category catFiction = new Category("Fiction & Literature", "Popular fiction and classics");
        Category catNonFiction = new Category("Personal Development", "Self-help and professional guides");
        Category catSciFi = new Category("Sci-Fi & Fantasy", "Science fiction and fantasy novels");
        Category catStationery = new Category("Fine Stationery", "Writing instruments, journals and paper");

        catDao.save(catFiction);
        catDao.save(catNonFiction);
        catDao.save(catSciFi);
        catDao.save(catStationery);

        ProductDao prodDao = new ProductDao();
        Product p1 = new Product(0, "Atomic Habits: An Easy & Proven Way to Build Good Habits", "Personal Development", "9780735211292", "James Clear", "Avery", "1st Edition", 18.00, 27.00, 45, 10);
        Product p2 = new Product(0, "Clean Code: A Handbook of Agile Software Craftsmanship", "Personal Development", "9780132350884", "Robert C. Martin", "Prentice Hall", "1st Edition", 30.00, 44.99, 28, 5);
        Product p3 = new Product(0, "Dune: Deluxe Collector's Edition", "Sci-Fi & Fantasy", "9780441013593", "Frank Herbert", "Ace Hardcover", "Deluxe", 24.00, 36.00, 18, 5);
        Product p4 = new Product(0, "Project Hail Mary", "Sci-Fi & Fantasy", "9780593135204", "Andy Weir", "Ballantine Books", "1st Edition", 19.50, 28.99, 32, 8);
        Product p5 = new Product(0, "Moleskine Classic Hardcover Dotted Notebook", "Fine Stationery", "STAT-MOL-001", "Moleskine", "Moleskine SpA", "Black Large", 14.00, 22.95, 60, 15);
        Product p6 = new Product(0, "Parker Jotter Stainless Steel Ballpoint Pen", "Fine Stationery", "STAT-PRK-002", "Parker", "Newell Brands", "Chrome Trim", 10.50, 16.50, 40, 10);
        Product p7 = new Product(0, "Oxford A4 Wirebound Ruled Notebook 5-Pack", "Fine Stationery", "STAT-OXF-003", "Oxford", "Hamelin Brands", "8mm Ruled", 11.00, 18.50, 35, 12);

        prodDao.save(p1);
        prodDao.save(p2);
        prodDao.save(p3);
        prodDao.save(p4);
        prodDao.save(p5);
        prodDao.save(p6);
        prodDao.save(p7);

        SettingsDao settingsDao = new SettingsDao();
        StoreSettings settings = settingsDao.getSettings();
        if (settings == null) settings = new StoreSettings();
        settings.setStoreName("Grand Central Bookstore & Fine Stationery");
        settings.setCurrencySymbol("$");
        settings.setTaxRate(8.25);
        settings.setUiTheme("LIGHT");
        settingsDao.saveSettings(settings);

        // 2. Render and capture on FX Thread
        runOnFx(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/t/fxml/MainShell.fxml"));
                Parent root = loader.load();
                MainShellController shell = loader.getController();

                Scene scene = new Scene(root, 1920, 1080);
                URL cssUrl = getClass().getResource("/com/example/t/css/modern-fluent.css");
                if (cssUrl != null) {
                    scene.getStylesheets().add(cssUrl.toExternalForm());
                }
                ThemeManager.applyTheme(scene, ThemeManager.THEME_LIGHT, null);

                // --- SCREENSHOT 1: POS Billing Screen with active shopping cart ---
                shell.navigateTo(MainShellController.ViewType.POS_BILLING);
                PosBillingController pos = shell.getPosBillingController();
                if (pos != null) {
                    pos.addProductToCart(p1, 1);
                    pos.addProductToCart(p5, 2);
                    pos.addProductToCart(p6, 1);
                }
                root.applyCss();
                root.layout();

                saveScreenshot(scene, "real_screenshot_01_pos_checkout.png");

                // --- SCREENSHOT 2: Stock Management / Book Catalog Screen ---
                shell.navigateTo(MainShellController.ViewType.STOCK_MANAGEMENT);
                root.applyCss();
                root.layout();

                saveScreenshot(scene, "real_screenshot_02_inventory_catalog.png");

                // --- SCREENSHOT 3: Analytics & Reports Dashboard Screen ---
                shell.navigateTo(MainShellController.ViewType.REPORTS_DASHBOARD);
                root.applyCss();
                root.layout();

                saveScreenshot(scene, "real_screenshot_03_reports_analytics.png");

                // --- SCREENSHOT 4: Dark Fluent Theme POS Screen ---
                ThemeManager.applyTheme(scene, ThemeManager.THEME_DARK, null);
                shell.navigateTo(MainShellController.ViewType.POS_BILLING);
                root.applyCss();
                root.layout();

                saveScreenshot(scene, "real_screenshot_04_pos_dark_mode.png");

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
