package com.vigolium.extension.ui.tab;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vigolium.extension.config.BridgeSettings;
import com.vigolium.extension.config.SnapshotSettings;
import com.vigolium.extension.service.BurpBridgeService;
import com.vigolium.extension.service.SiteMapSnapshotService;
import com.vigolium.extension.ui.panel.BridgePanel;
import com.vigolium.extension.ui.panel.FilterRulesPanel;
import com.vigolium.extension.ui.panel.ProxyModePanel;
import com.vigolium.extension.ui.panel.SiteMapSnapshotPanel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import org.junit.jupiter.api.Test;

class VigoliumTabTest {

    @Test
    void exposesBridgeAsDedicatedTopLevelTabWithRenamedRecordTabs() {
        JPanel findings = new JPanel();
        JPanel records = new JPanel();
        JPanel scans = new JPanel();
        JPanel bridge = new JPanel();
        JPanel settings = new JPanel();
        JPanel logs = new JPanel();
        JTabbedPane tabs = new JTabbedPane();

        VigoliumTab.addTabs(tabs, findings, records, scans, bridge, settings, logs);

        String[] titles = new String[tabs.getTabCount()];
        for (int i = 0; i < titles.length; i++) titles[i] = tabs.getTitleAt(i);
        assertArrayEquals(
                new String[] {"Findings Records", "HTTP Records", "Scanning Records", "Bridge", "Settings", "Logs"},
                titles);
        assertSame(bridge, tabs.getComponentAt(3));
    }

    @Test
    void bridgeTabContainsSnapshotAndLiveBridgePanels() {
        SnapshotSettings snapshotSettings = mock(SnapshotSettings.class);
        when(snapshotSettings.getSnapshotIntervalMinutes()).thenReturn(5);
        SiteMapSnapshotPanel snapshot = new SiteMapSnapshotPanel(snapshotSettings, mock(SiteMapSnapshotService.class));
        BridgeSettings bridgeSettings = mock(BridgeSettings.class);
        when(bridgeSettings.getBridgeListenUrl()).thenReturn("http://127.0.0.1:9009");
        BridgePanel liveBridge = new BridgePanel(bridgeSettings, mock(BurpBridgeService.class));
        ProxyModePanel proxyMode = new ProxyModePanel();
        FilterRulesPanel filterRules = new FilterRulesPanel();

        BridgeTab bridge = new BridgeTab(snapshot, liveBridge, proxyMode, filterRules);

        assertSame(snapshot, bridge.getSiteMapSnapshotPanel());
        assertSame(liveBridge, bridge.getBridgePanel());
        assertSame(proxyMode, bridge.getProxyModePanel());
        assertSame(filterRules, bridge.getFilterRulesPanel());
    }
}
