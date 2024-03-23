package com.example.t;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class SettingsScreen extends Stage {
    private Password password;

    public SettingsScreen(Password password) {
        this.password = password;
    }

    public void openSettingsScreen(Stage primaryStage, Shop shop) {
        shop.loadData(); // Load shop data from file

        // Create the settings screen content
        GridPane settingsContent = new GridPane();
        settingsContent.setAlignment(Pos.CENTER);
        settingsContent.setHgap(10);
        settingsContent.setVgap(10);

        // Password change section
        Label changePasswordLabel = new Label("Change Password:");
        changePasswordLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        settingsContent.add(changePasswordLabel, 0, 0, 2, 1);

        Label currentPasswordLabel = new Label("Current Password:");
        PasswordField currentPasswordField = new PasswordField();
        settingsContent.add(currentPasswordLabel, 0, 1);
        settingsContent.add(currentPasswordField, 1, 1);

        Label newPasswordLabel = new Label("New Password:");
        PasswordField newPasswordField = new PasswordField();
        settingsContent.add(newPasswordLabel, 0, 2);
        settingsContent.add(newPasswordField, 1, 2);

        Button changePasswordButton = new Button("Change Password");
        changePasswordButton.setOnAction(event -> {
            String currentPassword = currentPasswordField.getText();
            String newPassword = newPasswordField.getText();
            if (password.isCorrect(currentPassword)) {
                password.changePassword(newPassword);
                System.out.println("Password changed successfully.");
                close();
            } else {
                System.out.println("Incorrect current password. Please try again.");
            }
        });
        settingsContent.add(changePasswordButton, 1, 3);

        // Shop attributes section
        Label changeShopAttributesLabel = new Label("Change Shop Attributes:");
        changeShopAttributesLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        settingsContent.add(changeShopAttributesLabel, 0, 4, 2, 1);

        Label nameLabel = new Label("Name:");
        TextField nameField = new TextField(shop.getShopName());
        settingsContent.add(nameLabel, 0, 5);
        settingsContent.add(nameField, 1, 5);

        Label addressLabel = new Label("Address:");
        TextField addressField = new TextField(shop.getAddress());
        settingsContent.add(addressLabel, 0, 6);
        settingsContent.add(addressField, 1, 6);

        Label contactLabel = new Label("Contact:");
        TextField contactField = new TextField(shop.getMobileNumber());
        settingsContent.add(contactLabel, 0, 7);
        settingsContent.add(contactField, 1, 7);

        Button saveShopAttributesButton = new Button("Save");
        saveShopAttributesButton.setOnAction(event -> {
            String name = nameField.getText();
            String address = addressField.getText();
            String contact = contactField.getText();
            shop.setShopName(name);
            shop.setAddress(address);
            shop.setMobileNumber(contact);
            System.out.println("Shop attributes saved successfully.");
            close();
        });
        settingsContent.add(saveShopAttributesButton, 1, 8);

        // Set the scene
        Scene scene = new Scene(settingsContent, 400, 300);
        setScene(scene);

        // Apply a blur effect to the main menu screen while the settings screen is open
        GaussianBlur blurEffect = new GaussianBlur();
        primaryStage.getScene().getRoot().setEffect(blurEffect);

        // Set the modality and owner of the settings screen
        initModality(Modality.WINDOW_MODAL);
        initOwner(primaryStage);

        // Show the settings screen
        showAndWait();

        // Remove the blur effect from the main menu screen after the settings screen is closed
        primaryStage.getScene().getRoot().setEffect(null);
    }
}
