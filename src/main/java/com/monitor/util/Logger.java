package com.monitor.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe logging utility that writes logs to console, disk (logs/monitor.log),
 * and notifies active UI LogListeners.
 */
public class Logger {

    public enum Level {
        INFO,
        WARN,
        ERROR,
        ALERT
    }

    public interface LogListener {
        void onLog(String timestamp, Level level, String message);
    }

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final String LOG_DIR = "logs";
    private static final String LOG_FILE = "logs/monitor.log";

    private static final List<LogListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Object FILE_LOCK = new Object();

    static {
        File dir = new File(LOG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public static void addListener(LogListener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(LogListener listener) {
        LISTENERS.remove(listener);
    }

    public static void info(String message) {
        log(Level.INFO, message);
    }

    public static void warn(String message) {
        log(Level.WARN, message);
    }

    public static void error(String message) {
        log(Level.ERROR, message);
    }

    public static void alert(String message) {
        log(Level.ALERT, message);
    }

    public static void log(Level level, String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        String threadName = Thread.currentThread().getName();
        String formattedLine = String.format("[%s] [%-5s] [%s] %s",
                timestamp, level.name(), threadName, message);

        // 1. Console Output
        if (level == Level.ERROR || level == Level.ALERT) {
            System.err.println(formattedLine);
        } else {
            System.out.println(formattedLine);
        }

        // 2. File Output
        synchronized (FILE_LOCK) {
            try (FileWriter fw = new FileWriter(LOG_FILE, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(formattedLine);
            } catch (IOException e) {
                System.err.println("Failed to write log to " + LOG_FILE + ": " + e.getMessage());
            }
        }

        // 3. Notify Listeners (Swing UI)
        for (LogListener listener : LISTENERS) {
            try {
                listener.onLog(timestamp, level, message);
            } catch (Exception ignored) {
            }
        }
    }
}
