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
        super(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        setName("scanOptionsPanel");

        JPanel headerPanel = new JPanel(new BorderLayout(0, 4));
        JLabel titleLabel = new JLabel("Scan Options");
        Font base = UIManager.getFont("defaultFont");
        if (base != null) {
            titleLabel.setFont(base.deriveFont(Font.BOLD, base.getSize() + 4f));
        }
        headerPanel.add(titleLabel, BorderLayout.NORTH);

        JLabel descLabel = new JLabel(
                "Optional parameters applied when sending requests to scan. Leave blank to use server defaults.");
        headerPanel.add(descLabel, BorderLayout.SOUTH);

        JPanel formPanel = new JPanel(new GridBagLayout());

        scanAllButton = new JButton("Scan All HTTP Records");
        scanAllButton.setName("scanOptionsScanAllButton");
        scanAllButton.putClientProperty("FlatLaf.styleClass", "primary");
        scanAllButton.setToolTipText(
                "Sends every HTTP record in the current project to the scanner (POST /api/scan-all-records). "
                        + "Uses the modules and timeout configured above; leave them blank for server defaults.");
        GridBagConstraints buttonConstraints = new GridBagConstraints();
        buttonConstraints.gridx = 0;
        buttonConstraints.gridy = 0;
        buttonConstraints.anchor = GridBagConstraints.LINE_START;
        buttonConstraints.insets = new Insets(0, 0, 0, 12);
        formPanel.add(scanAllButton, buttonConstraints);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.LINE_END;
        gbc.insets = new Insets(0, 0, 0, 8);

        gbc.gridx = 1;
        gbc.gridy = 0;
        formPanel.add(new JLabel("Custom modules:"), gbc);

        gbc.gridx = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 0);
        modulesField = new JTextField(32);
        modulesField.setName("scanOptionsModulesField");
        modulesField.setToolTipText(
                "Comma-separated module IDs (e.g. xss-scanner,sqli-error-based). Blank = scan all.");
        modulesField.putClientProperty(
                "JTextField.placeholderText", "xss-scanner,sqli-error-based (blank = all modules)");
        formPanel.add(modulesField, gbc);

        gbc.gridx = 3;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.LINE_END;
        gbc.insets = new Insets(0, 18, 0, 8);
        formPanel.add(new JLabel("Timeout:"), gbc);

        gbc.gridx = 4;
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.insets = new Insets(0, 0, 0, 0);
        timeoutField = new JTextField(12);
        timeoutField.setName("scanOptionsTimeoutField");
        timeoutField.setToolTipText("Request timeout as Go duration (e.g. 30s, 2m). Blank = server default.");
        timeoutField.putClientProperty("JTextField.placeholderText", "30s, 2m (blank = server default)");
        formPanel.add(timeoutField, gbc);

        scanAllStatusLabel = new JLabel(" ");
        scanAllStatusLabel.setName("scanOptionsScanAllStatusLabel");
        GridBagConstraints statusConstraints = new GridBagConstraints();
        statusConstraints.gridx = 2;
        statusConstraints.gridy = 1;
        statusConstraints.gridwidth = 3;
        statusConstraints.weightx = 1.0;
        statusConstraints.fill = GridBagConstraints.HORIZONTAL;
        statusConstraints.anchor = GridBagConstraints.LINE_START;
        statusConstraints.insets = new Insets(8, 0, 0, 0);
        formPanel.add(scanAllStatusLabel, statusConstraints);

        add(headerPanel, BorderLayout.NORTH);
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
