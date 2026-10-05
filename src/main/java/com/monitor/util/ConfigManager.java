package com.monitor.util;

import com.monitor.model.Host;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;

/**
 * Manages loading and saving configured hosts to/from properties configuration files.
 */
public class ConfigManager {

    public static final String DEFAULT_CONFIG_PATH = "config/hosts.properties";

    /**
     * Loads hosts from the default configuration file path.
     */
    public static List<Host> loadHosts() {
        return loadHosts(new File(DEFAULT_CONFIG_PATH));
    }

    /**
     * Loads hosts from a specific file.
     */
    public static List<Host> loadHosts(File configFile) {
        List<Host> hostList = new ArrayList<>();
        if (!configFile.exists()) {
            Logger.warn("Config file not found at " + configFile.getAbsolutePath() + ". Loading built-in default hosts.");
            return createDefaultHosts();
        }

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(configFile);
             InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            Logger.error("Failed to read configuration from " + configFile.getAbsolutePath() + ": " + e.getMessage());
            return createDefaultHosts();
        }

        // Identify unique host IDs from properties keys (host.<id>.*)
        TreeSet<String> hostIds = new TreeSet<>((a, b) -> {
            try {
                int ia = Integer.parseInt(a);
                int ib = Integer.parseInt(b);
                return Integer.compare(ia, ib);
            } catch (NumberFormatException e) {
                return a.compareTo(b);
            }
        });

        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("host.")) {
                String[] parts = key.split("\\.");
                if (parts.length >= 3) {
                    hostIds.add(parts[1]);
                }
            }
        }

        for (String id : hostIds) {
            String prefix = "host." + id + ".";
            String name = props.getProperty(prefix + "name", "Host " + id).trim();
            String address = props.getProperty(prefix + "address", "").trim();
            int port = 0;
            try {
                port = Integer.parseInt(props.getProperty(prefix + "port", "0").trim());
            } catch (NumberFormatException ignored) {
            }

            String service = props.getProperty(prefix + "service", "Service").trim();
            int interval = 5;
            try {
                interval = Integer.parseInt(props.getProperty(prefix + "interval", "5").trim());
            } catch (NumberFormatException ignored) {
            }

            int timeout = 2500;
            try {
                timeout = Integer.parseInt(props.getProperty(prefix + "timeout", "2500").trim());
            } catch (NumberFormatException ignored) {
            }

            boolean enabled = Boolean.parseBoolean(props.getProperty(prefix + "enabled", "true").trim());

            if (!address.isEmpty()) {
                Host host = new Host(id, name, address, port, service, interval, timeout, enabled);
                hostList.add(host);
            }
        }

        if (hostList.isEmpty()) {
            Logger.warn("No hosts parsed from configuration; using default hosts.");
            return createDefaultHosts();
        }

        Logger.info("Successfully loaded " + hostList.size() + " host(s) from " + configFile.getName());
        return hostList;
    }

    /**
     * Saves the given list of hosts to the default configuration file.
     */
    public static void saveHosts(List<Host> hosts) throws IOException {
        saveHosts(hosts, new File(DEFAULT_CONFIG_PATH));
    }

    /**
     * Saves the given list of hosts to the specified file.
     */
    public static void saveHosts(List<Host> hosts, File targetFile) throws IOException {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        Properties props = new Properties();
        int index = 1;
        for (Host host : hosts) {
            String prefix = "host." + index + ".";
            props.setProperty(prefix + "name", host.getName());
            props.setProperty(prefix + "address", host.getAddress());
            props.setProperty(prefix + "port", String.valueOf(host.getPort()));
            props.setProperty(prefix + "service", host.getServiceName());
            props.setProperty(prefix + "interval", String.valueOf(host.getIntervalSeconds()));
            props.setProperty(prefix + "timeout", String.valueOf(host.getTimeoutMs()));
            props.setProperty(prefix + "enabled", String.valueOf(host.isEnabled()));
            index++;
        }

        try (FileOutputStream fos = new FileOutputStream(targetFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            props.store(writer, "Network Monitor Configured Hosts - Auto-generated");
        }

        Logger.info("Successfully saved " + hosts.size() + " host(s) to " + targetFile.getAbsolutePath());
    }

    /**
     * Fallback default hosts if configuration file is missing or empty.
     */
    public static List<Host> createDefaultHosts() {
        List<Host> defaults = new ArrayList<>();
        defaults.add(new Host("1", "Google Public DNS", "8.8.8.8", 53, "DNS Service", 5, 2500, true));
        defaults.add(new Host("2", "Cloudflare DNS", "1.1.1.1", 53, "DNS Service", 5, 2500, true));
        defaults.add(new Host("3", "Google Web", "google.com", 443, "HTTPS Web", 5, 3000, true));
        defaults.add(new Host("4", "Localhost Echo", "127.0.0.1", 0, "Local Ping", 5, 1500, true));
        defaults.add(new Host("5", "Local Dev Web Server", "127.0.0.1", 8080, "HTTP Dev Server", 5, 1500, true));
        return defaults;
    }
}
