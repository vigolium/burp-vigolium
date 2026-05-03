package com.vigolium.extension.ui.tab;

import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

public class VigoliumTab extends JPanel {

    private final FindingsTab findingsTab;
    private final HttpRecordsTab httpRecordsTab;
    private final ScansContainerTab scansContainerTab;
    private final SettingsTab settingsTab;
    private final LogsTab logsTab;

    public VigoliumTab(
            FindingsTab findingsTab,
            HttpRecordsTab httpRecordsTab,
            ScansContainerTab scansContainerTab,
            SettingsTab settingsTab,
            LogsTab logsTab) {
        super(new BorderLayout());
        this.findingsTab = findingsTab;
        this.httpRecordsTab = httpRecordsTab;
        this.scansContainerTab = scansContainerTab;
        this.settingsTab = settingsTab;
        this.logsTab = logsTab;

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setName("vigoliumTabbedPane");
        tabbedPane.addTab("Findings", findingsTab);
        tabbedPane.addTab("HTTP Records", httpRecordsTab);
        tabbedPane.addTab("Scans", scansContainerTab);
        tabbedPane.addTab("Settings", settingsTab);
        tabbedPane.addTab("Logs", logsTab);

        add(tabbedPane, BorderLayout.CENTER);
        setName("vigoliumTab");
    }

    public FindingsTab getFindingsTab() {
        return findingsTab;
    }

    public HttpRecordsTab getHttpRecordsTab() {
        return httpRecordsTab;
    }

    public ScansContainerTab getScansContainerTab() {
        return scansContainerTab;
    }

    public SettingsTab getSettingsTab() {
        return settingsTab;
    }

    public LogsTab getLogsTab() {
        return logsTab;
    }
}
