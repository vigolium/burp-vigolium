package com.vigolium.extension.ui.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.DocumentListener;

public class ScanOptionsPanel extends JPanel {

    private final JTextField modulesField;
    private final JTextField timeoutField;
    private final JButton scanAllButton;
    private final JLabel scanAllStatusLabel;

    public ScanOptionsPanel() {
        super(new BorderLayout(0, 5));
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        setName("scanOptionsPanel");

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout(5, 0));
        JLabel titleLabel = new JLabel("Scan Options");
        Font base = UIManager.getFont("defaultFont");
        if (base != null) {
            titleLabel.setFont(base.deriveFont(Font.BOLD, base.getSize() + 4f));
        }
        headerPanel.add(titleLabel, BorderLayout.CENTER);

        JLabel descLabel = new JLabel(
                "Optional parameters applied when sending requests to scan. Leave blank to use server defaults.");
        descLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(headerPanel, BorderLayout.NORTH);
        topPanel.add(descLabel, BorderLayout.SOUTH);

        // Form — single row: Custom modules: [field]   Timeout: [field]
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 0, 5);

        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JLabel("Custom modules:"), gbc);

        gbc.gridx = 1;
        modulesField = new JTextField(35);
        modulesField.setName("scanOptionsModulesField");
        modulesField.setToolTipText(
                "Comma-separated module IDs (e.g. xss-scanner,sqli-error-based). Blank = scan all.");
        modulesField.putClientProperty(
                "JTextField.placeholderText", "xss-scanner,sqli-error-based (blank = all modules)");
        formPanel.add(modulesField, gbc);

        gbc.gridx = 2;
        gbc.insets = new Insets(0, 15, 0, 5);
        formPanel.add(new JLabel("Timeout:"), gbc);

        gbc.gridx = 3;
        gbc.insets = new Insets(0, 0, 0, 5);
        timeoutField = new JTextField(12);
        timeoutField.setName("scanOptionsTimeoutField");
        timeoutField.setToolTipText("Request timeout as Go duration (e.g. 30s, 2m). Blank = server default.");
        timeoutField.putClientProperty("JTextField.placeholderText", "30s, 2m (blank = server default)");
        formPanel.add(timeoutField, gbc);

        gbc.gridx = 4;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(javax.swing.Box.createHorizontalGlue(), gbc);

        // Row 1: Scan All HTTP Records button + inline status
        scanAllButton = new JButton("Scan All HTTP Records");
        scanAllButton.setName("scanOptionsScanAllButton");
        scanAllButton.putClientProperty("FlatLaf.styleClass", "primary");
        scanAllButton.setToolTipText(
                "Sends every HTTP record in the current project to the scanner (POST /api/scan-all-records). "
                        + "Uses the modules and timeout configured above; leave them blank for server defaults.");

        scanAllStatusLabel = new JLabel(" ");
        scanAllStatusLabel.setName("scanOptionsScanAllStatusLabel");
        scanAllStatusLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));

        GridBagConstraints btnGbc = new GridBagConstraints();
        btnGbc.gridx = 0;
        btnGbc.gridy = 1;
        btnGbc.anchor = GridBagConstraints.WEST;
        btnGbc.insets = new Insets(8, 0, 0, 0);
        formPanel.add(scanAllButton, btnGbc);

        btnGbc = new GridBagConstraints();
        btnGbc.gridx = 1;
        btnGbc.gridy = 1;
        btnGbc.gridwidth = 3;
        btnGbc.anchor = GridBagConstraints.WEST;
        btnGbc.insets = new Insets(8, 0, 0, 0);
        formPanel.add(scanAllStatusLabel, btnGbc);

        add(topPanel, BorderLayout.NORTH);
        add(formPanel, BorderLayout.CENTER);
    }

    public void addScanAllListener(ActionListener listener) {
        scanAllButton.addActionListener(listener);
    }

    public void setScanAllRunning(boolean running) {
        scanAllButton.setEnabled(!running);
        if (running) {
            Color color = UIManager.getColor("Colors.ui.text.body");
            if (color != null) scanAllStatusLabel.setForeground(color);
            scanAllStatusLabel.setText("Starting scan...");
        }
    }

    public void setScanAllResult(boolean success, String message) {
        Color color =
                success ? UIManager.getColor("Colors.palette.success.3") : UIManager.getColor("Colors.palette.error.3");
        if (color == null) {
            color = success ? new Color(0x2E7D32) : new Color(0xC62828);
        }
        scanAllStatusLabel.setForeground(color);
        scanAllStatusLabel.setText(message);
    }

    public String getCustomModules() {
        return modulesField.getText().trim();
    }

    public void setCustomModules(String value) {
        modulesField.setText(value != null ? value : "");
    }

    public String getTimeout() {
        return timeoutField.getText().trim();
    }

    public void setTimeout(String value) {
        timeoutField.setText(value != null ? value : "");
    }

    public void addModulesDocumentListener(DocumentListener listener) {
        modulesField.getDocument().addDocumentListener(listener);
    }

    public void addTimeoutDocumentListener(DocumentListener listener) {
        timeoutField.getDocument().addDocumentListener(listener);
    }
}
