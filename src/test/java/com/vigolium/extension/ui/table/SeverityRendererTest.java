package com.vigolium.extension.ui.table;

import static org.junit.jupiter.api.Assertions.*;

import com.vigolium.extension.model.Severity;
import java.awt.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SeverityRendererTest {

    private SeverityRenderer renderer;
    private JTable table;

    @BeforeEach
    void setUp() {
        renderer = new SeverityRenderer();
        table = new JTable(new DefaultTableModel(new Object[][] {{}}, new Object[] {"Severity"}));
    }

    @ParameterizedTest
    @EnumSource(Severity.class)
    void rendersEachSeverityWithCorrectColor(Severity severity) {
        Component comp = renderer.getTableCellRendererComponent(table, severity, false, false, 0, 0);

        assertTrue(comp instanceof JLabel);
        JLabel label = (JLabel) comp;
        assertEquals(severity.label(), label.getText());
        assertEquals(severity.color(), label.getForeground());
        assertEquals(Font.BOLD, label.getFont().getStyle());
    }

    @ParameterizedTest
    @EnumSource(Severity.class)
    void selectedRowDoesNotOverrideForeground(Severity severity) {
        Component comp = renderer.getTableCellRendererComponent(table, severity, true, false, 0, 0);

        JLabel label = (JLabel) comp;
        assertEquals(severity.label(), label.getText());
        // When selected, foreground should NOT be set to severity color
        // (it uses table selection foreground instead)
        assertNotEquals(severity.color(), label.getForeground());
    }

    @Test
    void htmlDisabledOnRenderer() {
        assertEquals(Boolean.TRUE, renderer.getClientProperty("html.disable"));
    }

    @Test
    void htmlDisabledOnToolTip() {
        JToolTip tip = renderer.createToolTip();
        assertEquals(Boolean.TRUE, tip.getClientProperty("html.disable"));
    }
}
