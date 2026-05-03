package com.vigolium.extension.ui.table;

import static org.junit.jupiter.api.Assertions.*;

import com.vigolium.extension.model.Finding;
import com.vigolium.extension.model.Severity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FindingsColumnDefsTest {

    private List<ColumnDef<Finding>> columns;
    private Finding finding;

    @BeforeEach
    void setUp() {
        columns = FindingsColumnDefs.create();
        finding = new Finding(
                1,
                List.of("abc-123"),
                "scan-456",
                "xss-scanner",
                "XSS Scanner",
                "Reflected XSS via parameter 'q'",
                Severity.HIGH,
                "firm",
                List.of("xss", "reflected"),
                List.of("https://example.com/search?q=test"),
                "2026-02-16T15:05:00Z",
                "",
                "");
    }

    @Test
    void columnCountIs7() {
        assertEquals(7, columns.size());
    }

    @Test
    void columnNamesMatchDesign() {
        assertEquals("#", columns.get(0).name());
        assertEquals("Severity", columns.get(1).name());
        assertEquals("Module", columns.get(2).name());
        assertEquals("Description", columns.get(3).name());
        assertEquals("Confidence", columns.get(4).name());
        assertEquals("Matched At", columns.get(5).name());
        assertEquals("Found At", columns.get(6).name());
    }

    @Test
    void columnTypesCorrect() {
        assertEquals(Integer.class, columns.get(0).type());
        assertEquals(Severity.class, columns.get(1).type());
        assertEquals(String.class, columns.get(2).type());
        assertEquals(String.class, columns.get(3).type());
        assertEquals(String.class, columns.get(4).type());
        assertEquals(String.class, columns.get(5).type());
        assertEquals(String.class, columns.get(6).type());
    }

    @Test
    void accessorsExtractCorrectValues() {
        assertNull(columns.get(0).accessor().extract(finding));
        assertEquals(Severity.HIGH, columns.get(1).accessor().extract(finding));
        assertEquals("XSS Scanner", columns.get(2).accessor().extract(finding));
        assertEquals(
                "Reflected XSS via parameter 'q'", columns.get(3).accessor().extract(finding));
        assertEquals("firm", columns.get(4).accessor().extract(finding));
        assertEquals(
                "https://example.com/search?q=test", columns.get(5).accessor().extract(finding));
        // Formatted from ISO "2026-02-16T15:05:00Z" to local "yyyy-MM-dd HH:mm"
        String formatted = (String) columns.get(6).accessor().extract(finding);
        assertTrue(formatted.startsWith("2026-02-16"), "Expected formatted date, got: " + formatted);
        assertFalse(formatted.contains("Z"), "Should not contain raw ISO suffix");
    }

    @Test
    void allColumnsExceptRowNumberAreSortable() {
        assertFalse(columns.get(0).sortable(), "# column should not be sortable");
        for (int i = 1; i < columns.size(); i++) {
            assertTrue(columns.get(i).sortable(), columns.get(i).name() + " should be sortable");
        }
    }

    @Test
    void noColumnsAreEditable() {
        for (ColumnDef<Finding> col : columns) {
            assertFalse(col.editable(), col.name() + " should not be editable");
        }
    }
}
