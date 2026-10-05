package com.monitor;

import com.monitor.model.Host;
import com.monitor.model.MonitorResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class HostTest {

    private Host host;

    @BeforeEach
    void setUp() {
        host = new Host("1", "Test Web", "127.0.0.1", 8080, "HTTP", 5, 2000, true);
    }

    @Test
    @DisplayName("Host validation should succeed for valid configuration")
    void testValidHost() {
        assertDoesNotThrow(() -> host.validate());
        assertEquals("Test Web", host.getName());
        assertEquals("127.0.0.1", host.getAddress());
        assertEquals(8080, host.getPort());
        assertEquals("HTTP", host.getServiceName());
        assertTrue(host.hasPort());
    }

    @Test
    @DisplayName("Host validation should fail on blank name")
    void testBlankNameValidation() {
        host.setName("");
        assertThrows(IllegalArgumentException.class, () -> host.validate());
    }

    @Test
    @DisplayName("Host validation should fail on blank address")
    void testBlankAddressValidation() {
        host.setAddress("   ");
        assertThrows(IllegalArgumentException.class, () -> host.validate());
    }

    @Test
    @DisplayName("Host validation should fail on invalid port number")
    void testInvalidPortValidation() {
        host.setPort(-1);
        assertThrows(IllegalArgumentException.class, () -> host.validate());

        host.setPort(65536);
        assertThrows(IllegalArgumentException.class, () -> host.validate());

        // Port 0 is valid for ping only
        host.setPort(0);
        assertDoesNotThrow(() -> host.validate());
        assertFalse(host.hasPort());
    }

    @Test
    @DisplayName("Host validation should fail on invalid interval or timeout")
    void testIntervalAndTimeoutValidation() {
        host.setIntervalSeconds(0);
        // Setter enforces min 1, but test direct edge
        assertEquals(1, host.getIntervalSeconds());

        host.setTimeoutMs(50);
        // Setter enforces min 100
        assertEquals(100, host.getTimeoutMs());
    }

    @Test
    @DisplayName("Uptime calculation should compute accurate percentage")
    void testUptimeCalculation() {
        // Initial state
        assertEquals(100.0, host.getUptimePercentage(), 0.001);

        // 3 successful checks
        host.recordResult(MonitorResult.success("1", MonitorResult.PortStatus.OPEN, 15, "OK"));
        host.recordResult(MonitorResult.success("1", MonitorResult.PortStatus.OPEN, 25, "OK"));
        host.recordResult(MonitorResult.success("1", MonitorResult.PortStatus.OPEN, 20, "OK"));

        assertEquals(3, host.getTotalChecks());
        assertEquals(3, host.getSuccessfulChecks());
        assertEquals(0, host.getFailureCount());
        assertEquals(100.0, host.getUptimePercentage(), 0.001);

        // 1 failure
        host.recordResult(MonitorResult.failure("1", MonitorResult.PortStatus.CLOSED, 10, "Failed", "Refused"));

        assertEquals(4, host.getTotalChecks());
        assertEquals(3, host.getSuccessfulChecks());
        assertEquals(1, host.getFailureCount());
        assertEquals(75.0, host.getUptimePercentage(), 0.001);

        // Reset stats
        host.resetStats();
        assertEquals(0, host.getTotalChecks());
        assertEquals(0, host.getSuccessfulChecks());
        assertEquals(0, host.getFailureCount());
        assertEquals(100.0, host.getUptimePercentage(), 0.001);
    }
}
