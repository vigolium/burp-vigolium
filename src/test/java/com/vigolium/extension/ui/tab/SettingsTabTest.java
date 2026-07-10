package com.vigolium.extension.ui.tab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import com.vigolium.extension.controller.HotkeyController;
import com.vigolium.extension.ui.panel.HotkeysPanel;
import com.vigolium.extension.ui.panel.RequestStatsPanel;
import com.vigolium.extension.ui.panel.ScanOptionsPanel;
import com.vigolium.extension.ui.panel.ServerConnectionPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class SettingsTabTest {

    @Test
    void showsThePackagedExtensionVersion() {
        SettingsTab tab = settingsTab("1.2.3");

        JLabel version = find(tab, "settingsExtensionVersionLabel", JLabel.class);
        JTextField refreshShortcut = find(tab, "hotkeyRefreshRecordsField", JTextField.class);

        assertNotNull(version);
        assertEquals("Extension v1.2.3", version.getText());
        assertNotNull(refreshShortcut);
        assertEquals("Ctrl+Alt+R", refreshShortcut.getText());
    }

    @Test
    void serverUrlApiKeyAndActionShareOneAlignedRow() {
        ServerConnectionPanel panel = new ServerConnectionPanel();
        JTextField serverUrl = find(panel, "serverConnectionServerUrlField", JTextField.class);
        JPasswordField apiKey = find(panel, "serverConnectionApiKeyField", JPasswordField.class);
        JButton test = find(panel, "serverConnectionTestButton", JButton.class);

        assertNotNull(serverUrl);
        assertNotNull(apiKey);
        assertNotNull(test);
        assertSame(serverUrl.getParent(), apiKey.getParent());
        assertSame(serverUrl.getParent(), test.getParent());
        GridBagLayout layout = (GridBagLayout) serverUrl.getParent().getLayout();
        assertEquals(layout.getConstraints(serverUrl).gridy, layout.getConstraints(apiKey).gridy);
        assertEquals(layout.getConstraints(serverUrl).gridy, layout.getConstraints(test).gridy);
        assertEquals(0, layout.getConstraints(test).gridx);
    }

    @Test
    void scanInputsAndActionShareOneAlignedRow() {
        ScanOptionsPanel panel = new ScanOptionsPanel();
        JTextField modules = find(panel, "scanOptionsModulesField", JTextField.class);
        JTextField timeout = find(panel, "scanOptionsTimeoutField", JTextField.class);
        JButton scan = find(panel, "scanOptionsScanAllButton", JButton.class);

        assertNotNull(modules);
        assertNotNull(timeout);
        assertNotNull(scan);
        assertSame(modules.getParent(), timeout.getParent());
        assertSame(modules.getParent(), scan.getParent());
        GridBagLayout layout = (GridBagLayout) modules.getParent().getLayout();
        assertEquals(layout.getConstraints(modules).gridy, layout.getConstraints(timeout).gridy);
        assertEquals(layout.getConstraints(modules).gridy, layout.getConstraints(scan).gridy);
        assertEquals(0, layout.getConstraints(scan).gridx);
    }

    private static SettingsTab settingsTab(String version) {
        return new SettingsTab(
                new ServerConnectionPanel(),
                new ScanOptionsPanel(),
                new RequestStatsPanel(),
                new HotkeysPanel(mock(HotkeyController.class)),
                version);
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
