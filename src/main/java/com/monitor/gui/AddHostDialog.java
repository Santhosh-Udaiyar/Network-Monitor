package com.monitor.gui;

import com.monitor.model.Host;
import com.monitor.model.MonitorResult;
import com.monitor.monitor.PingMonitor;
import com.monitor.monitor.PortMonitor;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Modal dialog for creating and validating a new monitored host.
 * Includes interactive test connection probe.
 */
public class AddHostDialog extends JDialog {

    private final JTextField nameField = new JTextField(20);
    private final JTextField addressField = new JTextField(20);
    private final JTextField portField = new JTextField("80", 8);
    private final JTextField serviceField = new JTextField("Web Server", 20);
    private final JTextField intervalField = new JTextField("5", 6);
    private final JTextField timeoutField = new JTextField("2500", 6);

    private final JLabel testStatusLabel = new JLabel("Status: Ready to test");
    private final JButton testBtn = new JButton("Test Connection");
    private final JButton saveBtn = new JButton("Add Host");
    private final JButton cancelBtn = new JButton("Cancel");

    private final Consumer<Host> onHostAdded;

    public AddHostDialog(JFrame parent, Consumer<Host> onHostAdded) {
        super(parent, "Add New Monitored Host", true);
        this.onHostAdded = onHostAdded;

        setSize(480, 420);
        setLocationRelativeTo(parent);
        setResizable(false);
        setLayout(new BorderLayout());

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addFormRow(formPanel, gbc, row++, "Host Friendly Name:", nameField, "e.g. Primary Web Server, Prod DB");
        addFormRow(formPanel, gbc, row++, "Hostname or IP Address:", addressField, "e.g. 192.168.1.10, google.com");
        addFormRow(formPanel, gbc, row++, "TCP Port (0 = Ping only):", portField, "e.g. 80, 443, 5432, 0");
        addFormRow(formPanel, gbc, row++, "Service Name:", serviceField, "e.g. HTTP, PostgreSQL, DNS, ICMP");
        addFormRow(formPanel, gbc, row++, "Check Interval (sec):", intervalField, "e.g. 5 (seconds between checks)");
        addFormRow(formPanel, gbc, row++, "Connection Timeout (ms):", timeoutField, "e.g. 2500");

        // Test Connection Section
        JPanel testSection = new JPanel(new BorderLayout(8, 4));
        testSection.setBorder(BorderFactory.createTitledBorder("Connection Diagnostic Test"));
        JPanel testActionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        testActionPanel.add(testBtn);
        testActionPanel.add(testStatusLabel);
        testSection.add(testActionPanel, BorderLayout.CENTER);

        // Buttons Bar
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        btnPanel.add(cancelBtn);
        btnPanel.add(saveBtn);
        saveBtn.setFont(saveBtn.getFont().deriveFont(Font.BOLD));

        JPanel centerContainer = new JPanel();
        centerContainer.setLayout(new BoxLayout(centerContainer, BoxLayout.Y_AXIS));
        centerContainer.add(formPanel);
        centerContainer.add(testSection);

        add(centerContainer, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);

        // Action Handlers
        testBtn.addActionListener(e -> runTestConnection());
        saveBtn.addActionListener(e -> handleSave());
        cancelBtn.addActionListener(e -> dispose());
    }

    private void addFormRow(JPanel panel, GridBagConstraints gbc, int row, String label, JTextField field, String tooltip) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.35;
        JLabel lbl = new JLabel(label);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 12f));
        panel.add(lbl, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.65;
        field.setToolTipText(tooltip);
        panel.add(field, gbc);
    }

    private void runTestConnection() {
        String address = addressField.getText().trim();
        if (address.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a valid Hostname or IP address to test.", "Input Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 0 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Port must be an integer between 0 and 65535.", "Input Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int timeout;
        try {
            timeout = Integer.parseInt(timeoutField.getText().trim());
        } catch (NumberFormatException e) {
            timeout = 2500;
        }

        testBtn.setEnabled(false);
        testStatusLabel.setForeground(Color.GRAY);
        testStatusLabel.setText("Probing " + address + (port > 0 ? ":" + port : "") + "...");

        int finalTimeout = timeout;
        new Thread(() -> {
            Host probeHost = new Host(UUID.randomUUID().toString(), "Probe", address, port, "Test", 5, finalTimeout, true);
            MonitorResult res = (port > 0)
                    ? new PortMonitor().checkPort(probeHost)
                    : new PingMonitor().checkPing(probeHost);

            SwingUtilities.invokeLater(() -> {
                testBtn.setEnabled(true);
                if (res.isUp()) {
                    testStatusLabel.setForeground(new Color(46, 204, 113));
                    testStatusLabel.setText("SUCCESS: Connected in " + res.getLatencyMs() + " ms (" + res.getPortStatus() + ")");
                } else {
                    testStatusLabel.setForeground(new Color(231, 76, 60));
                    testStatusLabel.setText("FAILED: " + res.getMessage());
                }
            });
        }).start();
    }

    private void handleSave() {
        String name = nameField.getText().trim();
        String address = addressField.getText().trim();
        String service = serviceField.getText().trim();

        if (name.isEmpty() || address.isEmpty() || service.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name, Address, and Service Name must not be blank.", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 0 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Port must be a valid number between 0 and 65535.", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int interval;
        try {
            interval = Integer.parseInt(intervalField.getText().trim());
            if (interval < 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Interval must be at least 1 second.", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int timeout;
        try {
            timeout = Integer.parseInt(timeoutField.getText().trim());
            if (timeout < 100) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Timeout must be at least 100 ms.", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Host newHost = new Host(UUID.randomUUID().toString(), name, address, port, service, interval, timeout, true);
        if (onHostAdded != null) {
            onHostAdded.accept(newHost);
        }
        dispose();
    }
}
