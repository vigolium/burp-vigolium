package com.vigolium.extension.ui.table;

import com.vigolium.extension.model.Finding;
import com.vigolium.extension.model.Severity;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class FindingsColumnDefs {

    private FindingsColumnDefs() {}

    public static List<ColumnDef<Finding>> create() {
        List<ColumnDef<Finding>> defs = new ArrayList<>();
        defs.add(ColumnDef.<Finding>builder()
                .name("#")
                .type(Integer.class)
                .accessor(row -> null)
                .width(40)
                .sortable(false)
                .tooltip("Row number")
                .build());
        defs.add(ColumnDef.<Finding>builder()
                .name("Severity")
                .type(Severity.class)
                .accessor(Finding::severity)
                .width(80)
                .comparator(Severity.BY_ORDINAL)
                .tooltip("Finding severity level")
                .build());
        defs.add(ColumnDef.<Finding>builder()
                .name("Module")
                .type(String.class)
                .accessor(Finding::moduleName)
                .preferredWidth(120)
                .comparator(String.CASE_INSENSITIVE_ORDER)
                .tooltip("Scan module that found the issue")
                .build());
        defs.add(ColumnDef.<Finding>builder()
                .name("Description")
                .type(String.class)
                .accessor(f -> compactDescription(f.description()))
                .preferredWidth(350)
                .tooltip("Finding description")
                .build());
        defs.add(ColumnDef.<Finding>builder()
                .name("Confidence")
                .type(String.class)
                .accessor(Finding::confidence)
                .width(80)
                .comparator(String.CASE_INSENSITIVE_ORDER)
                .tooltip("Confidence level")
                .build());
        defs.add(ColumnDef.<Finding>builder()
                .name("Matched At")
                .type(String.class)
                .accessor(f -> f.matchedAt() != null && !f.matchedAt().isEmpty()
                        ? f.matchedAt().get(0)
                        : "")
                .preferredWidth(250)
                .tooltip("URL where finding was matched")
                .build());
        defs.add(ColumnDef.<Finding>builder()
                .name("Found At")
                .type(String.class)
                .accessor(f -> formatTimestamp(f.foundAt()))
                .preferredWidth(120)
                .tooltip("When the finding was discovered")
                .build());
        return defs;
    }

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    /**
     * Collapses whitespace (newlines/tabs) and strips leading markdown heading markers so the description renders as a
     * single compact line in the table cell. Full text is still shown in the detail pane below.
     */
    private static String compactDescription(String s) {
        if (s == null || s.isEmpty()) return "";
        String trimmed = s.replaceAll("\\s+", " ").trim();
        // Drop leading "# "/"## "/"### " markdown heading markers
        return trimmed.replaceFirst("^#{1,6}\\s+", "");
    }

    private static String formatTimestamp(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            return TIMESTAMP_FMT.format(Instant.parse(iso));
        } catch (Exception e) {
            return iso;
        }
    }
}
