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
        super(new BorderLayout(0, 3));
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        setName("serverConnectionPanel");

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("Server Connection");
        Font base = UIManager.getFont("defaultFont");
        if (base != null) {
            titleLabel.setFont(base.deriveFont(Font.BOLD, base.getSize() + 4f));
        }
        headerPanel.add(titleLabel, BorderLayout.NORTH);

        JLabel descLabel = new JLabel("Configure the Vigolium API server endpoint and authentication.");
        descLabel.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        headerPanel.add(descLabel, BorderLayout.SOUTH);

        // Form: Server URL on row 0, API Key on row 2, Test Connection + Status on row 4
        JPanel formPanel = new JPanel();
        GridBagLayout layout = new GridBagLayout();
        layout.columnWidths = new int[] {0, 5, 0, 10, 0, 0};
        layout.rowHeights = new int[] {0, 3, 0, 5, 0};
        formPanel.setLayout(layout);

        GridBagConstraints gbc = new GridBagConstraints();

        // Row 0: Server URL label + field
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.LINE_START;
        formPanel.add(new JLabel("Server URL:"), gbc);

        serverUrlField = new JTextField(40);
        serverUrlField.setName("serverConnectionServerUrlField");
        gbc.gridx = 2;
        gbc.anchor = GridBagConstraints.ABOVE_BASELINE;
        formPanel.add(serverUrlField, gbc);

        // Row 2: API Key label + field
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.LINE_START;
        formPanel.add(new JLabel("API Key:"), gbc);

        apiKeyField = new JPasswordField(40);
        apiKeyField.setName("serverConnectionApiKeyField");
        gbc.gridx = 2;
        gbc.anchor = GridBagConstraints.ABOVE_BASELINE;
        formPanel.add(apiKeyField, gbc);

        // Row 4: Test Connection (under labels) + Status
        testConnectionButton = new JButton("Test Connection");
        testConnectionButton.setName("serverConnectionTestButton");
        testConnectionButton.putClientProperty("FlatLaf.styleClass", "primary");
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
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.anchor = GridBagConstraints.LINE_START;
        formPanel.add(testConnectionButton, gbc);

        statusLabel = new JLabel(" ");
        statusLabel.setName("serverConnectionStatusLabel");
        gbc.gridx = 2;
        gbc.anchor = GridBagConstraints.LINE_START;
        formPanel.add(statusLabel, gbc);

        // Filler — pushes everything left
        gbc = new GridBagConstraints();
        gbc.gridx = 5;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(
                new Box.Filler(new Dimension(0, 0), new Dimension(0, 0), new Dimension(0, Integer.MAX_VALUE)), gbc);

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
