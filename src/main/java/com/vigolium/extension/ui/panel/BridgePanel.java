package com.vigolium.extension.ui.panel;

import com.vigolium.extension.config.BridgeSettings;
import com.vigolium.extension.service.BridgeStatus;
import com.vigolium.extension.service.BurpBridgeService;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class BridgePanel extends JPanel {

    private final BridgeSettings settings;
    private final BurpBridgeService service;
    private final JCheckBox enabled;
    private final JCheckBox inScopeOnly;
    private final JTextField url;
    private final JButton reconnect;
    private final JButton testConnection;
    private final JLabel status;
    private final JLabel endpoint;

    public BridgePanel(BridgeSettings settings, BurpBridgeService service) {
        super(new BorderLayout(0, 6));
        this.settings = settings;
        this.service = service;
        setName("bridgePanel");

        JPanel header = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Bridge");
        Font base = UIManager.getFont("defaultFont");
        if (base != null) title.setFont(base.deriveFont(Font.BOLD, base.getSize() + 4f));
        header.add(title, BorderLayout.NORTH);
        header.add(
                new JLabel("Loopback-only traffic exchange: query live history or add Vigolium records to Site map."),
                BorderLayout.SOUTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        enabled = new JCheckBox("Enable live bridge");
        enabled.setName("bridgeEnabledCheck");
        enabled.setSelected(settings.isBridgeEnabled());
        inScopeOnly = new JCheckBox("In-scope items only");
        inScopeOnly.setName("bridgeInScopeOnlyCheck");
        inScopeOnly.setSelected(settings.isBridgeInScopeOnly());
        url = new JTextField(settings.getBridgeListenUrl(), 32);
        url.setName("bridgeListenUrlField");
        reconnect = new JButton("Start / Restart");
        reconnect.setName("bridgeReconnectButton");
        reconnect.putClientProperty("FlatLaf.styleClass", "primary");
        testConnection = new JButton("Test Connection");
        testConnection.setName("bridgeTestConnectionButton");
        testConnection.putClientProperty("FlatLaf.styleClass", "primary");
        controls.add(enabled);
        controls.add(inScopeOnly);
        controls.add(new JLabel("Listener URL:"));
        controls.add(url);
        controls.add(reconnect);
        controls.add(testConnection);

        JPanel statusPanel = new JPanel(new BorderLayout(0, 4));
        status = new JLabel(settings.isBridgeEnabled() ? "Starting…" : "Bridge disabled");
        status.setName("bridgeStatusLabel");
        endpoint = new JLabel(" ");
        endpoint.setName("bridgeEndpointLabel");
        statusPanel.add(status, BorderLayout.NORTH);
        statusPanel.add(endpoint, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.add(controls, BorderLayout.NORTH);
        center.add(statusPanel, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        enabled.addActionListener(e -> {
            save();
            settings.setBridgeEnabled(enabled.isSelected());
            service.restart();
        });
        inScopeOnly.addActionListener(e -> settings.setBridgeInScopeOnly(inScopeOnly.isSelected()));
        reconnect.addActionListener(e -> {
            save();
            service.restart();
        });
        testConnection.addActionListener(e -> testConnection());
        url.addActionListener(e -> reconnect.doClick());
        service.setStatusListener(this::updateStatus);
    }

    private void save() {
        settings.setBridgeListenUrl(url.getText());
    }

    private void testConnection() {
        save();
        testConnection.setEnabled(false);
        testConnection.setText("Testing…");
        status.setText("Testing bridge connection…");
        service.testConnection()
                .whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
                    testConnection.setEnabled(true);
                    testConnection.setText("Test Connection");
                    if (error != null) {
                        status.setText("Connection test failed: " + error.getMessage());
                    } else {
                        status.setText(result.message());
                    }
                }));
    }

    private void updateStatus(BridgeStatus bridgeStatus) {
        status.setText(bridgeStatus.message());
        endpoint.setText(bridgeStatus.listenUrl() == null ? " " : "Endpoint: " + bridgeStatus.listenUrl());
    }
}
