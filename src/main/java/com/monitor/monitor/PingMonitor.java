package com.monitor.monitor;

import com.monitor.model.Host;
import com.monitor.model.MonitorResult;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Checks host reachability via ICMP / Echo reachability using java.net.InetAddress.
 */
public class PingMonitor {

    /**
     * Checks if the target host address responds to reachability probes.
     *
     * @param host Target host configuration
     * @return MonitorResult with UP/DOWN status, response time in ms, and diagnostics
     */
    public MonitorResult checkPing(Host host) {
        String address = host.getAddress();
        int timeoutMs = host.getTimeoutMs();

        long startTime = System.nanoTime();
        try {
            InetAddress inet = InetAddress.getByName(address);

            // Measure reachability duration
            boolean reachable = inet.isReachable(timeoutMs);
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            if (reachable) {
                return MonitorResult.success(
                        host.getId(),
                        MonitorResult.PortStatus.NOT_APPLICABLE,
                        Math.max(1, elapsedMs),
                        String.format("Host %s (%s) is reachable via ICMP/Echo (%d ms)",
                                host.getName(), inet.getHostAddress(), Math.max(1, elapsedMs))
                );
            } else {
                return MonitorResult.failure(
                        host.getId(),
                        MonitorResult.PortStatus.NOT_APPLICABLE,
                        elapsedMs,
                        String.format("Host %s (%s) unreachable within %d ms (ICMP/Port 7 echo timeout)",
                                host.getName(), inet.getHostAddress(), timeoutMs),
                        "Host unreachable / Echo probe timed out"
                );
            }
        } catch (UnknownHostException e) {
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
            return MonitorResult.failure(
                    host.getId(),
                    MonitorResult.PortStatus.NOT_APPLICABLE,
                    elapsedMs,
                    "DNS Resolution failed for host: " + address,
                    "Unknown Host: " + e.getMessage()
            );
        } catch (IOException e) {
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
            return MonitorResult.failure(
                    host.getId(),
                    MonitorResult.PortStatus.NOT_APPLICABLE,
                    elapsedMs,
                    "Network I/O error while probing host: " + e.getMessage(),
                    e.getMessage()
            );
        }
    }
}
