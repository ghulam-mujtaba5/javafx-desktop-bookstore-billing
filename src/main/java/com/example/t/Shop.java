package com.example.t;

import java.io.*;

public class Shop {
    private String shopName;
    private String address;
    private String mobileNumber;
    private static final String FILE_PATH = FilePathManager.getShopDataFilePath();
    private static final String DEFAULT_SHOP_NAME = "Shop Name";
    private static final String DEFAULT_ADDRESS = "Address";
    private static final String DEFAULT_MOBILE_NUMBER = "0300 000000";

    public Shop(String shopName, String address, String mobileNumber) {
        this.shopName = shopName;
        this.address = address;
        this.mobileNumber = mobileNumber;
    }

    public Shop() {
        this.shopName = DEFAULT_SHOP_NAME;
        this.address = DEFAULT_ADDRESS;
        this.mobileNumber = DEFAULT_MOBILE_NUMBER;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
        saveData();
    }

    public void setAddress(String address) {
        this.address = address;
        saveData();
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
        saveData();
    }

    public String getShopName() {
        return shopName;
    }

    public String getAddress() {
        return address;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void saveData() {
        File file = new File(FILE_PATH);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(shopName);
            writer.newLine();
            writer.write(address);
            writer.newLine();
            writer.write(mobileNumber);
        } catch (IOException e) {
            Logger.error("Error saving shop data: " + e.getMessage());
        }
    }

    public void loadData() {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            shopName = DEFAULT_SHOP_NAME;
            address = DEFAULT_ADDRESS;
            mobileNumber = DEFAULT_MOBILE_NUMBER;
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            if ((line = reader.readLine()) != null) {
                shopName = line;
            }
            if ((line = reader.readLine()) != null) {
                address = line;
            }
            if ((line = reader.readLine()) != null) {
                mobileNumber = line;
            }
        } catch (IOException e) {
            Logger.error("Error loading shop data: " + e.getMessage());
        }
    }
}
