package com.monitor;

import com.monitor.model.Host;
import com.monitor.util.ConfigManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigManagerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("ConfigManager should correctly parse hosts from properties file")
    void testLoadHostsFromFile() throws IOException {
        File configFile = tempDir.resolve("test-hosts.properties").toFile();
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("host.1.name=Custom DNS\n");
            writer.write("host.1.address=1.1.1.1\n");
            writer.write("host.1.port=53\n");
            writer.write("host.1.service=DNS\n");
            writer.write("host.1.interval=10\n");
            writer.write("host.1.timeout=3000\n");
            writer.write("host.1.enabled=true\n");

            writer.write("host.2.name=Custom Web\n");
            writer.write("host.2.address=127.0.0.1\n");
            writer.write("host.2.port=9090\n");
            writer.write("host.2.service=HTTP\n");
            writer.write("host.2.interval=3\n");
            writer.write("host.2.timeout=1000\n");
            writer.write("host.2.enabled=false\n");
        }

        List<Host> loaded = ConfigManager.loadHosts(configFile);
        assertEquals(2, loaded.size());

        Host h1 = loaded.get(0);
        assertEquals("Custom DNS", h1.getName());
        assertEquals("1.1.1.1", h1.getAddress());
        assertEquals(53, h1.getPort());
        assertEquals("DNS", h1.getServiceName());
        assertEquals(10, h1.getIntervalSeconds());
        assertEquals(3000, h1.getTimeoutMs());
        assertTrue(h1.isEnabled());

        Host h2 = loaded.get(1);
        assertEquals("Custom Web", h2.getName());
        assertEquals("127.0.0.1", h2.getAddress());
        assertEquals(9090, h2.getPort());
        assertEquals("HTTP", h2.getServiceName());
        assertEquals(3, h2.getIntervalSeconds());
        assertEquals(1000, h2.getTimeoutMs());
        assertFalse(h2.isEnabled());
    }

    @Test
    @DisplayName("ConfigManager should round-trip save and load hosts without loss")
    void testSaveAndReloadHosts() throws IOException {
        File targetFile = tempDir.resolve("saved-hosts.properties").toFile();

        List<Host> originalList = new ArrayList<>();
        originalList.add(new Host("1", "App Gateway", "10.0.0.1", 443, "HTTPS Gateway", 15, 4000, true));
        originalList.add(new Host("2", "MySQL DB", "10.0.0.2", 3306, "Database", 20, 5000, true));

        ConfigManager.saveHosts(originalList, targetFile);
        assertTrue(targetFile.exists());

        List<Host> reloaded = ConfigManager.loadHosts(targetFile);
        assertEquals(2, reloaded.size());

        assertEquals("App Gateway", reloaded.get(0).getName());
        assertEquals("10.0.0.1", reloaded.get(0).getAddress());
        assertEquals(443, reloaded.get(0).getPort());
        assertEquals("HTTPS Gateway", reloaded.get(0).getServiceName());

        assertEquals("MySQL DB", reloaded.get(1).getName());
        assertEquals("10.0.0.2", reloaded.get(1).getAddress());
        assertEquals(3306, reloaded.get(1).getPort());
        assertEquals("Database", reloaded.get(1).getServiceName());
    }

    @Test
    @DisplayName("ConfigManager should return default hosts when file does not exist")
    void testLoadNonExistentFile() {
        File nonExistent = tempDir.resolve("missing.properties").toFile();
        List<Host> defaults = ConfigManager.loadHosts(nonExistent);
        assertNotNull(defaults);
        assertFalse(defaults.isEmpty());
        assertTrue(defaults.stream().anyMatch(h -> h.getAddress().equals("8.8.8.8")));
    }
}
