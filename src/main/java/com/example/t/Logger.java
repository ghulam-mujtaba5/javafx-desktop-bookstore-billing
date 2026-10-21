package com.example.t;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Robust, lightweight application logging utility.
 * Formats messages with timestamps, log levels, and thread context.
 */
public class Logger {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public enum Level {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    private static Level currentLevel = Level.INFO;

    public static void setLevel(Level level) {
        if (level != null) {
            currentLevel = level;
        }
    }

    public static Level getLevel() {
        return currentLevel;
    }

    public static void debug(String message) {
        log(Level.DEBUG, message, null);
    }

    public static void debug(String message, Object... args) {
        log(Level.DEBUG, format(message, args), null);
    }

    public static void info(String message) {
        log(Level.INFO, message, null);
    }

    public static void info(String message, Object... args) {
        log(Level.INFO, format(message, args), null);
    }

    public static void warn(String message) {
        log(Level.WARN, message, null);
    }

    public static void warn(String message, Object... args) {
        log(Level.WARN, format(message, args), null);
    }

    public static void error(String message) {
        log(Level.ERROR, message, null);
    }

    public static void error(String message, Throwable t) {
        log(Level.ERROR, message, t);
    }

    private static void log(Level level, String message, Throwable t) {
        if (level.ordinal() < currentLevel.ordinal()) {
            return;
        }

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String threadName = Thread.currentThread().getName();
        String formattedMessage = String.format("[%s] [%s] [%-5s] %s",
                timestamp, threadName, level.name(), message != null ? message : "");

        PrintStream target = (level == Level.ERROR || level == Level.WARN) ? System.err : System.out;
        target.println(formattedMessage);

        if (t != null) {
            t.printStackTrace(target);
        }
    }

    private static String format(String template, Object... args) {
        if (template == null || args == null || args.length == 0) {
            return template;
        }
        try {
            return String.format(template, args);
        } catch (Exception e) {
            return template + " " + java.util.Arrays.toString(args);
        }
    }
}
