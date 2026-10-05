package com.monitor;

import com.monitor.model.Host;
import com.monitor.model.MonitorResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LatencyStatsTest {

    @Test
    @DisplayName("Latency statistics should properly track min, max, avg, and latest latency")
    void testLatencyCalculations() {
        Host host = new Host("Database", "192.168.1.50", 5432, "PostgreSQL");

        assertEquals(0, host.getMinLatencyMs());
        assertEquals(0, host.getMaxLatencyMs());
        assertEquals(0.0, host.getAverageLatencyMs(), 0.001);
        assertEquals(0, host.getLatestLatencyMs());

        // Check 1: 50 ms
        host.recordResult(MonitorResult.success(host.getId(), MonitorResult.PortStatus.OPEN, 50, "OK"));
        assertEquals(50, host.getMinLatencyMs());
        assertEquals(50, host.getMaxLatencyMs());
        assertEquals(50.0, host.getAverageLatencyMs(), 0.001);
        assertEquals(50, host.getLatestLatencyMs());

        // Check 2: 20 ms
        host.recordResult(MonitorResult.success(host.getId(), MonitorResult.PortStatus.OPEN, 20, "OK"));
        assertEquals(20, host.getMinLatencyMs());
        assertEquals(50, host.getMaxLatencyMs());
        assertEquals(35.0, host.getAverageLatencyMs(), 0.001);
        assertEquals(20, host.getLatestLatencyMs());

        // Check 3: 80 ms
        host.recordResult(MonitorResult.success(host.getId(), MonitorResult.PortStatus.OPEN, 80, "OK"));
        assertEquals(20, host.getMinLatencyMs());
        assertEquals(80, host.getMaxLatencyMs());
        assertEquals(50.0, host.getAverageLatencyMs(), 0.001); // (50 + 20 + 80) / 3 = 50.0
        assertEquals(80, host.getLatestLatencyMs());

        // Check 4: Failed check should not distort successful check latency averages
        host.recordResult(MonitorResult.failure(host.getId(), MonitorResult.PortStatus.CLOSED, 1500, "Refused", "Err"));
        assertEquals(20, host.getMinLatencyMs());
        assertEquals(80, host.getMaxLatencyMs());
        assertEquals(50.0, host.getAverageLatencyMs(), 0.001);
        assertEquals(1500, host.getLatestLatencyMs());
        assertEquals(1, host.getFailureCount());
        assertEquals(3, host.getSuccessfulChecks());
        assertEquals(4, host.getTotalChecks());
    }

    @Test
    @DisplayName("Host history snapshot should enforce maximum size bound")
    void testHistoryBoundedCapacity() {
        Host host = new Host("Cache", "127.0.0.1", 6379, "Redis");

        for (int i = 1; i <= 150; i++) {
            host.recordResult(MonitorResult.success(host.getId(), MonitorResult.PortStatus.OPEN, i, "OK " + i));
        }

        assertEquals(150, host.getTotalChecks());
        // Max history is capped at 100 entries
        assertEquals(100, host.getHistorySnapshot().size());
        // Oldest preserved element should have latency 51
        assertEquals(51, host.getHistorySnapshot().get(0).getLatencyMs());
        // Newest should be 150
        assertEquals(150, host.getHistorySnapshot().get(99).getLatencyMs());
    }
}
