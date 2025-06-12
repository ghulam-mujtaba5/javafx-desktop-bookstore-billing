package com.example.t;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    public static void info(String message) {
        log("INFO", message);
    }
    public static void warn(String message) {
        log("WARN", message);
    }
    public static void error(String message) {
        log("ERROR", message);
    }
    private static void log(String level, String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println("[" + timestamp + "] [" + level + "] " + message);
    }
}
