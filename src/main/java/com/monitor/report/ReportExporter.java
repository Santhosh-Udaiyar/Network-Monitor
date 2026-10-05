package com.monitor.report;

import com.monitor.model.Host;
import com.monitor.model.MonitorResult;
import com.monitor.util.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates and exports network monitoring summary and historical data to CSV and TXT files.
 */
public class ReportExporter {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Exports hosts summary and historical check results to a CSV file.
     *
     * @param targetFile Destination CSV file
     * @param hosts List of monitored hosts
     * @throws IOException If file creation or write fails
     */
    public static void exportToCSV(File targetFile, List<Host> hosts) throws IOException {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (FileOutputStream fos = new FileOutputStream(targetFile);
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {

            // Section 1: Summary of All Monitored Hosts
            writer.println("# --- NETWORK MONITOR SUMMARY REPORT ---");
            writer.println("# Generated at: " + LocalDateTime.now().format(TIME_FORMATTER));
            writer.println("Host Name,Address,Port,Service,Status,Port Status,Latest Latency (ms),Min Latency (ms),Avg Latency (ms),Max Latency (ms),Total Checks,Failures,Uptime (%)");

            for (Host host : hosts) {
                writer.printf("\"%s\",\"%s\",%d,\"%s\",%s,%s,%d,%d,%.1f,%d,%d,%d,%.2f%%%n",
                        escapeCsv(host.getName()),
                        escapeCsv(host.getAddress()),
                        host.getPort(),
                        escapeCsv(host.getServiceName()),
                        host.getCurrentStatus(),
                        host.getCurrentPortStatus(),
                        host.getLatestLatencyMs(),
                        host.getMinLatencyMs(),
                        host.getAverageLatencyMs(),
                        host.getMaxLatencyMs(),
                        host.getTotalChecks(),
                        host.getFailureCount(),
                        host.getUptimePercentage()
                );
            }

            writer.println();
            // Section 2: Detailed Check History
            writer.println("# --- DETAILED RECENT CHECK HISTORY ---");
            writer.println("Timestamp,Host Name,Address,Port,Service,Status,Port Status,Latency (ms),Message,Error");

            for (Host host : hosts) {
                for (MonitorResult res : host.getHistorySnapshot()) {
                    writer.printf("\"%s\",\"%s\",\"%s\",%d,\"%s\",%s,%s,%d,\"%s\",\"%s\"%n",
                            res.getFormattedTime(),
                            escapeCsv(host.getName()),
                            escapeCsv(host.getAddress()),
                            host.getPort(),
                            escapeCsv(host.getServiceName()),
                            res.getStatus(),
                            res.getPortStatus(),
                            res.getLatencyMs(),
                            escapeCsv(res.getMessage()),
                            escapeCsv(res.getErrorMessage())
                    );
                }
            }
        }

        Logger.info("Exported CSV monitoring report to " + targetFile.getAbsolutePath());
    }

    /**
     * Exports a human-readable text report containing summary statistics and status.
     *
     * @param targetFile Destination TXT file
     * @param hosts List of monitored hosts
     * @throws IOException If file creation or write fails
     */
    public static void exportToTXT(File targetFile, List<Host> hosts) throws IOException {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        int upCount = 0;
        int downCount = 0;
        double globalUptimeSum = 0;

        for (Host h : hosts) {
            if (h.getCurrentStatus() == MonitorResult.Status.UP) upCount++;
            else if (h.getCurrentStatus() == MonitorResult.Status.DOWN) downCount++;
            globalUptimeSum += h.getUptimePercentage();
        }

        double avgGlobalUptime = hosts.isEmpty() ? 100.0 : (globalUptimeSum / hosts.size());

        try (FileOutputStream fos = new FileOutputStream(targetFile);
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {

            writer.println("================================================================================");
            writer.println("                       NETWORK MONITOR - SYSTEM AUDIT REPORT                    ");
            writer.println("================================================================================");
            writer.println("Generated Date/Time  : " + LocalDateTime.now().format(TIME_FORMATTER));
            writer.println("Total Hosts Monitored: " + hosts.size());
            writer.println("Hosts Online (UP)    : " + upCount);
            writer.println("Hosts Offline (DOWN) : " + downCount);
            writer.printf("Overall Mean Uptime  : %.2f%%%n", avgGlobalUptime);
            writer.println("================================================================================");
            writer.println();

            writer.println(String.format("%-22s %-16s %-6s %-15s %-7s %-8s %-10s %-8s",
                    "HOST NAME", "ADDRESS", "PORT", "SERVICE", "STATUS", "LATENCY", "UPTIME", "FAILURES"));
            writer.println("--------------------------------------------------------------------------------");

            for (Host host : hosts) {
                String latencyStr = host.getCurrentStatus() == MonitorResult.Status.UP
                        ? host.getLatestLatencyMs() + " ms"
                        : "N/A";
                writer.println(String.format("%-22s %-16s %-6d %-15s %-7s %-8s %-9.2f%% %-8d",
                        truncate(host.getName(), 22),
                        truncate(host.getAddress(), 16),
                        host.getPort(),
                        truncate(host.getServiceName(), 15),
                        host.getCurrentStatus(),
                        latencyStr,
                        host.getUptimePercentage(),
                        host.getFailureCount()
                ));
            }

            writer.println("--------------------------------------------------------------------------------");
            writer.println();
            writer.println("PER-HOST DETAILED LATENCY METRICS:");
            writer.println("--------------------------------------------------------------------------------");
            for (Host host : hosts) {
                writer.println(String.format("* %s (%s:%d - %s):", host.getName(), host.getAddress(), host.getPort(), host.getServiceName()));
                writer.println(String.format("    Status: %s | Port Status: %s | Last Checked: %s",
                        host.getCurrentStatus(), host.getCurrentPortStatus(), host.getFormattedLastCheck()));
                writer.println(String.format("    Latency - Latest: %d ms | Min: %d ms | Avg: %.1f ms | Max: %d ms",
                        host.getLatestLatencyMs(), host.getMinLatencyMs(), host.getAverageLatencyMs(), host.getMaxLatencyMs()));
                writer.println(String.format("    Checks - Total: %d | Passed: %d | Failed: %d | Uptime: %.2f%%",
                        host.getTotalChecks(), host.getSuccessfulChecks(), host.getFailureCount(), host.getUptimePercentage()));
                if (!host.getLastErrorMessage().isEmpty()) {
                    writer.println("    Last Error: " + host.getLastErrorMessage());
                }
                writer.println();
            }
            writer.println("================================================================================");
            writer.println("                             END OF REPORT                                      ");
            writer.println("================================================================================");
        }

        Logger.info("Exported TXT monitoring report to " + targetFile.getAbsolutePath());
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }

    private static String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen - 1) + "~" : str;
    }
}
