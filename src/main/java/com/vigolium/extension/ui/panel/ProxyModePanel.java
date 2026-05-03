package com.vigolium.extension.ui.panel;

import java.awt.*;
import java.awt.event.ActionListener;
import javax.swing.*;

public class ProxyModePanel extends JPanel {

    private final JButton toggleButton;
    private final JLabel statusLabel;
    private final JCheckBox inScopeOnlyCheckbox;
    private boolean proxyEnabled;

    public ProxyModePanel() {
        super(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        setName("proxyModePanel");

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("Proxy Interception");
        Font base = UIManager.getFont("defaultFont");
        if (base != null) {
            titleLabel.setFont(base.deriveFont(Font.BOLD, base.getSize() + 4f));
        }
        headerPanel.add(titleLabel, BorderLayout.NORTH);

        JLabel descLabel = new JLabel(
                "When enabled, requests passing through Burp Proxy will be forwarded to Vigolium for scanning.");
        descLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        headerPanel.add(descLabel, BorderLayout.SOUTH);

        // Toggle button — no internal toggle logic, purely fires events
        toggleButton = new JButton();
        toggleButton.setName("proxyModeToggleButton");

        // Status label with colored dot
        statusLabel = new JLabel();
        statusLabel.setName("proxyModeStatusLabel");
        if (base != null) {
            statusLabel.setFont(base.deriveFont(Font.BOLD));
        }

        applyToggleStyle();

        // In-scope only checkbox
        inScopeOnlyCheckbox = new JCheckBox("Only in-scope requests");
        inScopeOnlyCheckbox.setName("proxyModeInScopeOnlyCheckbox");
        inScopeOnlyCheckbox.setSelected(false);

        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        controlsPanel.add(inScopeOnlyCheckbox);
        controlsPanel.add(toggleButton);
        controlsPanel.add(statusLabel);

        add(headerPanel, BorderLayout.NORTH);
        add(controlsPanel, BorderLayout.CENTER);
    }

    private void applyToggleStyle() {
        if (proxyEnabled) {
            toggleButton.setText("Turn OFF");
            toggleButton.putClientProperty("FlatLaf.styleClass", null);
            statusLabel.setText("\u2B24 Running");
            statusLabel.setForeground(UIManager.getColor("Colors.palette.success.3"));
        } else {
            toggleButton.setText("Turn ON");
            toggleButton.putClientProperty("FlatLaf.styleClass", "primary");
            statusLabel.setText("\u2B24 Stopped");
            statusLabel.setForeground(UIManager.getColor("Colors.palette.error.3"));
        }
    }

    public boolean isProxyEnabled() {
        return proxyEnabled;
    }

    public void setProxyEnabled(boolean enabled) {
        proxyEnabled = enabled;
        applyToggleStyle();
    }

    public void addToggleListener(ActionListener listener) {
        toggleButton.addActionListener(listener);
    }

    public boolean isInScopeOnly() {
        return inScopeOnlyCheckbox.isSelected();
    }

    public void setInScopeOnly(boolean selected) {
        inScopeOnlyCheckbox.setSelected(selected);
    }

    public void addInScopeOnlyListener(ActionListener listener) {
        inScopeOnlyCheckbox.addActionListener(listener);
    }
}
