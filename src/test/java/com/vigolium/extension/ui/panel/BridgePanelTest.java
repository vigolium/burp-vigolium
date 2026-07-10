package com.vigolium.extension.ui.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vigolium.extension.config.BridgeSettings;
import com.vigolium.extension.config.SnapshotSettings;
import com.vigolium.extension.service.BurpBridgeService;
import com.vigolium.extension.service.SiteMapSnapshotService;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.CompletableFuture;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import org.junit.jupiter.api.Test;

class BridgePanelTest {

    @Test
    void bridgeControlsPersistScopeAndTestTheConnection() {
        BridgeSettings settings = mock(BridgeSettings.class);
        when(settings.getBridgeListenUrl()).thenReturn("http://127.0.0.1:9009");
        when(settings.isBridgeInScopeOnly()).thenReturn(false);
        BurpBridgeService service = mock(BurpBridgeService.class);
        when(service.testConnection())
                .thenReturn(CompletableFuture.completedFuture(
                        new BurpBridgeService.ConnectionTestResult(true, "Bridge connection successful")));
        BridgePanel panel = new BridgePanel(settings, service);

        assertNotNull(find(panel, "bridgeEndpointLabel", Component.class));

        JCheckBox inScopeOnly = find(panel, "bridgeInScopeOnlyCheck", JCheckBox.class);
        assertNotNull(inScopeOnly);
        assertFalse(inScopeOnly.isSelected());
        inScopeOnly.doClick();
        verify(settings).setBridgeInScopeOnly(true);

        JButton testConnection = find(panel, "bridgeTestConnectionButton", JButton.class);
        assertNotNull(testConnection);
        assertEquals("primary", testConnection.getClientProperty("FlatLaf.styleClass"));
        testConnection.doClick();
        verify(service).testConnection();
    }

    @Test
    void primaryBridgeActionsUseTheOrangeAccentStyle() {
        SnapshotSettings snapshotSettings = mock(SnapshotSettings.class);
        when(snapshotSettings.getSnapshotIntervalMinutes()).thenReturn(5);
        SiteMapSnapshotPanel snapshotPanel =
                new SiteMapSnapshotPanel(snapshotSettings, mock(SiteMapSnapshotService.class));
        AbstractButton snapshot = find(snapshotPanel, "siteMapSnapshotNowButton", AbstractButton.class);

        BridgeSettings bridgeSettings = mock(BridgeSettings.class);
        when(bridgeSettings.getBridgeListenUrl()).thenReturn("http://127.0.0.1:9009");
        BridgePanel bridgePanel = new BridgePanel(bridgeSettings, mock(BurpBridgeService.class));
        AbstractButton restart = find(bridgePanel, "bridgeReconnectButton", AbstractButton.class);

        assertNotNull(snapshot);
        assertNotNull(restart);
        assertEquals("primary", snapshot.getClientProperty("FlatLaf.styleClass"));
        assertEquals("primary", restart.getClientProperty("FlatLaf.styleClass"));
    }

    private static <T extends Component> T find(Container root, String name, Class<T> type) {
        for (Component component : root.getComponents()) {
            if (name.equals(component.getName()) && type.isInstance(component)) return type.cast(component);
            if (component instanceof Container child) {
                T match = find(child, name, type);
                if (match != null) return match;
            }
        }
        return null;
    }
}
