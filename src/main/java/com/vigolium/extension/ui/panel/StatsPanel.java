package com.vigolium.extension.ui.panel;

import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

public class StatsPanel extends JPanel {

    private final JLabel sentLabel;
    private final JLabel pendingLabel;
    private final JLabel failedLabel;

    public StatsPanel(String namePrefix) {
        super(new FlowLayout(FlowLayout.LEFT, 10, 0));
        setName(namePrefix + "StatsPanel");

        sentLabel = new JLabel("Sent: 0");
        sentLabel.setName(namePrefix + "SentLabel");
        pendingLabel = new JLabel("Pending: 0");
        pendingLabel.setName(namePrefix + "PendingLabel");
        failedLabel = new JLabel("Failed: 0");
        failedLabel.setName(namePrefix + "FailedLabel");

        add(sentLabel);
        add(verticalSeparator());
        add(pendingLabel);
        add(verticalSeparator());
        add(failedLabel);
    }

    public void updateCounters(int sent, int pending, int failed) {
        sentLabel.setText("Sent: " + sent);
        pendingLabel.setText("Pending: " + pending);
        failedLabel.setText("Failed: " + failed);
    }

    private static JSeparator verticalSeparator() {
        return new JSeparator(SwingConstants.VERTICAL) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(1, 20);
            }
        };
    }
}
