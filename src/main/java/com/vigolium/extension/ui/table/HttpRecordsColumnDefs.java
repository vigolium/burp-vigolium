package com.vigolium.extension.ui.table;

import com.vigolium.extension.model.HttpRecord;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class HttpRecordsColumnDefs {

    private HttpRecordsColumnDefs() {}

    public static List<ColumnDef<HttpRecord>> create() {
        List<ColumnDef<HttpRecord>> defs = new ArrayList<>();
        defs.add(ColumnDef.<HttpRecord>builder()
                .name("#")
                .type(Integer.class)
                .accessor(row -> null)
                .width(40)
                .sortable(false)
                .tooltip("Row number")
                .build());
        defs.add(ColumnDef.<HttpRecord>builder()
                .name("Method")
                .type(String.class)
                .accessor(HttpRecord::method)
                .width(70)
                .comparator(String.CASE_INSENSITIVE_ORDER)
                .tooltip("HTTP method")
                .build());
        defs.add(ColumnDef.<HttpRecord>builder()
                .name("Status")
                .type(Integer.class)
                .accessor(HttpRecord::statusCode)
                .width(65)
                .tooltip("HTTP response status code")
                .build());
        defs.add(ColumnDef.<HttpRecord>builder()
                .name("Host")
                .type(String.class)
                .accessor(HttpRecord::hostname)
                .preferredWidth(180)
                .comparator(String.CASE_INSENSITIVE_ORDER)
                .tooltip("Target hostname")
                .build());
        defs.add(ColumnDef.<HttpRecord>builder()
                .name("Path")
                .type(String.class)
                .accessor(HttpRecord::path)
                .preferredWidth(320)
                .comparator(String.CASE_INSENSITIVE_ORDER)
                .tooltip("Request path")
                .build());
        defs.add(ColumnDef.<HttpRecord>builder()
                .name("Length")
                .type(Integer.class)
                .accessor(HttpRecord::responseContentLength)
                .width(80)
                .tooltip("Response content length (bytes)")
                .build());
        defs.add(ColumnDef.<HttpRecord>builder()
                .name("Time (ms)")
                .type(Integer.class)
                .accessor(HttpRecord::responseTimeMs)
                .width(85)
                .tooltip("Response time in milliseconds")
                .build());
        defs.add(ColumnDef.<HttpRecord>builder()
                .name("Risk")
                .type(Integer.class)
                .accessor(HttpRecord::riskScore)
                .width(60)
                .tooltip("Risk score")
                .build());
        defs.add(ColumnDef.<HttpRecord>builder()
                .name("Source")
                .type(String.class)
                .accessor(HttpRecord::source)
                .width(110)
                .comparator(String.CASE_INSENSITIVE_ORDER)
                .tooltip("Ingestion source")
                .build());
        defs.add(ColumnDef.<HttpRecord>builder()
                .name("Sent At")
                .type(String.class)
                .accessor(r -> formatTimestamp(r.sentAt()))
                .preferredWidth(140)
                .tooltip("When the request was sent")
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
