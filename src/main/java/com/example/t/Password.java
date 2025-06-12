package com.example.t;

import java.io.*;

public class Password {
    public String password;
    private final String DEFAULT_PASSWORD = "book shop";
    private final String FILE_PATH = FilePathManager.getPasswordFilePath();

    public Password() {
        loadPasswordFromFile();
    }

    public boolean isCorrect(String inputPassword) {
        return password.equals(inputPassword);
    }

    public void changePassword(String newPassword) {
        password = newPassword;
        savePasswordToFile();
    }

    private void loadPasswordFromFile() {
        try {
            File file = new File(FILE_PATH);
            if (file.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                password = reader.readLine();
                reader.close();
            } else {
                password = DEFAULT_PASSWORD;
                savePasswordToFile();
            }
        } catch (IOException e) {
            System.err.println("Failed to load password from file: " + e.getMessage());
            password = DEFAULT_PASSWORD;
        }
    }

    private void savePasswordToFile() {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_PATH));
            writer.write(password);
            writer.close();
        } catch (IOException e) {
            System.err.println("Failed to save password to file: " + e.getMessage());
        }
    }
}
