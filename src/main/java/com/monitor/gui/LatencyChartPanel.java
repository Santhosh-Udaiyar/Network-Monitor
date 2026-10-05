package com.monitor.gui;

import com.monitor.model.Host;
import com.monitor.model.MonitorResult;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.util.List;

/**
 * High-performance Java 2D vector chart that visualizes response time (latency in ms)
 * over time for monitored hosts, featuring gradient fills and min/avg/max guidelines.
 */
public class LatencyChartPanel extends JPanel {

    private final JComboBox<HostItem> hostSelector = new JComboBox<>();
    private final ChartCanvas canvas = new ChartCanvas();
    private Host selectedHost = null;

    public static class HostItem {
        private final Host host;

        public HostItem(Host host) {
            this.host = host;
        }

        public Host getHost() {
            return host;
        }

        @Override
        public String toString() {
            return host.getName() + " (" + host.getAddress() + (host.hasPort() ? ":" + host.getPort() : "") + ")";
        }
    }

    public LatencyChartPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);

        // Top control bar
        JPanel controlBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        controlBar.setOpaque(false);
        JLabel label = new JLabel("Select Host to View Latency Trend:");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        controlBar.add(label);

        hostSelector.setPreferredSize(new Dimension(320, 28));
        hostSelector.addActionListener(e -> {
            HostItem item = (HostItem) hostSelector.getSelectedItem();
            selectedHost = item != null ? item.getHost() : null;
            canvas.repaint();
        });
        controlBar.add(hostSelector);

        add(controlBar, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);

        // Repaint periodically for live motion
        Timer repaintTimer = new Timer(1000, e -> canvas.repaint());
        repaintTimer.start();
    }

    public void updateHosts(List<Host> hosts) {
        Host previousSelection = selectedHost;
        hostSelector.removeAllItems();

        HostItem selectedItem = null;
        for (Host host : hosts) {
            HostItem item = new HostItem(host);
            hostSelector.addItem(item);
            if (previousSelection != null && previousSelection.getId().equals(host.getId())) {
                selectedItem = item;
            }
        }

        if (selectedItem != null) {
            hostSelector.setSelectedItem(selectedItem);
            selectedHost = selectedItem.getHost();
        } else if (hostSelector.getItemCount() > 0) {
            hostSelector.setSelectedIndex(0);
            selectedHost = ((HostItem) hostSelector.getItemAt(0)).getHost();
        } else {
            selectedHost = null;
        }

        canvas.repaint();
    }

    public void setSelectedHost(Host host) {
        if (host == null) return;
        for (int i = 0; i < hostSelector.getItemCount(); i++) {
            HostItem item = hostSelector.getItemAt(i);
            if (item.getHost().getId().equals(host.getId())) {
                hostSelector.setSelectedIndex(i);
                selectedHost = host;
                canvas.repaint();
                break;
            }
        }
    }

    private class ChartCanvas extends JPanel {

        public ChartCanvas() {
            setBackground(new Color(24, 26, 31));
            setDoubleBuffered(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            // Background
            g2.setColor(new Color(20, 22, 28));
            g2.fillRect(0, 0, width, height);

            if (selectedHost == null) {
                drawEmptyMessage(g2, "No host selected. Add or select a host to view latency charts.", width, height);
                g2.dispose();
                return;
            }

            List<MonitorResult> history = selectedHost.getHistorySnapshot();
            if (history.isEmpty()) {
                drawEmptyMessage(g2, "Waiting for initial check results for " + selectedHost.getName() + "...", width, height);
                g2.dispose();
                return;
            }

            int paddingLeft = 65;
            int paddingRight = 40;
            int paddingTop = 45;
            int paddingBottom = 45;

            int plotWidth = width - paddingLeft - paddingRight;
            int plotHeight = height - paddingTop - paddingBottom;

            if (plotWidth <= 20 || plotHeight <= 20) {
                g2.dispose();
                return;
            }

            // Determine max latency scale (minimum scale 50ms)
            long maxLatency = 50;
            for (MonitorResult r : history) {
                if (r.getLatencyMs() > maxLatency) {
                    maxLatency = r.getLatencyMs();
                }
            }
            // Round max up to neat multiple
            maxLatency = (long) (Math.ceil(maxLatency / 25.0) * 25.0);

            // 1. Draw Grid lines and Y-Axis labels
            g2.setColor(new Color(50, 55, 68));
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 4f}, 0f));
            int gridTicks = 5;
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));

            for (int i = 0; i <= gridTicks; i++) {
                int y = paddingTop + (int) ((gridTicks - i) * (plotHeight / (double) gridTicks));
                g2.drawLine(paddingLeft, y, paddingLeft + plotWidth, y);

                long labelVal = (long) (i * (maxLatency / (double) gridTicks));
                String labelStr = labelVal + " ms";
                g2.setColor(new Color(150, 155, 170));
                g2.drawString(labelStr, paddingLeft - g2.getFontMetrics().stringWidth(labelStr) - 8, y + 4);
                g2.setColor(new Color(40, 44, 55));
            }

            // Draw Plot Area border
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(new Color(60, 66, 82));
            g2.drawRect(paddingLeft, paddingTop, plotWidth, plotHeight);

            // 2. Plot Latency Points & Area
            int count = history.size();
            int[] xPoints = new int[count];
            int[] yPoints = new int[count];

            double xStep = (count > 1) ? (plotWidth / (double) (count - 1)) : plotWidth;

            GeneralPath linePath = new GeneralPath();
            GeneralPath areaPath = new GeneralPath();
            areaPath.moveTo(paddingLeft, paddingTop + plotHeight);

            for (int i = 0; i < count; i++) {
                MonitorResult r = history.get(i);
                int x = (int) (paddingLeft + (i * xStep));
                long lat = Math.min(r.getLatencyMs(), maxLatency);
                int y = (int) (paddingTop + plotHeight - ((lat / (double) maxLatency) * plotHeight));

                xPoints[i] = x;
                yPoints[i] = y;

                if (i == 0) {
                    linePath.moveTo(x, y);
                    areaPath.lineTo(x, y);
                } else {
                    linePath.lineTo(x, y);
                    areaPath.lineTo(x, y);
                }
            }

            areaPath.lineTo(xPoints[count - 1], paddingTop + plotHeight);
            areaPath.closePath();

            // Gradient fill under curve
            GradientPaint gp = new GradientPaint(
                    paddingLeft, paddingTop, new Color(41, 128, 185, 110),
                    paddingLeft, paddingTop + plotHeight, new Color(41, 128, 185, 10)
            );
            g2.setPaint(gp);
            g2.fill(areaPath);

            // Draw line
            g2.setColor(new Color(52, 152, 219));
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(linePath);

            // Draw points
            for (int i = 0; i < count; i++) {
                MonitorResult r = history.get(i);
                int x = xPoints[i];
                int y = yPoints[i];

                if (r.isUp()) {
                    g2.setColor(new Color(46, 204, 113));
                } else {
                    g2.setColor(new Color(231, 76, 60));
                }
                g2.fillOval(x - 3, y - 3, 7, 7);
                g2.setColor(Color.WHITE);
                g2.drawOval(x - 3, y - 3, 7, 7);
            }

            // 3. Average Guideline
            double avgLat = selectedHost.getAverageLatencyMs();
            if (avgLat > 0 && avgLat <= maxLatency) {
                int avgY = (int) (paddingTop + plotHeight - ((avgLat / (double) maxLatency) * plotHeight));
                g2.setColor(new Color(241, 196, 15, 180));
                g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 3f}, 0f));
                g2.drawLine(paddingLeft, avgY, paddingLeft + plotWidth, avgY);
                String avgStr = String.format("Avg: %.1f ms", avgLat);
                g2.drawString(avgStr, paddingLeft + plotWidth - g2.getFontMetrics().stringWidth(avgStr) - 6, avgY - 4);
            }

            // 4. Header title & stats overlay
            g2.setFont(new Font("SansSerif", Font.BOLD, 13));
            g2.setColor(Color.WHITE);
            String title = String.format("%s (%s) - Latency History (Last %d checks)",
                    selectedHost.getName(), selectedHost.getAddress(), count);
            g2.drawString(title, paddingLeft, paddingTop - 18);

            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(new Color(180, 190, 205));
            String statsStr = String.format("Latest: %d ms  |  Min: %d ms  |  Avg: %.1f ms  |  Max: %d ms  |  Uptime: %.1f%%",
                    selectedHost.getLatestLatencyMs(),
                    selectedHost.getMinLatencyMs(),
                    selectedHost.getAverageLatencyMs(),
                    selectedHost.getMaxLatencyMs(),
                    selectedHost.getUptimePercentage());
            int statsW = g2.getFontMetrics().stringWidth(statsStr);
            g2.drawString(statsStr, paddingLeft + plotWidth - statsW, paddingTop - 18);

            // 5. Time axis markers (Start & End)
            if (count > 0) {
                g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
                g2.setColor(new Color(140, 145, 160));
                String firstTime = history.get(0).getFormattedTime();
                String lastTime = history.get(count - 1).getFormattedTime();
                g2.drawString(firstTime, paddingLeft, height - paddingBottom + 16);
                int lastW = g2.getFontMetrics().stringWidth(lastTime);
                g2.drawString(lastTime, paddingLeft + plotWidth - lastW, height - paddingBottom + 16);
            }

            g2.dispose();
        }

        private void drawEmptyMessage(Graphics2D g2, String msg, int w, int h) {
            g2.setColor(new Color(160, 165, 180));
            g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
            int sw = g2.getFontMetrics().stringWidth(msg);
            g2.drawString(msg, (w - sw) / 2, h / 2);
        }
    }
}
