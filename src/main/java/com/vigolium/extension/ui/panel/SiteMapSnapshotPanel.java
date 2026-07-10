package com.vigolium.extension.ui.panel;

import com.vigolium.extension.config.SnapshotSettings;
import com.vigolium.extension.service.SiteMapSnapshotService;
import com.vigolium.extension.service.SnapshotStatus;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;

public class SiteMapSnapshotPanel extends JPanel {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final SnapshotSettings settings;
    private final SiteMapSnapshotService service;
    private final JCheckBox autoSnapshot;
    private final JCheckBox inScopeOnly;
    private final JSpinner intervalMinutes;
    private final JButton snapshotNow;
    private final JLabel statusLabel;
    private final JLabel detailsLabel;

    public SiteMapSnapshotPanel(SnapshotSettings settings, SiteMapSnapshotService service) {
        super(new BorderLayout(0, 6));
        this.settings = settings;
        this.service = service;
        setName("siteMapSnapshotPanel");

        JPanel header = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Target Site Map Snapshot");
        Font base = UIManager.getFont("defaultFont");
        if (base != null) title.setFont(base.deriveFont(Font.BOLD, base.getSize() + 4f));
        header.add(title, BorderLayout.NORTH);
        header.add(
                new JLabel("Synchronize Burp Target → Site map traffic into Vigolium for agent searches."),
                BorderLayout.SOUTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        snapshotNow = new JButton("Snapshot now");
        snapshotNow.setName("siteMapSnapshotNowButton");
        snapshotNow.putClientProperty("FlatLaf.styleClass", "primary");
        autoSnapshot = new JCheckBox("Auto snapshot");
        autoSnapshot.setName("siteMapSnapshotAutoCheck");
        autoSnapshot.setSelected(settings.isSnapshotAutoEnabled());
        intervalMinutes = new JSpinner(new SpinnerNumberModel(settings.getSnapshotIntervalMinutes(), 1, 1440, 1));
        intervalMinutes.setName("siteMapSnapshotIntervalSpinner");
        inScopeOnly = new JCheckBox("In-scope only");
        inScopeOnly.setName("siteMapSnapshotInScopeCheck");
        inScopeOnly.setSelected(settings.isSnapshotInScopeOnly());

        controls.add(snapshotNow);
        controls.add(autoSnapshot);
        controls.add(new JLabel("Every"));
        controls.add(intervalMinutes);
        controls.add(new JLabel("minutes"));
        controls.add(inScopeOnly);

        JPanel status = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Not run yet");
        statusLabel.setName("siteMapSnapshotStatusLabel");
        detailsLabel = new JLabel(" ");
        detailsLabel.setName("siteMapSnapshotDetailsLabel");
        status.add(statusLabel, BorderLayout.NORTH);
        status.add(detailsLabel, BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.add(controls, BorderLayout.NORTH);
        center.add(status, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        snapshotNow.addActionListener(e -> service.snapshotNow("Button"));
        autoSnapshot.addActionListener(e -> {
            settings.setSnapshotAutoEnabled(autoSnapshot.isSelected());
            service.rescheduleAutomaticSnapshot();
        });
        intervalMinutes.addChangeListener(e -> {
            settings.setSnapshotIntervalMinutes((Integer) intervalMinutes.getValue());
            if (settings.isSnapshotAutoEnabled()) service.rescheduleAutomaticSnapshot();
        });
        inScopeOnly.addActionListener(e -> settings.setSnapshotInScopeOnly(inScopeOnly.isSelected()));
        service.setStatusListener(this::updateStatus);
    }

    private void updateStatus(SnapshotStatus status) {
        snapshotNow.setEnabled(status.state() != SnapshotStatus.State.RUNNING);
        statusLabel.setText(status.message());
        String completed = status.completedAt() == null ? "never" : TIME_FORMAT.format(status.completedAt());
        String next = status.nextRunAt() == null ? "disabled" : TIME_FORMAT.format(status.nextRunAt());
        detailsLabel.setText("Last: " + completed + " · Next: " + next + " · Discovered: " + status.discovered()
                + " · Uploaded: " + status.uploaded() + " · Inserted: " + status.inserted() + " · Updated: "
                + status.updated() + " · Failed: " + status.failed());
    }
}
