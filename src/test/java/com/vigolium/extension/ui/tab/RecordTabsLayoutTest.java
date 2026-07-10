package com.vigolium.extension.ui.tab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class RecordTabsLayoutTest {

    @Test
    void everyFindingsDataColumnHasASortField() {
        Map<String, String> expected = Map.of(
                "Severity", "severity",
                "Module", "module_name",
                "Description", "description",
                "Confidence", "confidence",
                "Matched At", "matched_at",
                "Found At", "found_at");

        expected.forEach((column, field) -> assertEquals(field, FindingsTab.mapColumnToSortField(column)));
        assertNull(FindingsTab.mapColumnToSortField("#"));
    }

    @Test
    void everyHttpRecordsDataColumnHasASortField() {
        Map<String, String> expected = Map.of(
                "Method", "method",
                "Status", "status_code",
                "Host", "hostname",
                "Path", "path",
                "Length", "response_content_length",
                "Time (ms)", "response_time",
                "Risk", "risk_score",
                "Source", "source",
                "Sent At", "sent_at");

        expected.forEach((column, field) -> assertEquals(field, HttpRecordsTab.mapColumnToSortField(column)));
        assertNull(HttpRecordsTab.mapColumnToSortField("#"));
    }

    @Test
    void findingsToolbarLeadsWithRefreshAndUsesComfortableSpacing() {
        FindingsTab tab = new FindingsTab(montoyaApi());
        JButton refresh = find(tab, "findingsRefreshButton", JButton.class);
        JTextField search = find(tab, "findingsSearchField", JTextField.class);
        JPanel toolbar = find(tab, "findingsToolbar", JPanel.class);
        JTable table = find(tab, "findingsTable", JTable.class);

        assertNotNull(refresh);
        assertNotNull(search);
        assertSame(refresh.getParent(), search.getParent());
        assertTrue(indexOf(refresh.getParent(), refresh) < indexOf(search.getParent(), search));
        assertTrue(toolbar.getInsets().top >= 8);
        assertTrue(table.getRowHeight() >= 24);
        assertRefreshShortcut(tab, refresh);
    }

    @Test
    void findingsMarkdownCopyActionSitsBesideDescriptionAndUsesPrimaryStyle() {
        FindingsTab tab = new FindingsTab(montoyaApi());
        JButton description = find(tab, "findingsToggleDescriptionButton", JButton.class);
        JButton copy = find(tab, "findingsCopyDetailsButton", JButton.class);

        assertNotNull(description);
        assertNotNull(copy);
        assertSame(description.getParent(), copy.getParent());
        assertEquals(indexOf(description.getParent(), description) + 1, indexOf(copy.getParent(), copy));
        assertEquals("Copy Finding as Markdown", copy.getText());
        assertEquals("primary", copy.getClientProperty("FlatLaf.styleClass"));
    }

    @Test
    void httpRecordsToolbarLeadsWithRefreshAndUsesComfortableSpacing() {
        HttpRecordsTab tab = new HttpRecordsTab(montoyaApi());
        JButton refresh = find(tab, "httpRecordsRefreshButton", JButton.class);
        JTextField search = find(tab, "httpRecordsSearchField", JTextField.class);
        JPanel toolbar = find(tab, "httpRecordsToolbar", JPanel.class);
        JTable table = find(tab, "httpRecordsTable", JTable.class);

        assertNotNull(refresh);
        assertNotNull(search);
        assertSame(refresh.getParent(), search.getParent());
        assertTrue(indexOf(refresh.getParent(), refresh) < indexOf(search.getParent(), search));
        assertTrue(toolbar.getInsets().top >= 8);
        assertTrue(table.getRowHeight() >= 24);
        assertRefreshShortcut(tab, refresh);
    }

    @Test
    void nativeScanPaginationStartsWithResultSummary() {
        ScansTab tab = new ScansTab();
        JLabel summary = find(tab, "scansPageInfoLabel", JLabel.class);
        JButton previous = find(tab, "scansPrevButton", JButton.class);
        JPanel toolbar = find(tab, "scansToolbar", JPanel.class);

        assertNotNull(summary);
        assertNotNull(previous);
        assertSame(summary.getParent(), previous.getParent());
        assertTrue(indexOf(summary.getParent(), summary) < indexOf(previous.getParent(), previous));
        assertTrue(toolbar.getInsets().top >= 8);
        assertRefreshShortcut(tab, find(tab, "scansRefreshButton", JButton.class));
    }

    @Test
    void agenticScanToolbarLeadsWithRefreshBeforeFilters() {
        AgentSessionsTab tab = new AgentSessionsTab();
        JButton refresh = find(tab, "agentSessionsRefreshButton", JButton.class);
        JComboBox<?> mode = find(tab, "agentSessionsModeCombo", JComboBox.class);
        JPanel toolbar = find(tab, "agentSessionsToolbar", JPanel.class);

        assertNotNull(refresh);
        assertNotNull(mode);
        assertSame(refresh.getParent(), mode.getParent());
        assertTrue(indexOf(refresh.getParent(), refresh) < indexOf(mode.getParent(), mode));
        assertTrue(toolbar.getInsets().top >= 8);
        assertRefreshShortcut(tab, refresh);
    }

    private static MontoyaApi montoyaApi() {
        MontoyaApi api = mock(MontoyaApi.class);
        UserInterface userInterface = mock(UserInterface.class);
        HttpRequestEditor requestEditor = mock(HttpRequestEditor.class);
        HttpResponseEditor responseEditor = mock(HttpResponseEditor.class);
        when(api.userInterface()).thenReturn(userInterface);
        when(userInterface.createHttpRequestEditor(any(EditorOptions[].class))).thenReturn(requestEditor);
        when(userInterface.createHttpResponseEditor(any(EditorOptions[].class))).thenReturn(responseEditor);
        when(requestEditor.uiComponent()).thenReturn(new JPanel());
        when(responseEditor.uiComponent()).thenReturn(new JPanel());
        return api;
    }

    private static int indexOf(Container parent, Component target) {
        Component[] components = parent.getComponents();
        for (int i = 0; i < components.length; i++) {
            if (components[i] == target) return i;
        }
        return -1;
    }

    private static void assertRefreshShortcut(JComponent view, JButton refresh) {
        AtomicInteger clicks = new AtomicInteger();
        refresh.addActionListener(event -> clicks.incrementAndGet());
        Object actionKey =
                view.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(RefreshShortcut.KEYSTROKE);
        assertNotNull(actionKey);
        Action action = view.getActionMap().get(actionKey);
        assertNotNull(action);

        action.actionPerformed(new ActionEvent(view, ActionEvent.ACTION_PERFORMED, "refresh"));

        assertEquals(1, clicks.get());
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
