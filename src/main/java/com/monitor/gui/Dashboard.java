package com.monitor.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.monitor.model.Host;
import com.monitor.model.MonitorResult;
import com.monitor.monitor.HostMonitor;
import com.monitor.report.ReportExporter;
import com.monitor.scheduler.MonitorScheduler;
import com.monitor.util.ConfigManager;
import com.monitor.util.Logger;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Main Swing GUI Dashboard for Network Monitor.
 */
public class Dashboard extends JFrame {

    private final MonitorScheduler scheduler;
    private final HostTableModel hostTableModel;
    private final AlertTableModel alertTableModel;

    private final JTable hostTable;
    private final JTable alertTable;
    private final LatencyChartPanel latencyChartPanel;
    private final UptimeChartPanel uptimeChartPanel;
    private final JTextArea logArea;

    // Stat KPI labels
    private final JLabel totalHostsVal = new JLabel("0");
    private final JLabel upHostsVal = new JLabel("0");
    private final JLabel downHostsVal = new JLabel("0");
    private final JLabel avgLatencyVal = new JLabel("0 ms");
    private final JLabel avgUptimeVal = new JLabel("100.0%");

    // Action buttons
    private final JButton pauseResumeBtn = new JButton("Pause");
    private final JButton checkAllBtn = new JButton("Check All Now");
    private final JButton checkSelectedBtn = new JButton("Check Selected");
    private final JButton addHostBtn = new JButton("Add Host...");
    private final JButton removeHostBtn = new JButton("Remove Selected");
    private final JButton exportCsvBtn = new JButton("Export CSV...");
    private final JButton exportTxtBtn = new JButton("Export TXT...");
    private final JButton saveConfigBtn = new JButton("Save Config");
    private final JButton reloadConfigBtn = new JButton("Reload Config");

    // Status bar
    private final JLabel statusLabel = new JLabel("Status: Initializing...");
    private final JLabel lastUpdatedLabel = new JLabel("Last Updated: -");

    public Dashboard(MonitorScheduler scheduler) {
        super("Network Monitor - Enterprise Service Availability & Performance Dashboard");
        this.scheduler = scheduler;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1240, 820);
        setMinimumSize(new Dimension(960, 640));
        setLocationRelativeTo(null);

        this.hostTableModel = new HostTableModel(scheduler.getHosts());
        this.alertTableModel = new AlertTableModel();
        this.latencyChartPanel = new LatencyChartPanel();
        this.uptimeChartPanel = new UptimeChartPanel();
        this.logArea = new JTextArea();

        this.hostTable = new JTable(hostTableModel);
        this.alertTable = new JTable(alertTableModel);

        initUI();
        wireListeners();

        // Update charts initially
        latencyChartPanel.updateHosts(scheduler.getHosts());
        uptimeChartPanel.updateHosts(scheduler.getHosts());
        updateSummaryCards();
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 0));

        // 1. Top Section: Header & KPI metric cards & Toolbar
        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));

        JPanel headerPanel = createHeaderPanel();
        JPanel kpiPanel = createKpiPanel();
        JPanel toolbarPanel = createToolbar();

        topContainer.add(headerPanel);
        topContainer.add(kpiPanel);
        topContainer.add(toolbarPanel);
        add(topContainer, BorderLayout.NORTH);

        // 2. Center Section: Tabbed Pane
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(tabbedPane.getFont().deriveFont(Font.BOLD, 12f));

        // Tab 1: Hosts Overview
        JPanel hostsPanel = new JPanel(new BorderLayout());
        setupHostTable();
        hostsPanel.add(new JScrollPane(hostTable), BorderLayout.CENTER);
        tabbedPane.addTab("  Hosts Overview  ", hostsPanel);

        // Tab 2: Latency Chart
        tabbedPane.addTab("  Latency Charts  ", latencyChartPanel);

        // Tab 3: Uptime & Failures Chart
        tabbedPane.addTab("  Uptime & Failures  ", uptimeChartPanel);

        // Tab 4: Alerts & Incidents
        JPanel alertsPanel = new JPanel(new BorderLayout());
        setupAlertTable();
        alertsPanel.add(new JScrollPane(alertTable), BorderLayout.CENTER);
        tabbedPane.addTab("  Alerts & Incidents  ", alertsPanel);

        // Tab 5: Live Event Logs
        JPanel logsPanel = new JPanel(new BorderLayout());
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(24, 26, 31));
        logArea.setForeground(new Color(210, 215, 225));
        logsPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        JPanel logControls = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clearLogBtn = new JButton("Clear Logs");
        clearLogBtn.addActionListener(e -> logArea.setText(""));
        logControls.add(clearLogBtn);
        logsPanel.add(logControls, BorderLayout.SOUTH);

        tabbedPane.addTab("  Live Logs  ", logsPanel);

        add(tabbedPane, BorderLayout.CENTER);

        // 3. Bottom Status Bar
        JPanel statusBar = new JPanel(new BorderLayout(10, 0));
        statusBar.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        lastUpdatedLabel.setFont(lastUpdatedLabel.getFont().deriveFont(11f));
        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(lastUpdatedLabel, BorderLayout.EAST);
        add(statusBar, BorderLayout.SOUTH);
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));
        panel.setBackground(new Color(30, 33, 42));

        JLabel title = new JLabel("NETWORK MONITOR");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setForeground(new Color(245, 247, 250));

        JLabel subtitle = new JLabel("Real-time Availability, TCP Port Probing, Response Time & Latency Metrics");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitle.setForeground(new Color(160, 168, 185));

        JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        textPanel.setOpaque(false);
        textPanel.add(title);
        textPanel.add(subtitle);

        panel.add(textPanel, BorderLayout.WEST);
        return panel;
    }

    private JPanel createKpiPanel() {
        JPanel kpiPanel = new JPanel(new GridLayout(1, 5, 12, 0));
        kpiPanel.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        kpiPanel.setBackground(new Color(25, 28, 36));

        kpiPanel.add(createCard("TOTAL HOSTS", totalHostsVal, new Color(52, 152, 219)));
        kpiPanel.add(createCard("HOSTS ONLINE (UP)", upHostsVal, new Color(46, 204, 113)));
        kpiPanel.add(createCard("HOSTS DOWN", downHostsVal, new Color(231, 76, 60)));
        kpiPanel.add(createCard("AVG LATENCY", avgLatencyVal, new Color(241, 196, 15)));
        kpiPanel.add(createCard("OVERALL UPTIME", avgUptimeVal, new Color(155, 89, 182)));

        return kpiPanel;
    }

    private JPanel createCard(String title, JLabel valueLabel, Color accent) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(new Color(33, 37, 48));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
        titleLabel.setForeground(new Color(150, 155, 170));

        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        valueLabel.setForeground(Color.WHITE);

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        toolbar.setBorder(BorderFactory.createEmptyBorder(4, 12, 6, 12));

        toolbar.add(pauseResumeBtn);
        toolbar.add(checkAllBtn);
        toolbar.add(checkSelectedBtn);
        toolbar.add(new JLabel(" | "));
        toolbar.add(addHostBtn);
        toolbar.add(removeHostBtn);
        toolbar.add(new JLabel(" | "));
        toolbar.add(exportCsvBtn);
        toolbar.add(exportTxtBtn);
        toolbar.add(new JLabel(" | "));
        toolbar.add(saveConfigBtn);
        toolbar.add(reloadConfigBtn);

        return toolbar;
    }

    private void setupHostTable() {
        hostTable.setRowHeight(28);
        hostTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        hostTable.getTableHeader().setReorderingAllowed(false);
        hostTable.setFont(new Font("SansSerif", Font.PLAIN, 12));

        // Column Renderers
        hostTable.getColumnModel().getColumn(4).setCellRenderer(new StatusBadgeRenderer());
        hostTable.getColumnModel().getColumn(5).setCellRenderer(new PortStatusRenderer());
        hostTable.getColumnModel().getColumn(6).setCellRenderer(new LatencyRenderer());
        hostTable.getColumnModel().getColumn(10).setCellRenderer(new UptimeProgressRenderer());

        // Select host triggers chart focus
        hostTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = hostTable.getSelectedRow();
                if (selectedRow >= 0 && selectedRow < scheduler.getHosts().size()) {
                    Host h = scheduler.getHosts().get(selectedRow);
                    latencyChartPanel.setSelectedHost(h);
                }
            }
        });
    }

    private void setupAlertTable() {
        alertTable.setRowHeight(26);
        alertTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        alertTable.getColumnModel().getColumn(1).setCellRenderer(new AlertTypeRenderer());
    }

    private void wireListeners() {
        // Scheduler host check callback
        scheduler.addListener((host, result) -> SwingUtilities.invokeLater(() -> {
            hostTableModel.fireTableDataChanged();
            updateSummaryCards();
            lastUpdatedLabel.setText("Last Checked: " + result.getFormattedTime());
            latencyChartPanel.repaint();
            uptimeChartPanel.repaint();
        }));

        // Scheduler alert callback
        scheduler.addAlertListener((host, type, message) -> SwingUtilities.invokeLater(() -> {
            alertTableModel.addAlert(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    type, host.getName(), host.getAddress() + (host.hasPort() ? ":" + host.getPort() : ""), message);
        }));

        // Logger listener
        Logger.addListener((timestamp, level, message) -> SwingUtilities.invokeLater(() -> {
            logArea.append(String.format("[%s] [%-5s] %s%n", timestamp, level.name(), message));
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }));

        // Button Actions
        pauseResumeBtn.addActionListener(e -> {
            if (scheduler.isPaused()) {
                scheduler.resume();
                pauseResumeBtn.setText("Pause");
                statusLabel.setText("Status: Running periodic checks");
            } else {
                scheduler.pause();
                pauseResumeBtn.setText("Resume");
                statusLabel.setText("Status: PAUSED");
            }
        });

        checkAllBtn.addActionListener(e -> {
            scheduler.checkAllNow();
            statusLabel.setText("Status: Triggered check for all hosts");
        });

        checkSelectedBtn.addActionListener(e -> {
            int row = hostTable.getSelectedRow();
            if (row >= 0 && row < scheduler.getHosts().size()) {
                Host h = scheduler.getHosts().get(row);
                scheduler.submitHostCheck(h);
                statusLabel.setText("Status: Checking " + h.getName() + "...");
            } else {
                JOptionPane.showMessageDialog(this, "Please select a host in the table first.", "Selection Required", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        addHostBtn.addActionListener(e -> {
            AddHostDialog dialog = new AddHostDialog(this, newHost -> {
                scheduler.addHost(newHost);
                hostTableModel.fireTableDataChanged();
                latencyChartPanel.updateHosts(scheduler.getHosts());
                uptimeChartPanel.updateHosts(scheduler.getHosts());
                updateSummaryCards();
            });
            dialog.setVisible(true);
        });

        removeHostBtn.addActionListener(e -> {
            int row = hostTable.getSelectedRow();
            if (row >= 0 && row < scheduler.getHosts().size()) {
                Host h = scheduler.getHosts().get(row);
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Are you sure you want to remove host: " + h.getName() + " (" + h.getAddress() + ")?",
                        "Confirm Host Removal", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    scheduler.removeHost(h);
                    hostTableModel.fireTableDataChanged();
                    latencyChartPanel.updateHosts(scheduler.getHosts());
                    uptimeChartPanel.updateHosts(scheduler.getHosts());
                    updateSummaryCards();
                }
            } else {
                JOptionPane.showMessageDialog(this, "Please select a host to remove.", "Selection Required", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        exportCsvBtn.addActionListener(e -> exportReport(true));
        exportTxtBtn.addActionListener(e -> exportReport(false));

        saveConfigBtn.addActionListener(e -> {
            try {
                ConfigManager.saveHosts(scheduler.getHosts());
                JOptionPane.showMessageDialog(this,
                        "Host configuration successfully saved to " + ConfigManager.DEFAULT_CONFIG_PATH,
                        "Configuration Saved", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Failed to save configuration: " + ex.getMessage(),
                        "Error Saving Config", JOptionPane.ERROR_MESSAGE);
            }
        });

        reloadConfigBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Reload configuration from " + ConfigManager.DEFAULT_CONFIG_PATH + "? Current unsaved changes will be lost.",
                    "Confirm Reload", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                List<Host> loaded = ConfigManager.loadHosts();
                scheduler.setHosts(loaded);
                hostTableModel.fireTableDataChanged();
                latencyChartPanel.updateHosts(scheduler.getHosts());
                uptimeChartPanel.updateHosts(scheduler.getHosts());
                updateSummaryCards();
                scheduler.checkAllNow();
            }
        });

        // Periodic UI update ticker
        Timer statusTicker = new Timer(1000, e -> {
            String state = scheduler.isPaused() ? "PAUSED" : (scheduler.isRunning() ? "ACTIVE" : "STOPPED");
            statusLabel.setText(String.format("Status: %s | Hosts: %d | Worker Pool: Ready",
                    state, scheduler.getHosts().size()));
        });
        statusTicker.start();
    }

    private void updateSummaryCards() {
        List<Host> hosts = scheduler.getHosts();
        int total = hosts.size();
        int up = 0;
        int down = 0;
        long totalLat = 0;
        int latCount = 0;
        double sumUptime = 0;

        for (Host h : hosts) {
            if (h.getCurrentStatus() == MonitorResult.Status.UP) {
                up++;
                totalLat += h.getLatestLatencyMs();
                latCount++;
            } else if (h.getCurrentStatus() == MonitorResult.Status.DOWN) {
                down++;
            }
            sumUptime += h.getUptimePercentage();
        }

        totalHostsVal.setText(String.valueOf(total));
        upHostsVal.setText(String.valueOf(up));
        downHostsVal.setText(String.valueOf(down));
        avgLatencyVal.setText((latCount > 0 ? (totalLat / latCount) : 0) + " ms");
        avgUptimeVal.setText(String.format("%.1f%%", total > 0 ? (sumUptime / total) : 100.0));
    }

    private void exportReport(boolean isCsv) {
        JFileChooser chooser = new JFileChooser(".");
        chooser.setSelectedFile(new File(isCsv ? "network-report.csv" : "network-report.txt"));
        chooser.setFileFilter(new FileNameExtensionFilter(
                isCsv ? "CSV Files (*.csv)" : "Text Reports (*.txt)",
                isCsv ? "csv" : "txt"
        ));

        int result = chooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                if (isCsv) {
                    ReportExporter.exportToCSV(file, scheduler.getHosts());
                } else {
                    ReportExporter.exportToTXT(file, scheduler.getHosts());
                }
                JOptionPane.showMessageDialog(this,
                        "Report exported successfully to:\n" + file.getAbsolutePath(),
                        "Export Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Export failed: " + ex.getMessage(),
                        "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // --- Table Models ---

    private static class HostTableModel extends AbstractTableModel {
        private final String[] columns = {
                "Host Name", "Address / IP", "Port", "Service", "Status",
                "Port Status", "Latency", "Min / Avg / Max", "Last Check", "Failures", "Uptime %"
        };
        private final List<Host> hosts;

        public HostTableModel(List<Host> hosts) {
            this.hosts = hosts;
        }

        @Override
        public int getRowCount() {
            return hosts.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int col) {
            return columns[col];
        }

        @Override
        public Object getValueAt(int row, int col) {
            if (row >= hosts.size()) return null;
            Host h = hosts.get(row);
            switch (col) {
                case 0: return h.getName();
                case 1: return h.getAddress();
                case 2: return h.hasPort() ? String.valueOf(h.getPort()) : "ICMP";
                case 3: return h.getServiceName();
                case 4: return h.getCurrentStatus();
                case 5: return h.getCurrentPortStatus();
                case 6: return h.getCurrentStatus() == MonitorResult.Status.UP ? h.getLatestLatencyMs() + " ms" : "Timeout";
                case 7: return String.format("%d / %.0f / %d ms", h.getMinLatencyMs(), h.getAverageLatencyMs(), h.getMaxLatencyMs());
                case 8: return h.getFormattedLastCheck();
                case 9: return h.getFailureCount();
                case 10: return h.getUptimePercentage();
                default: return "";
            }
        }
    }

    private static class AlertTableModel extends AbstractTableModel {
        private final String[] columns = {"Timestamp", "Type", "Host", "Target", "Event Details"};
        private final List<AlertEntry> entries = new ArrayList<>();

        public static class AlertEntry {
            String time;
            HostMonitor.AlertType type;
            String host;
            String target;
            String message;

            public AlertEntry(String time, HostMonitor.AlertType type, String host, String target, String message) {
                this.time = time;
                this.type = type;
                this.host = host;
                this.target = target;
                this.message = message;
            }
        }

        public void addAlert(String time, HostMonitor.AlertType type, String host, String target, String message) {
            entries.add(0, new AlertEntry(time, type, host, target, message));
            if (entries.size() > 200) {
                entries.remove(entries.size() - 1);
            }
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int col) {
            return columns[col];
        }

        @Override
        public Object getValueAt(int row, int col) {
            if (row >= entries.size()) return null;
            AlertEntry e = entries.get(row);
            switch (col) {
                case 0: return e.time;
                case 1: return e.type;
                case 2: return e.host;
                case 3: return e.target;
                case 4: return e.message;
                default: return "";
            }
        }
    }

    // --- Custom Table Cell Renderers ---

    private static class StatusBadgeRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(getFont().deriveFont(Font.BOLD, 11f));

            if (value instanceof MonitorResult.Status) {
                MonitorResult.Status s = (MonitorResult.Status) value;
                if (s == MonitorResult.Status.UP) {
                    c.setForeground(new Color(46, 204, 113));
                    setText("● UP");
                } else if (s == MonitorResult.Status.DOWN) {
                    c.setForeground(new Color(231, 76, 60));
                    setText("✖ DOWN");
                } else {
                    c.setForeground(new Color(160, 165, 175));
                    setText("○ PENDING");
                }
            }
            return c;
        }
    }

    private static class PortStatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(getFont().deriveFont(Font.PLAIN, 11f));

            if (value instanceof MonitorResult.PortStatus) {
                MonitorResult.PortStatus ps = (MonitorResult.PortStatus) value;
                switch (ps) {
                    case OPEN:
                        c.setForeground(new Color(52, 152, 219));
                        setText("OPEN");
                        break;
                    case CLOSED:
                        c.setForeground(new Color(231, 76, 60));
                        setText("CLOSED");
                        break;
                    case TIMEOUT:
                        c.setForeground(new Color(243, 156, 18));
                        setText("TIMEOUT");
                        break;
                    default:
                        c.setForeground(new Color(140, 145, 155));
                        setText("N/A");
                        break;
                }
            }
            return c;
        }
    }

    private static class LatencyRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.RIGHT);
            if (value != null && value.toString().contains("ms")) {
                try {
                    String num = value.toString().replace("ms", "").trim();
                    long ms = Long.parseLong(num);
                    if (ms < 50) c.setForeground(new Color(46, 204, 113));
                    else if (ms < 150) c.setForeground(new Color(241, 196, 15));
                    else c.setForeground(new Color(230, 126, 34));
                } catch (Exception ignored) {
                }
            } else {
                c.setForeground(new Color(231, 76, 60));
            }
            return c;
        }
    }

    private static class UptimeProgressRenderer extends JProgressBar implements TableCellRenderer {
        public UptimeProgressRenderer() {
            super(0, 100);
            setStringPainted(true);
            setBorderPainted(false);
            setFont(new Font("SansSerif", Font.BOLD, 10));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            double uptime = 100.0;
            if (value instanceof Number) {
                uptime = ((Number) value).doubleValue();
            }
            setValue((int) Math.round(uptime));
            setString(String.format("%.1f%%", uptime));

            if (uptime >= 95.0) {
                setForeground(new Color(46, 204, 113));
            } else if (uptime >= 80.0) {
                setForeground(new Color(241, 196, 15));
            } else {
                setForeground(new Color(231, 76, 60));
            }
            return this;
        }
    }

    private static class AlertTypeRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(getFont().deriveFont(Font.BOLD, 11f));

            if (value == HostMonitor.AlertType.DOWN) {
                c.setForeground(new Color(231, 76, 60));
                setText("CRITICAL DOWN");
            } else if (value == HostMonitor.AlertType.RECOVERED) {
                c.setForeground(new Color(46, 204, 113));
                setText("RECOVERED");
            }
            return c;
        }
    }
}
