package com.monitor.monitor;

import com.monitor.model.Host;
import com.monitor.model.MonitorResult;
import com.monitor.util.Logger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Executes checks on a Host using PingMonitor or PortMonitor, updates host statistics,
 * and triggers alerts on status transitions (DOWN or RECOVERY).
 */
public class HostMonitor {

    public enum AlertType {
        DOWN,
        RECOVERED
    }

    public interface AlertListener {
        void onAlert(Host host, AlertType type, String message);
    }

    private final PingMonitor pingMonitor;
    private final PortMonitor portMonitor;
    private final List<AlertListener> alertListeners = new CopyOnWriteArrayList<>();

    public HostMonitor() {
        this.pingMonitor = new PingMonitor();
        this.portMonitor = new PortMonitor();
    }

    public HostMonitor(PingMonitor pingMonitor, PortMonitor portMonitor) {
        this.pingMonitor = pingMonitor;
        this.portMonitor = portMonitor;
    }

    public void addAlertListener(AlertListener listener) {
        if (listener != null && !alertListeners.contains(listener)) {
            alertListeners.add(listener);
        }
    }

    public void removeAlertListener(AlertListener listener) {
        alertListeners.remove(listener);
    }

    /**
     * Executes a check against the host, records the result, and fires alerts on state changes.
     *
     * @param host The host to check
     * @return The resulting MonitorResult
     */
    public MonitorResult checkHost(Host host) {
        if (!host.isEnabled()) {
            return new MonitorResult(
                    host.getId(),
                    MonitorResult.Status.UNKNOWN,
                    MonitorResult.PortStatus.NOT_APPLICABLE,
                    0,
                    false,
                    "Monitoring disabled for this host",
                    null
            );
        }

        MonitorResult.Status previousStatus = host.getCurrentStatus();

        MonitorResult result;
        if (host.hasPort()) {
            result = portMonitor.checkPort(host);
        } else {
            result = pingMonitor.checkPing(host);
        }

        // Update host statistics
        host.recordResult(result);

        // Check for state transitions and fire alerts
        if (previousStatus == MonitorResult.Status.UP && result.getStatus() == MonitorResult.Status.DOWN) {
            String alertMsg = String.format("ALERT: Host '%s' (%s:%d) is DOWN! Error: %s",
                    host.getName(), host.getAddress(), host.getPort(), result.getMessage());
            Logger.alert(alertMsg);
            fireAlert(host, AlertType.DOWN, alertMsg);
        } else if (previousStatus == MonitorResult.Status.DOWN && result.getStatus() == MonitorResult.Status.UP) {
            String recoveryMsg = String.format("RECOVERY: Host '%s' (%s:%d) has RECOVERED! Latency: %d ms",
                    host.getName(), host.getAddress(), host.getPort(), result.getLatencyMs());
            Logger.info(recoveryMsg);
            fireAlert(host, AlertType.RECOVERED, recoveryMsg);
        } else if (previousStatus == MonitorResult.Status.UNKNOWN && result.getStatus() == MonitorResult.Status.DOWN) {
            // Initial failure on first check
            String initialFailMsg = String.format("WARNING: Host '%s' (%s:%d) is DOWN on initial check. Error: %s",
                    host.getName(), host.getAddress(), host.getPort(), result.getMessage());
            Logger.warn(initialFailMsg);
            fireAlert(host, AlertType.DOWN, initialFailMsg);
        } else {
            // Normal continuous status log
            if (result.isUp()) {
                Logger.info(String.format("Checked [%s - %s]: UP (%d ms)",
                        host.getName(), host.getServiceName(), result.getLatencyMs()));
            } else {
                Logger.warn(String.format("Checked [%s - %s]: DOWN (%s)",
                        host.getName(), host.getServiceName(), result.getMessage()));
            }
        }

        return result;
    }

    private void fireAlert(Host host, AlertType type, String message) {
        for (AlertListener listener : alertListeners) {
            try {
                listener.onAlert(host, type, message);
            } catch (Exception e) {
                Logger.error("Error in alert listener: " + e.getMessage());
            }
        }
    }

    public PingMonitor getPingMonitor() {
        return pingMonitor;
    }

    public PortMonitor getPortMonitor() {
        return portMonitor;
    }
}
