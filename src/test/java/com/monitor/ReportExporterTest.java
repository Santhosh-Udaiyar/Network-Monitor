package com.monitor;

import com.monitor.model.Host;
import com.monitor.model.MonitorResult;
import com.monitor.report.ReportExporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ReportExporterTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("ReportExporter should produce well-formed CSV report file")
    void testExportToCSV() throws IOException {
        File csvFile = tempDir.resolve("report.csv").toFile();

        List<Host> hosts = new ArrayList<>();
        Host h1 = new Host("1", "Web Server", "192.168.1.10", 80, "HTTP", 5, 2000, true);
        h1.recordResult(MonitorResult.success("1", MonitorResult.PortStatus.OPEN, 24, "Connected"));
        hosts.add(h1);

        ReportExporter.exportToCSV(csvFile, hosts);

        assertTrue(csvFile.exists());
        assertTrue(csvFile.length() > 0);

        List<String> lines = Files.readAllLines(csvFile.toPath());
        assertTrue(lines.stream().anyMatch(l -> l.contains("NETWORK MONITOR SUMMARY REPORT")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("Host Name,Address,Port")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("Web Server") && l.contains("192.168.1.10")));
    }

    @Test
    @DisplayName("ReportExporter should produce readable TXT audit report file")
    void testExportToTXT() throws IOException {
        File txtFile = tempDir.resolve("report.txt").toFile();

        List<Host> hosts = new ArrayList<>();
        Host h1 = new Host("1", "Database Server", "192.168.1.20", 5432, "PostgreSQL", 5, 2000, true);
        h1.recordResult(MonitorResult.success("1", MonitorResult.PortStatus.OPEN, 12, "Connected"));
        h1.recordResult(MonitorResult.failure("1", MonitorResult.PortStatus.CLOSED, 5, "Connection refused", "Refused"));
        hosts.add(h1);

        ReportExporter.exportToTXT(txtFile, hosts);

        assertTrue(txtFile.exists());
        assertTrue(txtFile.length() > 0);

        String content = Files.readString(txtFile.toPath());
        assertTrue(content.contains("NETWORK MONITOR - SYSTEM AUDIT REPORT"));
        assertTrue(content.contains("Total Hosts Monitored: 1"));
        assertTrue(content.contains("Database Server"));
        assertTrue(content.contains("PostgreSQL"));
        assertTrue(content.contains("PER-HOST DETAILED LATENCY METRICS"));
    }
}
