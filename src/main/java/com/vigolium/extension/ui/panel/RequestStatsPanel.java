package com.vigolium.extension.ui.panel;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

public class RequestStatsPanel extends JPanel {

    private final StatsPanel ingestStats;
    private final StatsPanel scanStats;

    public RequestStatsPanel() {
        super(new BorderLayout(0, 5));
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        setName("requestStatsPanel");

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("Request Statistics");
        Font base = UIManager.getFont("defaultFont");
        if (base != null) {
            titleLabel.setFont(base.deriveFont(Font.BOLD, base.getSize() + 4f));
        }
        headerPanel.add(titleLabel, BorderLayout.NORTH);

        JLabel descLabel = new JLabel("Counters for requests sent via proxy, context menu, and hotkeys.");
        descLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        headerPanel.add(descLabel, BorderLayout.SOUTH);

        // Form — single row: Ingestion: <stats>     Scan: <stats>
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.insets = new Insets(0, 0, 0, 5);

        JLabel ingestLabel = new JLabel("Ingestion:");
        if (base != null) {
            ingestLabel.setFont(base.deriveFont(Font.BOLD));
        }
        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(ingestLabel, gbc);

        ingestStats = new StatsPanel("ingest");
        gbc.gridx = 1;
        formPanel.add(ingestStats, gbc);

        JLabel scanLabel = new JLabel("Scan:");
        if (base != null) {
            scanLabel.setFont(base.deriveFont(Font.BOLD));
        }
        gbc.gridx = 2;
        gbc.insets = new Insets(0, 20, 0, 5);
        formPanel.add(scanLabel, gbc);

        scanStats = new StatsPanel("scan");
        gbc.gridx = 3;
        gbc.insets = new Insets(0, 0, 0, 5);
        formPanel.add(scanStats, gbc);

        // Filler
        gbc.gridx = 4;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(
                new Box.Filler(new Dimension(0, 0), new Dimension(0, 0), new Dimension(0, Integer.MAX_VALUE)), gbc);

        add(headerPanel, BorderLayout.NORTH);
        add(formPanel, BorderLayout.CENTER);
    }

    public StatsPanel getIngestStats() {
        return ingestStats;
    }

    public StatsPanel getScanStats() {
        return scanStats;
    }
}
