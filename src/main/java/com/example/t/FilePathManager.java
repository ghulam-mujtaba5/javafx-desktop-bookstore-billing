package com.example.t;

import java.io.File;

public class FilePathManager {
    private static final String APP_NAME = "GM Shop";
    private static final String APP_DATA_PATH = System.getenv("APPDATA") + File.separator + APP_NAME;
    private static final String STOCK_FILE_PATH ="D:\\t\\src\\main\\stock.txt";
    private static final String BACKGROUND_IMAGE_PATH = "C:\\Program Files (x86)\\GM Shop\\src\\main\\background1.jpg";
    private static final String SETTINGS_ICON_PATH = "C:\\Program Files (x86)\\GM Shop\\src\\main\\setting_icon.png";
    private static final String PASSWORD_FILE_PATH = APP_DATA_PATH + File.separator + "password.txt";
    private static final String INVOICE_HISTORY_FILE_PATH = APP_DATA_PATH + File.separator + "invoice_history.txt";

    private static final String SHOP_DATA_FILE_PATH = APP_DATA_PATH + File.separator + "shop_data.txt";

    // Add more file paths if needed

    public static String getStockFilePath() {
        return STOCK_FILE_PATH;
    }

    public static String getBackgroundImagePath() {
        return BACKGROUND_IMAGE_PATH;
    }

    public static String getSettingsIconPath() {
        return SETTINGS_ICON_PATH;
    }

    public static String getPasswordFilePath() {
        return PASSWORD_FILE_PATH;
    }

    public static String getInvoiceHistoryFilePath() {
        return INVOICE_HISTORY_FILE_PATH;
    }

    public static String getShopDataFilePath() {
        return SHOP_DATA_FILE_PATH;
    }

    // Add more static methods to retrieve other file paths
}
