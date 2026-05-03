package com.vigolium.extension.ui.table;

import com.vigolium.extension.model.Scan;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class ScansColumnDefs {

    private ScansColumnDefs() {}

    public static List<ColumnDef<Scan>> create() {
        List<ColumnDef<Scan>> defs = new ArrayList<>();
        defs.add(ColumnDef.<Scan>builder()
                .name("#")
                .type(Integer.class)
                .accessor(row -> null)
                .width(40)
                .sortable(false)
                .tooltip("Row number")
                .build());
        defs.add(ColumnDef.<Scan>builder()
                .name("Status")
                .type(String.class)
                .accessor(Scan::status)
                .width(100)
                .tooltip("Scan status")
                .build());
        defs.add(ColumnDef.<Scan>builder()
                .name("Mode")
                .type(String.class)
                .accessor(Scan::scanMode)
                .width(90)
                .tooltip("Scan mode (full/target/incremental/single/selective/sast)")
                .build());
        defs.add(ColumnDef.<Scan>builder()
                .name("Name / UUID")
                .type(String.class)
                .accessor(s -> (s.name() == null || s.name().isEmpty()) ? s.uuid() : s.name())
                .preferredWidth(220)
                .tooltip("Scan name or UUID if no name")
                .build());
        defs.add(ColumnDef.<Scan>builder()
                .name("Findings")
                .type(Integer.class)
                .accessor(Scan::totalFindings)
                .width(80)
                .tooltip("Total findings produced")
                .build());
        defs.add(ColumnDef.<Scan>builder()
                .name("Processed")
                .type(Integer.class)
                .accessor(Scan::processedCount)
                .width(90)
                .tooltip("Records processed")
                .build());
        defs.add(ColumnDef.<Scan>builder()
                .name("Source")
                .type(String.class)
                .accessor(Scan::sourceType)
                .width(90)
                .tooltip("Source type (local/git-url/gcs)")
                .build());
        defs.add(ColumnDef.<Scan>builder()
                .name("Started")
                .type(String.class)
                .accessor(s -> formatTimestamp(s.startedAt()))
                .preferredWidth(140)
                .tooltip("Scan start time")
                .build());
        defs.add(ColumnDef.<Scan>builder()
                .name("Finished")
                .type(String.class)
                .accessor(s -> formatTimestamp(s.finishedAt()))
                .preferredWidth(140)
                .tooltip("Scan finish time")
                .build());
        return defs;
    }

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static String formatTimestamp(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            return TIMESTAMP_FMT.format(Instant.parse(iso));
        } catch (Exception e) {
            return iso;
        }
    }
}
