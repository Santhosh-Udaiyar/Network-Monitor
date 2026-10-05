package com.monitor.monitor;

import com.monitor.model.Host;
import com.monitor.model.MonitorResult;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Probes TCP port availability and connection latency using standard Java Sockets.
 * Distinguishes between OPEN, CLOSED (refused), and TIMEOUT states.
 */
public class PortMonitor {

    /**
     * Attempts a TCP connection to the configured host and port.
     *
     * @param host Target host configuration
     * @return MonitorResult indicating OPEN, CLOSED, or TIMEOUT
     */
    public MonitorResult checkPort(Host host) {
        String address = host.getAddress();
        int port = host.getPort();
        int timeoutMs = host.getTimeoutMs();

        long startTime = System.nanoTime();
        try (Socket socket = new Socket()) {
            // Configure socket options for clean probing
            socket.setTcpNoDelay(true);
            socket.setSoLinger(false, 0);

            // Attempt TCP 3-way handshake with timeout
            socket.connect(new InetSocketAddress(address, port), timeoutMs);

            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
            return MonitorResult.success(
                    host.getId(),
                    MonitorResult.PortStatus.OPEN,
                    Math.max(1, elapsedMs),
                    String.format("Port %d is OPEN on %s (latency: %d ms)", port, address, Math.max(1, elapsedMs))
            );
        } catch (SocketTimeoutException e) {
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
            return MonitorResult.failure(
                    host.getId(),
                    MonitorResult.PortStatus.TIMEOUT,
                    elapsedMs,
                    String.format("Connection to %s:%d TIMED OUT after %d ms", address, port, timeoutMs),
                    "Connection timed out"
            );
        } catch (ConnectException e) {
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
            return MonitorResult.failure(
                    host.getId(),
                    MonitorResult.PortStatus.CLOSED,
                    elapsedMs,
                    String.format("Port %d on %s is CLOSED (Connection refused)", port, address),
                    "Connection refused (port closed)"
            );
        } catch (UnknownHostException e) {
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
            return MonitorResult.failure(
                    host.getId(),
                    MonitorResult.PortStatus.CLOSED,
                    elapsedMs,
                    "DNS resolution failed for " + address,
                    "Unknown Host: " + e.getMessage()
            );
        } catch (NoRouteToHostException e) {
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
            return MonitorResult.failure(
                    host.getId(),
                    MonitorResult.PortStatus.CLOSED,
                    elapsedMs,
                    "No route to host: " + address,
                    "No route to host"
            );
        } catch (IOException e) {
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
            return MonitorResult.failure(
                    host.getId(),
                    MonitorResult.PortStatus.CLOSED,
                    elapsedMs,
                    "I/O error connecting to " + address + ":" + port + " (" + e.getMessage() + ")",
                    e.getMessage()
            );
        }
    }
}
