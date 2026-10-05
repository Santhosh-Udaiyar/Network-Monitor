package com.monitor;

import com.monitor.model.Host;
import com.monitor.model.MonitorResult;
import com.monitor.monitor.PortMonitor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

public class PortMonitorTest {

    @Test
    @DisplayName("PortMonitor should detect OPEN port when TCP server is listening")
    void testOpenPortDetection() throws IOException {
        PortMonitor monitor = new PortMonitor();

        // Start a temporary local ServerSocket on an available port
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();

            // Background acceptor thread to allow connection handshake to complete
            Thread acceptThread = new Thread(() -> {
                try {
                    Socket accepted = serverSocket.accept();
                    accepted.close();
                } catch (IOException ignored) {
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();

            Host host = new Host("Local Test", "127.0.0.1", port, "Test Service");
            host.setTimeoutMs(2000);

            MonitorResult result = monitor.checkPort(host);

            assertEquals(MonitorResult.Status.UP, result.getStatus());
            assertEquals(MonitorResult.PortStatus.OPEN, result.getPortStatus());
            assertTrue(result.isUp());
            assertTrue(result.getLatencyMs() >= 0);
            assertTrue(result.getMessage().contains("OPEN"));
        }
    }

    @Test
    @DisplayName("PortMonitor should detect CLOSED port when no process is listening")
    void testClosedPortDetection() throws IOException {
        PortMonitor monitor = new PortMonitor();

        // Find an unused local port by opening and immediately closing ServerSocket
        int closedPort;
        try (ServerSocket ss = new ServerSocket(0)) {
            closedPort = ss.getLocalPort();
        }

        Host host = new Host("Closed Local Test", "127.0.0.1", closedPort, "Unused Port");
        host.setTimeoutMs(1000);

        MonitorResult result = monitor.checkPort(host);

        assertEquals(MonitorResult.Status.DOWN, result.getStatus());
        assertEquals(MonitorResult.PortStatus.CLOSED, result.getPortStatus());
        assertFalse(result.isUp());
        assertTrue(result.getMessage().contains("CLOSED") || result.getMessage().contains("refused"));
    }

    @Test
    @DisplayName("PortMonitor should handle unknown hostname gracefully")
    void testUnknownHostDetection() {
        PortMonitor monitor = new PortMonitor();
        Host host = new Host("Invalid Host", "nonexistent-domain-xyz-123456789.invalid", 80, "Dummy");
        host.setTimeoutMs(1000);

        MonitorResult result = monitor.checkPort(host);
        assertEquals(MonitorResult.Status.DOWN, result.getStatus());
        assertFalse(result.isUp());
        assertTrue(result.getMessage().contains("DNS") || result.getMessage().contains("Unknown"));
    }
}
