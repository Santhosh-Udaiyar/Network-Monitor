package com.monitor.gui;

import com.monitor.model.Host;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/**
 * Visualizes host uptime percentages and failure counts using custom Java 2D bar and ring charts.
 */
public class UptimeChartPanel extends JPanel {

    private final List<Host> hosts = new ArrayList<>();

    public UptimeChartPanel() {
        setBackground(new Color(20, 22, 28));
        setDoubleBuffered(true);

        Timer timer = new Timer(1500, e -> repaint());
        timer.start();
    }

    public synchronized void updateHosts(List<Host> newHosts) {
        hosts.clear();
        if (newHosts != null) {
            hosts.addAll(newHosts);
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        g2.setColor(new Color(20, 22, 28));
        g2.fillRect(0, 0, width, height);

        List<Host> currentHosts;
        synchronized (this) {
            currentHosts = new ArrayList<>(this.hosts);
        }

        if (currentHosts.isEmpty()) {
            g2.setColor(new Color(160, 165, 180));
            g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
            String msg = "No hosts configured to display uptime metrics.";
            g2.drawString(msg, (width - g2.getFontMetrics().stringWidth(msg)) / 2, height / 2);
            g2.dispose();
            return;
        }

        // Layout: Left column = Global Donut Gauge & Failure Counts; Right column = Uptime Bar per Host
        int leftColWidth = Math.min(300, width / 3);
        int rightColWidth = width - leftColWidth - 30;

        // 1. Draw Global Uptime Donut Gauge
        drawGlobalDonut(g2, 20, 20, leftColWidth - 40, leftColWidth - 40, currentHosts);

        // 2. Draw Failure Breakdown in Left Column below donut
        drawFailureSummary(g2, 20, leftColWidth, leftColWidth - 20, height - leftColWidth - 30, currentHosts);

        // 3. Draw Host Uptime Bars in Right Column
        drawHostUptimeBars(g2, leftColWidth + 20, 25, rightColWidth, height - 50, currentHosts);

        g2.dispose();
    }

    private void drawGlobalDonut(Graphics2D g2, int x, int y, int w, int h, List<Host> currentHosts) {
        int diameter = Math.min(w, h);
        int cx = x + (w - diameter) / 2;
        int cy = y + (h - diameter) / 2;

        double sumUptime = 0;
        int upCount = 0;
        for (Host hst : currentHosts) {
            sumUptime += hst.getUptimePercentage();
            if (hst.isUp()) upCount++;
        }
        double avgUptime = currentHosts.isEmpty() ? 100.0 : (sumUptime / currentHosts.size());

        // Background track ring
        int strokeWidth = 16;
        g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(45, 50, 65));
        g2.drawOval(cx + strokeWidth / 2, cy + strokeWidth / 2, diameter - strokeWidth, diameter - strokeWidth);

        // Active uptime arc
        int arcAngle = (int) Math.round((avgUptime / 100.0) * 360.0);
        Color arcColor = avgUptime >= 95 ? new Color(46, 204, 113) :
                (avgUptime >= 80 ? new Color(241, 196, 15) : new Color(231, 76, 60));
        g2.setColor(arcColor);
        g2.drawArc(cx + strokeWidth / 2, cy + strokeWidth / 2, diameter - strokeWidth, diameter - strokeWidth, 90, -arcAngle);

        // Center Text
        g2.setFont(new Font("SansSerif", Font.BOLD, 18));
        g2.setColor(Color.WHITE);
        String pctStr = String.format("%.1f%%", avgUptime);
        int sw = g2.getFontMetrics().stringWidth(pctStr);
        g2.drawString(pctStr, cx + (diameter - sw) / 2, cy + (diameter / 2) + 2);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g2.setColor(new Color(170, 175, 190));
        String subStr = "GLOBAL UPTIME";
        int subW = g2.getFontMetrics().stringWidth(subStr);
        g2.drawString(subStr, cx + (diameter - subW) / 2, cy + (diameter / 2) + 18);
    }

    private void drawFailureSummary(Graphics2D g2, int x, int y, int w, int h, List<Host> currentHosts) {
        g2.setFont(new Font("SansSerif", Font.BOLD, 13));
        g2.setColor(Color.WHITE);
        g2.drawString("Incidents & Failures", x, y + 20);

        long totalFailures = 0;
        int activeDown = 0;
        for (Host host : currentHosts) {
            totalFailures += host.getFailureCount();
            if (host.getCurrentStatus() == com.monitor.model.MonitorResult.Status.DOWN) {
                activeDown++;
            }
        }

        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2.setColor(new Color(180, 185, 200));
        g2.drawString("Active Down Hosts: " + activeDown, x, y + 42);
        g2.drawString("Total Recorded Failures: " + totalFailures, x, y + 60);

        // Mini failure bars
        int itemY = y + 80;
        for (int i = 0; i < Math.min(5, currentHosts.size()); i++) {
            Host host = currentHosts.get(i);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.setColor(new Color(200, 205, 215));
            String name = host.getName();
            if (name.length() > 18) name = name.substring(0, 17) + "~";
            g2.drawString(name, x, itemY + 10);

            int barX = x + 110;
            int maxBarW = w - 150;
            int barW = totalFailures == 0 ? 0 : (int) ((host.getFailureCount() / (double) Math.max(1, totalFailures)) * maxBarW);

            g2.setColor(new Color(50, 55, 70));
            g2.fillRoundRect(barX, itemY + 2, maxBarW, 10, 4, 4);

            if (barW > 0) {
                g2.setColor(new Color(231, 76, 60));
                g2.fillRoundRect(barX, itemY + 2, Math.max(4, barW), 10, 4, 4);
            }

            g2.setColor(new Color(180, 185, 200));
            g2.drawString(String.valueOf(host.getFailureCount()), barX + maxBarW + 6, itemY + 11);

            itemY += 22;
        }
    }

    private void drawHostUptimeBars(Graphics2D g2, int x, int y, int w, int h, List<Host> currentHosts) {
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.setColor(Color.WHITE);
        g2.drawString("Host Uptime Comparison", x, y + 5);

        int rowHeight = Math.max(34, Math.min(50, (h - 40) / Math.max(1, currentHosts.size())));
        int curY = y + 30;

        int labelWidth = 180;
        int barWidth = w - labelWidth - 85;

        for (Host host : currentHosts) {
            double uptime = host.getUptimePercentage();

            // Label
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            g2.setColor(new Color(220, 225, 235));
            String label = host.getName();
            if (label.length() > 22) label = label.substring(0, 21) + "~";
            g2.drawString(label, x, curY + 14);

            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.setColor(new Color(140, 145, 160));
            g2.drawString(host.getAddress() + (host.hasPort() ? ":" + host.getPort() : "") + " (" + host.getServiceName() + ")",
                    x, curY + 26);

            // Bar background
            int bx = x + labelWidth;
            int by = curY + 6;
            int bh = 14;

            g2.setColor(new Color(40, 44, 55));
            g2.fillRoundRect(bx, by, barWidth, bh, 6, 6);

            // Uptime filled bar
            int fillW = (int) Math.round((uptime / 100.0) * barWidth);
            Color barColor = (uptime >= 95.0) ? new Color(46, 204, 113) :
                    ((uptime >= 80.0) ? new Color(241, 196, 15) : new Color(231, 76, 60));

            g2.setColor(barColor);
            if (fillW > 0) {
                g2.fillRoundRect(bx, by, fillW, bh, 6, 6);
            }

            // Percentage Text
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            g2.setColor(barColor);
            String valStr = String.format("%.1f%%", uptime);
            g2.drawString(valStr, bx + barWidth + 10, by + 12);

            curY += rowHeight;
            if (curY > y + h) break;
        }
    }
}
