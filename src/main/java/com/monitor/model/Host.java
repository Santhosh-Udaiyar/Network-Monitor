package com.monitor.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Represents a monitored target host/service and maintains real-time statistics
 * including uptime percentage, latency stats, and check history.
 */
public class Host {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final int MAX_HISTORY_SIZE = 100;

    private final String id;
    private String name;
    private String address;
    private int port;
    private String serviceName;
    private int intervalSeconds;
    private int timeoutMs;
    private boolean enabled;

    // Statistics (thread-safe synchronization)
    private final Object statsLock = new Object();
    private long totalChecks = 0;
    private long successfulChecks = 0;
    private long failureCount = 0;
    private long consecutiveFailures = 0;
    private long minLatencyMs = -1;
    private long maxLatencyMs = 0;
    private long totalLatencyMs = 0;
    private long latestLatencyMs = 0;

    private MonitorResult.Status currentStatus = MonitorResult.Status.UNKNOWN;
    private MonitorResult.PortStatus currentPortStatus = MonitorResult.PortStatus.NOT_APPLICABLE;
    private Instant lastCheckTime = null;
    private String lastErrorMessage = "";

    // Circular/bounded history for real-time charting
    private final ConcurrentLinkedDeque<MonitorResult> history = new ConcurrentLinkedDeque<>();

    public Host(String id, String name, String address, int port, String serviceName,
                int intervalSeconds, int timeoutMs, boolean enabled) {
        this.id = (id == null || id.trim().isEmpty()) ? UUID.randomUUID().toString() : id;
        this.name = name;
        this.address = address;
        this.port = port;
        this.serviceName = serviceName;
        this.intervalSeconds = Math.max(1, intervalSeconds);
        this.timeoutMs = Math.max(200, timeoutMs);
        this.enabled = enabled;
    }

    public Host(String name, String address, int port, String serviceName) {
        this(UUID.randomUUID().toString(), name, address, port, serviceName, 5, 2500, true);
    }

    /**
     * Validates configuration parameters of this host.
     * @throws IllegalArgumentException if fields are invalid.
     */
    public void validate() {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Host name cannot be empty");
        }
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("Host address/IP cannot be empty");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535 (0 for ICMP Ping only)");
        }
        if (serviceName == null || serviceName.trim().isEmpty()) {
            throw new IllegalArgumentException("Service name cannot be empty");
        }
        if (intervalSeconds < 1) {
            throw new IllegalArgumentException("Check interval must be at least 1 second");
        }
        if (timeoutMs < 100) {
            throw new IllegalArgumentException("Timeout must be at least 100 ms");
        }
    }

    /**
     * Records a new monitor result and updates statistical metrics atomically.
     */
    public void recordResult(MonitorResult result) {
        synchronized (statsLock) {
            totalChecks++;
            lastCheckTime = result.getTimestamp();
            currentStatus = result.getStatus();
            currentPortStatus = result.getPortStatus();
            latestLatencyMs = result.getLatencyMs();

            if (result.isUp()) {
                successfulChecks++;
                consecutiveFailures = 0;
                lastErrorMessage = "";

                if (minLatencyMs < 0 || latestLatencyMs < minLatencyMs) {
                    minLatencyMs = latestLatencyMs;
                }
                if (latestLatencyMs > maxLatencyMs) {
                    maxLatencyMs = latestLatencyMs;
                }
                totalLatencyMs += latestLatencyMs;
            } else {
                failureCount++;
                consecutiveFailures++;
                lastErrorMessage = result.getErrorMessage();
            }

            // Append to history and prune if necessary
            history.addLast(result);
            while (history.size() > MAX_HISTORY_SIZE) {
                history.pollFirst();
            }
        }
    }

    /**
     * Calculates current uptime percentage (0.0% to 100.0%).
     */
    public double getUptimePercentage() {
        synchronized (statsLock) {
            if (totalChecks == 0) {
                return 100.0;
            }
            return (successfulChecks / (double) totalChecks) * 100.0;
        }
    }

    /**
     * Calculates average latency of successful checks.
     */
    public double getAverageLatencyMs() {
        synchronized (statsLock) {
            if (successfulChecks == 0) {
                return 0.0;
            }
            return totalLatencyMs / (double) successfulChecks;
        }
    }

    /**
     * Resets accumulated statistics.
     */
    public void resetStats() {
        synchronized (statsLock) {
            totalChecks = 0;
            successfulChecks = 0;
            failureCount = 0;
            consecutiveFailures = 0;
            minLatencyMs = -1;
            maxLatencyMs = 0;
            totalLatencyMs = 0;
            latestLatencyMs = 0;
            currentStatus = MonitorResult.Status.UNKNOWN;
            currentPortStatus = MonitorResult.PortStatus.NOT_APPLICABLE;
            lastCheckTime = null;
            lastErrorMessage = "";
            history.clear();
        }
    }

    public List<MonitorResult> getHistorySnapshot() {
        return new ArrayList<>(history);
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = Math.max(1, intervalSeconds);
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = Math.max(100, timeoutMs);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getTotalChecks() {
        synchronized (statsLock) {
            return totalChecks;
        }
    }

    public long getSuccessfulChecks() {
        synchronized (statsLock) {
            return successfulChecks;
        }
    }

    public long getFailureCount() {
        synchronized (statsLock) {
            return failureCount;
        }
    }

    public long getConsecutiveFailures() {
        synchronized (statsLock) {
            return consecutiveFailures;
        }
    }

    public long getMinLatencyMs() {
        synchronized (statsLock) {
            return minLatencyMs < 0 ? 0 : minLatencyMs;
        }
    }

    public long getMaxLatencyMs() {
        synchronized (statsLock) {
            return maxLatencyMs;
        }
    }

    public long getLatestLatencyMs() {
        synchronized (statsLock) {
            return latestLatencyMs;
        }
    }

    public MonitorResult.Status getCurrentStatus() {
        synchronized (statsLock) {
            return currentStatus;
        }
    }

    public boolean isUp() {
        synchronized (statsLock) {
            return currentStatus == MonitorResult.Status.UP;
        }
    }

    public MonitorResult.PortStatus getCurrentPortStatus() {
        synchronized (statsLock) {
            return currentPortStatus;
        }
    }

    public Instant getLastCheckTime() {
        synchronized (statsLock) {
            return lastCheckTime;
        }
    }

    public String getFormattedLastCheck() {
        synchronized (statsLock) {
            return lastCheckTime != null ? FORMATTER.format(lastCheckTime) : "Never";
        }
    }

    public String getLastErrorMessage() {
        synchronized (statsLock) {
            return lastErrorMessage;
        }
    }

    public boolean hasPort() {
        return port > 0 && port <= 65535;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Host host = (Host) o;
        return Objects.equals(id, host.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("%s (%s:%d - %s)", name, address, port, serviceName);
    }
}
