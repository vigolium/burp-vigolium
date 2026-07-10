package com.vigolium.extension.ui.panel;

import com.vigolium.extension.controller.HotkeyController;
import com.vigolium.extension.ui.dialog.HotkeyDialog;
import java.awt.*;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class HotkeysPanel extends JPanel {

    private final HotkeyController hotkeyController;
    private final JTextField ingestField;
    private final JTextField scanField;
    private final JTextField agentScanField;
    private final JTextField snapshotSitemapField;
    private final JTextField refreshRecordsField;
    private String ingestMontoyaKey;
    private String scanMontoyaKey;
    private String agentScanMontoyaKey;
    private String snapshotSitemapMontoyaKey;

    public HotkeysPanel(HotkeyController hotkeyController) {
        super(new BorderLayout(0, 3));
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        setName("hotkeysPanel");
        this.hotkeyController = hotkeyController;

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("Keyboard Shortcuts");
        Font base = UIManager.getFont("defaultFont");
        if (base != null) {
            titleLabel.setFont(base.deriveFont(Font.BOLD, base.getSize() + 4f));
        }
        headerPanel.add(titleLabel, BorderLayout.NORTH);

        JLabel descLabel = new JLabel("Configure action shortcuts. Record-view refresh uses Ctrl+Alt+R.");
        descLabel.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        headerPanel.add(descLabel, BorderLayout.SOUTH);

        // Form
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagLayout layout = (GridBagLayout) formPanel.getLayout();
        layout.columnWidths = new int[] {0, 5, 0, 5, 0};
        layout.rowHeights = new int[] {0, 2, 0, 2, 0};

        GridBagConstraints gbc = new GridBagConstraints();

        // Row 0: Send to Ingestion
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.LINE_START;
        formPanel.add(new JLabel("Send to Ingestion (/api/ingest-http):"), gbc);

        ingestField = createHotkeyField("hotkeyIngestField");
        gbc.gridx = 2;
        gbc.anchor = GridBagConstraints.ABOVE_BASELINE;
        formPanel.add(ingestField, gbc);

        ingestField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openIngestDialog();
            }
        });

        // Row 2: Send to Native Scan
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.LINE_START;
        formPanel.add(new JLabel("Send to Native Scan (/api/scan-request):"), gbc);

        scanField = createHotkeyField("hotkeyScanField");
        gbc.gridx = 2;
        gbc.anchor = GridBagConstraints.ABOVE_BASELINE;
        formPanel.add(scanField, gbc);

        scanField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openScanDialog();
            }
        });

        // Row 4: Send to Agentic Scan
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.anchor = GridBagConstraints.LINE_START;
        formPanel.add(new JLabel("Send to Agentic Scan (/api/agent/run/swarm):"), gbc);

        agentScanField = createHotkeyField("hotkeyAgentScanField");
        gbc.gridx = 2;
        gbc.anchor = GridBagConstraints.ABOVE_BASELINE;
        formPanel.add(agentScanField, gbc);

        agentScanField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openAgentScanDialog();
            }
        });

        // Row 6: Snapshot Target Site Map
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.anchor = GridBagConstraints.LINE_START;
        formPanel.add(new JLabel("Snapshot Target Site Map (/api/burp/sitemap/snapshot):"), gbc);

        snapshotSitemapField = createHotkeyField("hotkeySnapshotSitemapField");
        gbc.gridx = 2;
        gbc.anchor = GridBagConstraints.ABOVE_BASELINE;
        formPanel.add(snapshotSitemapField, gbc);

        snapshotSitemapField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openSnapshotSitemapDialog();
            }
        });

        // Row 8: fixed contextual refresh shortcut
        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.anchor = GridBagConstraints.LINE_START;
        formPanel.add(new JLabel("Refresh active records view:"), gbc);

        refreshRecordsField = new JTextField(20);
        refreshRecordsField.setEditable(false);
        refreshRecordsField.setFocusable(false);
        refreshRecordsField.setName("hotkeyRefreshRecordsField");
        refreshRecordsField.setText("Ctrl+Alt+R");
        refreshRecordsField.setToolTipText("Refreshes the active Findings, HTTP, Native Scans, or Agentic Scans view");
        gbc.gridx = 2;
        gbc.anchor = GridBagConstraints.ABOVE_BASELINE;
        formPanel.add(refreshRecordsField, gbc);

        // Filler
        gbc.gridx = 4;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        formPanel.add(
                new Box.Filler(
                        new java.awt.Dimension(0, 0),
                        new java.awt.Dimension(0, 0),
                        new java.awt.Dimension(0, Integer.MAX_VALUE)),
                gbc);

        add(headerPanel, BorderLayout.NORTH);
        add(formPanel, BorderLayout.CENTER);
    }

    public void setIngestHotkey(String montoyaKey) {
        this.ingestMontoyaKey = montoyaKey;
        ingestField.setText(displayText(montoyaKey));
    }

    public void setScanHotkey(String montoyaKey) {
        this.scanMontoyaKey = montoyaKey;
        scanField.setText(displayText(montoyaKey));
    }

    public void setAgentScanHotkey(String montoyaKey) {
        this.agentScanMontoyaKey = montoyaKey;
        agentScanField.setText(displayText(montoyaKey));
    }

    public void setSnapshotSitemapHotkey(String montoyaKey) {
        this.snapshotSitemapMontoyaKey = montoyaKey;
        snapshotSitemapField.setText(displayText(montoyaKey));
    }

    private void openIngestDialog() {
        HotkeyDialog dialog = new HotkeyDialog(
                SwingUtilities.getWindowAncestor(this), "Send to Ingestion", ingestMontoyaKey, newKey -> {
                    setIngestHotkey(newKey);
                    hotkeyController.updateIngestHotkey(newKey);
                });
        dialog.open();
    }

    private void openScanDialog() {
        HotkeyDialog dialog = new HotkeyDialog(
                SwingUtilities.getWindowAncestor(this), "Send to Native Scan", scanMontoyaKey, newKey -> {
                    setScanHotkey(newKey);
                    hotkeyController.updateScanHotkey(newKey);
                });
        dialog.open();
    }

    private void openAgentScanDialog() {
        HotkeyDialog dialog = new HotkeyDialog(
                SwingUtilities.getWindowAncestor(this), "Send to Agentic Scan", agentScanMontoyaKey, newKey -> {
                    setAgentScanHotkey(newKey);
                    hotkeyController.updateAgentScanHotkey(newKey);
                });
        dialog.open();
    }

    private void openSnapshotSitemapDialog() {
        HotkeyDialog dialog = new HotkeyDialog(
                SwingUtilities.getWindowAncestor(this),
                "Snapshot Target Site Map",
                snapshotSitemapMontoyaKey,
                newKey -> {
                    setSnapshotSitemapHotkey(newKey);
                    hotkeyController.updateSnapshotSitemapHotkey(newKey);
                });
        dialog.open();
    }

    private static JTextField createHotkeyField(String name) {
        JTextField field = new JTextField(20);
        field.setEditable(false);
        field.setFocusable(true);
        field.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        field.setName(name);
        field.setText("Not set");
        return field;
    }

    private static String displayText(String montoyaKey) {
        if (montoyaKey == null || montoyaKey.isBlank()) return "Not set";
        return montoyaKey;
    }
}
