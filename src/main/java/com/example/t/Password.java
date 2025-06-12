package com.example.t;

import java.io.*;
import javafx.scene.control.Alert;

/**
 * Handles password storage, verification, and updates for the application.
 * Passwords are stored in plain text (no hashing).
 */
public class Password {
    /** The plain text password string. */
    private String password;
    private final String DEFAULT_PASSWORD = "book shop";
    private final String FILE_PATH = FilePathManager.getPasswordFilePath();

    /**
     * Loads the password from file or sets a default if not present.
     */
    public Password() {
        loadPasswordFromFile();
    }

    /**
     * Verifies if the input password is correct.
     * @param inputPassword The password to check.
     * @return true if the password is correct, false otherwise.
     */
    public boolean isCorrect(String inputPassword) {
        return password != null && password.equals(inputPassword);
    }

    /**
     * Changes the password and saves to file.
     * @param newPassword The new password to set.
     */
    public void changePassword(String newPassword) {
        password = newPassword;
        savePasswordToFile();
    }

    /**
     * Loads the password from file, or sets and saves a default password if file does not exist.
     */
    private void loadPasswordFromFile() {
        try {
            File file = new File(FILE_PATH);
            if (file.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    password = reader.readLine();
                }
            } else {
                password = DEFAULT_PASSWORD;
                savePasswordToFile();
            }
        } catch (IOException e) {
            Logger.error("Failed to load password from file: " + e.getMessage());
            showErrorDialog("Failed to load password. Using default password.");
            password = DEFAULT_PASSWORD;
        }
    }

    /**
     * Saves the password to file.
     */
    private void savePasswordToFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_PATH))) {
            writer.write(password);
        } catch (IOException e) {
            Logger.error("Failed to save password to file: " + e.getMessage());
            showErrorDialog("Failed to save password. Please check file permissions.");
        }
    }

    /**
     * Shows a user-friendly error dialog for password-related errors.
     * @param message The error message to display.
     */
    private void showErrorDialog(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Password Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
