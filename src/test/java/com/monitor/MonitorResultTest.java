package com.monitor;

import com.monitor.model.MonitorResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MonitorResultTest {

    @Test
    @DisplayName("MonitorResult.success should populate UP status and correct fields")
    void testSuccessResult() {
        MonitorResult res = MonitorResult.success("host-1", MonitorResult.PortStatus.OPEN, 35, "Port open");

        assertEquals("host-1", res.getHostId());
        assertEquals(MonitorResult.Status.UP, res.getStatus());
        assertTrue(res.isUp());
        assertTrue(res.isReachable());
        assertEquals(MonitorResult.PortStatus.OPEN, res.getPortStatus());
        assertEquals(35, res.getLatencyMs());
        assertEquals("Port open", res.getMessage());
        assertEquals("", res.getErrorMessage());
        assertNotNull(res.getTimestamp());
        assertNotNull(res.getFormattedTime());
        assertTrue(res.toString().contains("Status: UP"));
    }

    @Test
    @DisplayName("MonitorResult.failure should populate DOWN status and error details")
    void testFailureResult() {
        MonitorResult res = MonitorResult.failure("host-2", MonitorResult.PortStatus.TIMEOUT, 2500, "Timed out", "Socket timeout");

        assertEquals("host-2", res.getHostId());
        assertEquals(MonitorResult.Status.DOWN, res.getStatus());
        assertFalse(res.isUp());
        assertFalse(res.isReachable());
        assertEquals(MonitorResult.PortStatus.TIMEOUT, res.getPortStatus());
        assertEquals(2500, res.getLatencyMs());
        assertEquals("Timed out", res.getMessage());
        assertEquals("Socket timeout", res.getErrorMessage());
        assertTrue(res.toString().contains("Status: DOWN"));
    }
}
