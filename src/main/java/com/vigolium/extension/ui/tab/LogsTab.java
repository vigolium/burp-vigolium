package com.vigolium.extension.ui.tab;

import com.vigolium.extension.service.LogService;
import java.awt.*;
import javax.swing.*;

public class LogsTab extends JPanel {

    private final JTextArea logArea;
    private final JCheckBox autoScrollCheck;
    private final JComboBox<Integer> maxEntriesCombo;
    private final LogService logService;

    public LogsTab(LogService logService) {
        super(new BorderLayout());
        this.logService = logService;

        logArea = new JTextArea();
        logArea.setName("logsTextArea");
        logArea.setEditable(false);
        logArea.setFont(new Font(
                Font.MONOSPACED,
                Font.PLAIN,
                UIManager.getFont("defaultFont") != null
                        ? UIManager.getFont("defaultFont").getSize()
                        : 12));

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.getVerticalScrollBar().setUnitIncrement(25);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(25);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        toolbar.setName("logsToolbar");

        JButton clearBtn = new JButton("Clear");
        clearBtn.setName("logsClearButton");

        autoScrollCheck = new JCheckBox("Auto-scroll", true);
        autoScrollCheck.setName("logsAutoScrollCheck");

        maxEntriesCombo = new JComboBox<>(new Integer[] {500, 1000, 5000});
        maxEntriesCombo.setName("logsMaxEntriesCombo");
        maxEntriesCombo.setSelectedItem(1000);

        toolbar.add(clearBtn);
        toolbar.add(autoScrollCheck);
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(new JLabel("Max entries:"));
        toolbar.add(maxEntriesCombo);

        add(toolbar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        // Wire listeners
        clearBtn.addActionListener(e -> {
            logService.clear();
            logArea.setText("");
        });

        maxEntriesCombo.addActionListener(e -> {
            Integer max = (Integer) maxEntriesCombo.getSelectedItem();
            if (max != null) {
                logService.setMaxEntries(max);
            }
        });

        logService.addChangeListener(this::refreshLogs);

        setName("logsTab");
    }

    private void refreshLogs() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refreshLogs);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (LogService.LogEntry entry : logService.getEntries()) {
            sb.append(String.format("[%s] %-5s %s%n", entry.timestamp(), entry.level(), entry.message()));
        }
        logArea.setText(sb.toString());

        if (autoScrollCheck.isSelected()) {
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }
    }

    public JTextArea getLogArea() {
        return logArea;
    }
}
