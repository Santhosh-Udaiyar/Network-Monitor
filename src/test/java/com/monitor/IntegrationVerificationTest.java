package com.monitor;

import com.monitor.model.Host;
import com.monitor.model.MonitorResult;
import com.monitor.monitor.HostMonitor;
import com.monitor.report.ReportExporter;
import com.monitor.scheduler.MonitorScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class IntegrationVerificationTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("End-to-End verification: Multiple hosts, simulated failure, DOWN alert, recovery, report export")
    void testEndToEndLifecycle() throws Exception {
        // Step 1: Start dynamic local mock service on port P
        int servicePort;
        try (ServerSocket probe = new ServerSocket(0)) {
            servicePort = probe.getLocalPort();
        }

        AtomicBoolean serverRunning = new AtomicBoolean(true);
        ServerSocket mockServer = new ServerSocket(servicePort);

        Thread serverThread = new Thread(() -> {
            while (serverRunning.get()) {
                try {
                    Socket client = mockServer.accept();
                    client.close();
                } catch (IOException e) {
                    break;
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        // Step 2: Configure multiple hosts
        Host localMockHost = new Host("1", "Mock App Service", "127.0.0.1", servicePort, "App API", 1, 1500, true);
        Host dnsHost = new Host("2", "Google DNS", "8.8.8.8", 53, "DNS", 1, 2000, true);
        Host localPing = new Host("3", "Local Loopback", "127.0.0.1", 0, "Loopback ICMP", 1, 1500, true);

        List<Host> hostList = new ArrayList<>(List.of(localMockHost, dnsHost, localPing));
        MonitorScheduler scheduler = new MonitorScheduler(hostList, 4);

        List<String> alerts = new CopyOnWriteArrayList<>();
        scheduler.addAlertListener((host, type, message) -> alerts.add(type.name() + ": " + host.getName()));

        HostMonitor hostMonitor = scheduler.getHostMonitor();

        // Step 3: Check while mock server is running -> UP
        MonitorResult res1 = hostMonitor.checkHost(localMockHost);
        assertEquals(MonitorResult.Status.UP, res1.getStatus());
        assertEquals(MonitorResult.PortStatus.OPEN, res1.getPortStatus());
        assertTrue(localMockHost.isUp());
        assertEquals(100.0, localMockHost.getUptimePercentage(), 0.001);

        // Step 4: Simulate service failure (kill mock server)
        serverRunning.set(false);
        mockServer.close();
        Thread.sleep(100);

        // Step 5: Check after failure -> DOWN and DOWN alert triggered
        MonitorResult res2 = hostMonitor.checkHost(localMockHost);
        assertEquals(MonitorResult.Status.DOWN, res2.getStatus());
        assertEquals(MonitorResult.PortStatus.CLOSED, res2.getPortStatus());
        assertFalse(localMockHost.isUp());
        assertEquals(1, localMockHost.getFailureCount());
        assertTrue(localMockHost.getUptimePercentage() < 100.0);
        assertTrue(alerts.stream().anyMatch(a -> a.startsWith("DOWN: Mock App Service")));

        // Step 6: Restore service (rebind ServerSocket on same port)
        serverRunning.set(true);
        ServerSocket restoredServer = new ServerSocket(servicePort);
        Thread restoredThread = new Thread(() -> {
            while (serverRunning.get()) {
                try {
                    Socket client = restoredServer.accept();
                    client.close();
                } catch (IOException e) {
                    break;
                }
            }
        });
        restoredThread.setDaemon(true);
        restoredThread.start();
        Thread.sleep(100);

        // Step 7: Check after restoration -> RECOVERED and RECOVERY alert triggered
        MonitorResult res3 = hostMonitor.checkHost(localMockHost);
        assertEquals(MonitorResult.Status.UP, res3.getStatus());
        assertEquals(MonitorResult.PortStatus.OPEN, res3.getPortStatus());
        assertTrue(localMockHost.isUp());
        assertTrue(alerts.stream().anyMatch(a -> a.startsWith("RECOVERED: Mock App Service")));

        // Step 8: Export reports
        File csvReport = tempDir.resolve("verification-report.csv").toFile();
        File txtReport = tempDir.resolve("verification-report.txt").toFile();

        ReportExporter.exportToCSV(csvReport, hostList);
        ReportExporter.exportToTXT(txtReport, hostList);

        assertTrue(csvReport.exists() && csvReport.length() > 0);
        assertTrue(txtReport.exists() && txtReport.length() > 0);

        String csvContent = Files.readString(csvReport.toPath());
        assertTrue(csvContent.contains("Mock App Service"));
        assertTrue(csvContent.contains("Google DNS"));

        String txtContent = Files.readString(txtReport.toPath());
        assertTrue(txtContent.contains("NETWORK MONITOR - SYSTEM AUDIT REPORT"));
        assertTrue(txtContent.contains("Mock App Service"));

        // Cleanup
        serverRunning.set(false);
        restoredServer.close();
        scheduler.shutdown();
    }
}
