package com.vigolium.extension.ui.tab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.internal.MontoyaObjectFactory;
import burp.api.montoya.internal.ObjectFactoryLocator;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import com.vigolium.extension.model.Finding;
import com.vigolium.extension.model.Severity;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FindingsEvidenceTabsTest {

    private MontoyaObjectFactory originalObjectFactory;

    @BeforeEach
    void setUp() {
        originalObjectFactory = ObjectFactoryLocator.FACTORY;
        MontoyaObjectFactory objectFactory = mock(MontoyaObjectFactory.class);
        when(objectFactory.httpRequest(anyString())).thenReturn(mock(HttpRequest.class));
        when(objectFactory.httpResponse(anyString())).thenReturn(mock(HttpResponse.class));
        ObjectFactoryLocator.FACTORY = objectFactory;
    }

    @AfterEach
    void tearDown() {
        ObjectFactoryLocator.FACTORY = originalObjectFactory;
    }

    @Test
    void rendersEachEvidenceAsAnImmediatelySelectableTab() {
        FindingsTab tab = new FindingsTab(montoyaApi());
        tab.showFinding(findingWithThreeAdditionalEvidences());
        JTabbedPane evidenceTabs = find(tab, "findingsEvidenceTabs", JTabbedPane.class);
        JSplitPane editors = find(tab, "findingsEditorSplitPane", JSplitPane.class);

        assertNotNull(evidenceTabs);
        assertNotNull(editors);
        assertEquals(4, evidenceTabs.getTabCount());
        assertEquals("Primary", evidenceTabs.getTitleAt(0));
        assertEquals("Evidence #1", evidenceTabs.getTitleAt(1));
        assertEquals("Evidence #3", evidenceTabs.getTitleAt(3));

        evidenceTabs.setSelectedIndex(2);

        assertSame(evidenceTabs.getComponentAt(2), editors.getParent());
    }

    @Test
    void clearingTheFindingRestoresASinglePrimaryTab() {
        FindingsTab tab = new FindingsTab(montoyaApi());
        tab.showFinding(findingWithThreeAdditionalEvidences());
        tab.showFinding(null);
        JTabbedPane evidenceTabs = find(tab, "findingsEvidenceTabs", JTabbedPane.class);

        assertNotNull(evidenceTabs);
        assertEquals(1, evidenceTabs.getTabCount());
        assertEquals("Primary", evidenceTabs.getTitleAt(0));
    }

    private static Finding findingWithThreeAdditionalEvidences() {
        return new Finding(
                1,
                List.of(),
                "scan-id",
                "idor",
                "IDOR",
                "Description",
                Severity.MEDIUM,
                "tentative",
                List.of(),
                List.of(),
                "2026-07-10",
                "",
                "",
                "active",
                "",
                "audit",
                "",
                "",
                List.of(),
                List.of("", "", ""),
                "hash",
                "2026-07-10");
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
