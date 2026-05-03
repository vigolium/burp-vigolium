package com.vigolium.extension.ui.tab;

import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

public class ScansContainerTab extends JPanel {

    private final ScansTab scansTab;
    private final AgentSessionsTab agentSessionsTab;

    public ScansContainerTab(ScansTab scansTab, AgentSessionsTab agentSessionsTab) {
        super(new BorderLayout());
        this.scansTab = scansTab;
        this.agentSessionsTab = agentSessionsTab;

        JTabbedPane tabs = new JTabbedPane();
        tabs.setName("scansContainerTabs");
        tabs.addTab("Native Scans", scansTab);
        tabs.addTab("Agentic Scans", agentSessionsTab);

        add(tabs, BorderLayout.CENTER);
        setName("scansContainerTab");
    }

    public ScansTab getScansTab() {
        return scansTab;
    }

    public AgentSessionsTab getAgentSessionsTab() {
        return agentSessionsTab;
    }
}
