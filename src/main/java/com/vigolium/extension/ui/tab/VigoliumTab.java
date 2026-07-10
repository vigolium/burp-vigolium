package com.vigolium.extension.ui.tab;

import java.awt.BorderLayout;
import java.awt.Component;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

public class VigoliumTab extends JPanel {

    private final FindingsTab findingsTab;
    private final HttpRecordsTab httpRecordsTab;
    private final ScansContainerTab scansContainerTab;
    private final BridgeTab bridgeTab;
    private final SettingsTab settingsTab;
    private final LogsTab logsTab;

    public VigoliumTab(
            FindingsTab findingsTab,
            HttpRecordsTab httpRecordsTab,
            ScansContainerTab scansContainerTab,
            BridgeTab bridgeTab,
            SettingsTab settingsTab,
            LogsTab logsTab) {
        super(new BorderLayout());
        this.findingsTab = findingsTab;
        this.httpRecordsTab = httpRecordsTab;
        this.scansContainerTab = scansContainerTab;
        this.bridgeTab = bridgeTab;
        this.settingsTab = settingsTab;
        this.logsTab = logsTab;

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setName("vigoliumTabbedPane");
        addTabs(tabbedPane, findingsTab, httpRecordsTab, scansContainerTab, bridgeTab, settingsTab, logsTab);

        add(tabbedPane, BorderLayout.CENTER);
        setName("vigoliumTab");
    }

    static void addTabs(
            JTabbedPane tabbedPane,
            Component findings,
            Component httpRecords,
            Component scans,
            Component bridge,
            Component settings,
            Component logs) {
        tabbedPane.addTab("Findings Records", findings);
        tabbedPane.addTab("HTTP Records", httpRecords);
        tabbedPane.addTab("Scanning Records", scans);
        tabbedPane.addTab("Bridge", bridge);
        tabbedPane.addTab("Settings", settings);
        tabbedPane.addTab("Logs", logs);
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

    public BridgeTab getBridgeTab() {
        return bridgeTab;
    }

    public SettingsTab getSettingsTab() {
        return settingsTab;
    }

    public LogsTab getLogsTab() {
        return logsTab;
    }
}
