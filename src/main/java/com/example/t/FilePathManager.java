package com.example.t;

import com.example.t.database.DatabaseManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages runtime file paths using AppData resolution with fallback to source directories.
 * Eliminates hardcoded drive paths (D:\, C:\) for full portability.
 */
public class FilePathManager {

    public static String getStockFilePath() {
        Path appDataStock = DatabaseManager.getDataDirectory().resolve("stock.txt");
        if (Files.exists(appDataStock)) {
            return appDataStock.toAbsolutePath().toString();
        }
        Path srcStock = Paths.get("src", "main", "stock.txt");
        if (Files.exists(srcStock)) {
            return srcStock.toAbsolutePath().toString();
        }
        return appDataStock.toAbsolutePath().toString();
    }

    public static String getBackgroundImagePath() {
        Path appDataImg = DatabaseManager.getDataDirectory().resolve("background1.jpg");
        if (Files.exists(appDataImg)) {
            return appDataImg.toUri().toString();
        }
        Path srcImg = Paths.get("src", "main", "background1.jpg");
        if (Files.exists(srcImg)) {
            return srcImg.toUri().toString();
        }
        return appDataImg.toUri().toString();
    }

    public static String getSettingsIconPath() {
        Path appDataIcon = DatabaseManager.getDataDirectory().resolve("setting_icon.png");
        if (Files.exists(appDataIcon)) {
            return appDataIcon.toUri().toString();
        }
        Path srcIcon = Paths.get("src", "main", "setting_icon.png");
        if (Files.exists(srcIcon)) {
            return srcIcon.toUri().toString();
        }
        return appDataIcon.toUri().toString();
    }

    public static String getPasswordFilePath() {
        Path appDataPassword = DatabaseManager.getDataDirectory().resolve("password.txt");
        if (Files.exists(appDataPassword)) {
            return appDataPassword.toAbsolutePath().toString();
        }
        Path srcPassword = Paths.get("src", "main", "password.txt");
        if (Files.exists(srcPassword)) {
            return srcPassword.toAbsolutePath().toString();
        }
        return appDataPassword.toAbsolutePath().toString();
    }

    public static String getInvoiceHistoryFilePath() {
        return DatabaseManager.getDataDirectory().resolve("invoice_history.txt").toAbsolutePath().toString();
    }

    public static String getShopDataFilePath() {
        Path appDataShop = DatabaseManager.getDataDirectory().resolve("shop_data.txt");
        if (Files.exists(appDataShop)) {
            return appDataShop.toAbsolutePath().toString();
        }
        Path srcShop = Paths.get("src", "main", "Shop_Data.txt");
        if (Files.exists(srcShop)) {
            return srcShop.toAbsolutePath().toString();
        }
        return appDataShop.toAbsolutePath().toString();
    }
}
