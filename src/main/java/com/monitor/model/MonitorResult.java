package com.monitor.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Encapsulates the outcome of a single network check against a host and port.
 */
public class MonitorResult {

    public enum Status {
        UP,
        DOWN,
        UNKNOWN
    }

    public enum PortStatus {
        OPEN,
        CLOSED,
        TIMEOUT,
        NOT_APPLICABLE
    }

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final String hostId;
    private final Instant timestamp;
    private final Status status;
    private final PortStatus portStatus;
    private final long latencyMs;
    private final boolean reachable;
    private final String message;
    private final String errorMessage;

    public MonitorResult(String hostId, Status status, PortStatus portStatus,
                         long latencyMs, boolean reachable, String message, String errorMessage) {
        this.hostId = hostId;
        this.timestamp = Instant.now();
        this.status = status;
        this.portStatus = portStatus;
        this.latencyMs = latencyMs;
        this.reachable = reachable;
        this.message = message != null ? message : "";
        this.errorMessage = errorMessage != null ? errorMessage : "";
    }

    public static MonitorResult success(String hostId, PortStatus portStatus, long latencyMs, String message) {
        return new MonitorResult(hostId, Status.UP, portStatus, latencyMs, true, message, null);
    }

    public static MonitorResult failure(String hostId, PortStatus portStatus, long latencyMs, String message, String error) {
        return new MonitorResult(hostId, Status.DOWN, portStatus, latencyMs, false, message, error);
    }

    public String getHostId() {
        return hostId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getFormattedTime() {
        return TIME_FORMATTER.format(timestamp);
    }

    public Status getStatus() {
        return status;
    }

    public boolean isUp() {
        return status == Status.UP;
    }

    public PortStatus getPortStatus() {
        return portStatus;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public boolean isReachable() {
        return reachable;
    }

    public String getMessage() {
        return message;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return String.format("[%s] Status: %s | Port: %s | Latency: %d ms | Info: %s",
                getFormattedTime(), status, portStatus, latencyMs, message);
    }
}
