package com.vigolium.extension.ui.panel;

import java.awt.*;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.event.DocumentListener;

public class ServerConnectionPanel extends JPanel {

    private static final String[] LOADING_FRAMES = {"Testing.", "Testing..", "Testing..."};

    private final JTextField serverUrlField;
    private final JPasswordField apiKeyField;
    private final JButton testConnectionButton;
    private final JLabel statusLabel;
    private final Timer loadingTimer;
    private int loadingFrame;

    public ServerConnectionPanel() {
        super(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        setName("serverConnectionPanel");

        JPanel headerPanel = new JPanel(new BorderLayout(0, 4));
        JLabel titleLabel = new JLabel("Server Connection");
        Font base = UIManager.getFont("defaultFont");
        if (base != null) {
            titleLabel.setFont(base.deriveFont(Font.BOLD, base.getSize() + 4f));
        }
        headerPanel.add(titleLabel, BorderLayout.NORTH);

        JLabel descLabel = new JLabel("Configure the Vigolium API server endpoint and authentication.");
        headerPanel.add(descLabel, BorderLayout.SOUTH);

        JPanel formPanel = new JPanel(new GridBagLayout());

        testConnectionButton = new JButton("Test Connection");
        testConnectionButton.setName("serverConnectionTestButton");
        testConnectionButton.putClientProperty("FlatLaf.styleClass", "primary");
        testConnectionButton.setEnabled(false);
        GridBagConstraints buttonConstraints = new GridBagConstraints();
        buttonConstraints.gridx = 0;
        buttonConstraints.gridy = 0;
        buttonConstraints.anchor = GridBagConstraints.LINE_START;
        buttonConstraints.insets = new Insets(0, 0, 0, 12);
        formPanel.add(testConnectionButton, buttonConstraints);

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 1;
        labelConstraints.gridy = 0;
        labelConstraints.anchor = GridBagConstraints.LINE_END;
        labelConstraints.insets = new Insets(0, 0, 0, 8);
        formPanel.add(new JLabel("Server URL:"), labelConstraints);

        serverUrlField = new JTextField(28);
        serverUrlField.setName("serverConnectionServerUrlField");
        serverUrlField.setToolTipText("Vigolium API server URL");
        serverUrlField.putClientProperty("JTextField.placeholderText", "http://127.0.0.1:9002");
        GridBagConstraints serverConstraints = new GridBagConstraints();
        serverConstraints.gridx = 2;
        serverConstraints.gridy = 0;
        serverConstraints.weightx = 0.6;
        serverConstraints.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(serverUrlField, serverConstraints);

        GridBagConstraints apiLabelConstraints = new GridBagConstraints();
        apiLabelConstraints.gridx = 3;
        apiLabelConstraints.gridy = 0;
        apiLabelConstraints.anchor = GridBagConstraints.LINE_END;
        apiLabelConstraints.insets = new Insets(0, 18, 0, 8);
        formPanel.add(new JLabel("API Key:"), apiLabelConstraints);

        apiKeyField = new JPasswordField(22);
        apiKeyField.setName("serverConnectionApiKeyField");
        apiKeyField.setToolTipText("API key used to authenticate with the Vigolium server");
        apiKeyField.putClientProperty("JTextField.placeholderText", "Enter API key");
        GridBagConstraints apiConstraints = new GridBagConstraints();
        apiConstraints.gridx = 4;
        apiConstraints.gridy = 0;
        apiConstraints.weightx = 0.4;
        apiConstraints.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(apiKeyField, apiConstraints);

        testConnectionButton.setEnabled(!serverUrlField.getText().trim().isEmpty());
        serverUrlField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() {
                testConnectionButton.setEnabled(!serverUrlField.getText().trim().isEmpty());
            }

            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                update();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                update();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                update();
            }
        });

        statusLabel = new JLabel("Status: Not tested");
        statusLabel.setName("serverConnectionStatusLabel");
        Color muted = UIManager.getColor("Label.disabledForeground");
        if (muted != null) statusLabel.setForeground(muted);
        GridBagConstraints statusConstraints = new GridBagConstraints();
        statusConstraints.gridx = 2;
        statusConstraints.gridy = 1;
        statusConstraints.gridwidth = 3;
        statusConstraints.weightx = 1.0;
        statusConstraints.fill = GridBagConstraints.HORIZONTAL;
        statusConstraints.anchor = GridBagConstraints.LINE_START;
        statusConstraints.insets = new Insets(8, 0, 0, 0);
        formPanel.add(statusLabel, statusConstraints);

        add(headerPanel, BorderLayout.NORTH);
        add(formPanel, BorderLayout.CENTER);

        loadingTimer = new Timer(400, e -> {
            loadingFrame = (loadingFrame + 1) % LOADING_FRAMES.length;
            testConnectionButton.setText(LOADING_FRAMES[loadingFrame]);
        });
    }

    public String getServerUrl() {
        return serverUrlField.getText().trim();
    }

    public void setServerUrl(String url) {
        serverUrlField.setText(url);
    }

    public String getApiKey() {
        return new String(apiKeyField.getPassword());
    }

    public void setApiKey(String key) {
        apiKeyField.setText(key);
    }

    public void addTestConnectionListener(ActionListener listener) {
        testConnectionButton.addActionListener(listener);
    }

    public void setConnectionStatus(boolean connected, String message) {
        Color color = connected
                ? UIManager.getColor("Colors.palette.success.3")
                : UIManager.getColor("Colors.palette.error.3");
        if (color == null) {
            color = connected ? new Color(0x2E7D32) : new Color(0xC62828);
        }
        statusLabel.setForeground(color);
        statusLabel.setText("Status: " + message);
    }

    public void setTesting(boolean testing) {
        if (testing) {
            loadingFrame = 0;
            testConnectionButton.setText(LOADING_FRAMES[0]);
            testConnectionButton.setEnabled(false);
            Color color = UIManager.getColor("Label.foreground");
            if (color != null) statusLabel.setForeground(color);
            statusLabel.setText("Status: Testing connection…");
            loadingTimer.start();
        } else {
            loadingTimer.stop();
            testConnectionButton.setText("Test Connection");
            testConnectionButton.setEnabled(true);
        }
    }

    public void addServerUrlDocumentListener(DocumentListener listener) {
        serverUrlField.getDocument().addDocumentListener(listener);
    }

    public void addApiKeyDocumentListener(DocumentListener listener) {
        apiKeyField.getDocument().addDocumentListener(listener);
    }
}
