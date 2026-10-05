package com.monitor;

import com.formdev.flatlaf.FlatDarkLaf;
import com.monitor.gui.Dashboard;
import com.monitor.model.Host;
import com.monitor.scheduler.MonitorScheduler;
import com.monitor.util.ConfigManager;
import com.monitor.util.Logger;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.util.List;

/**
 * Main application entrypoint for Network Monitor.
 */
public class Main {

    public static void main(String[] args) {
        Logger.info("Starting Network Monitor v1.0.0...");

        // Check if headless or CLI argument requested
        boolean isHeadless = GraphicsEnvironment.isHeadless() ||
                (args.length > 0 && ("--cli".equalsIgnoreCase(args[0]) || "--headless".equalsIgnoreCase(args[0])));

        // Load configured hosts
        List<Host> hosts = ConfigManager.loadHosts();

        // Create scheduler with worker thread pool
        int workerCount = Math.max(4, Runtime.getRuntime().availableProcessors());
        MonitorScheduler scheduler = new MonitorScheduler(hosts, workerCount);

        // Register JVM Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Logger.info("Application shutting down...");
            scheduler.shutdown();
        }, "shutdown-hook"));

        if (!isHeadless) {
            // Setup modern FlatLaf dark theme for Swing
            try {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } catch (Exception e) {
                Logger.warn("FlatLaf could not be initialized; using system default Look & Feel: " + e.getMessage());
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                }
            }

            SwingUtilities.invokeLater(() -> {
                Dashboard dashboard = new Dashboard(scheduler);
                dashboard.setVisible(true);
                // Start scheduler once GUI is ready
                scheduler.start();
            });
        } else {
            Logger.info("Running in headless / CLI mode. Press Ctrl+C to terminate.");
            scheduler.start();

            // Keep main thread alive in CLI mode
            try {
                while (true) {
                    Thread.sleep(10000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
