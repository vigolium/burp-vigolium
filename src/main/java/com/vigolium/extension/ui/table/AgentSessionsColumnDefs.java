package com.vigolium.extension.ui.table;

import com.vigolium.extension.model.AgentSession;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class AgentSessionsColumnDefs {

    private AgentSessionsColumnDefs() {}

    public static List<ColumnDef<AgentSession>> create() {
        List<ColumnDef<AgentSession>> defs = new ArrayList<>();
        defs.add(ColumnDef.<AgentSession>builder()
                .name("#")
                .type(Integer.class)
                .accessor(row -> null)
                .width(40)
                .sortable(false)
                .tooltip("Row number")
                .build());
        defs.add(ColumnDef.<AgentSession>builder()
                .name("Mode")
                .type(String.class)
                .accessor(AgentSession::mode)
                .width(90)
                .tooltip("Agent mode (query/autopilot/swarm)")
                .build());
        defs.add(ColumnDef.<AgentSession>builder()
                .name("Status")
                .type(String.class)
                .accessor(AgentSession::status)
                .width(100)
                .tooltip("Session status")
                .build());
        defs.add(ColumnDef.<AgentSession>builder()
                .name("Agent")
                .type(String.class)
                .accessor(AgentSession::agentName)
                .width(100)
                .tooltip("Agent backend")
                .build());
        defs.add(ColumnDef.<AgentSession>builder()
                .name("Target")
                .type(String.class)
                .accessor(AgentSession::targetUrl)
                .preferredWidth(240)
                .tooltip("Target URL")
                .build());
        defs.add(ColumnDef.<AgentSession>builder()
                .name("Phase")
                .type(String.class)
                .accessor(AgentSession::currentPhase)
                .preferredWidth(130)
                .tooltip("Current or last phase (swarm only)")
                .build());
        defs.add(ColumnDef.<AgentSession>builder()
                .name("Findings")
                .type(Integer.class)
                .accessor(AgentSession::findingCount)
                .width(80)
                .tooltip("Findings produced")
                .build());
        defs.add(ColumnDef.<AgentSession>builder()
                .name("Duration")
                .type(String.class)
                .accessor(s -> formatDuration(s.durationMs()))
                .width(90)
                .tooltip("Session duration")
                .build());
        defs.add(ColumnDef.<AgentSession>builder()
                .name("Started")
                .type(String.class)
                .accessor(s -> formatTimestamp(s.startedAt()))
                .preferredWidth(140)
                .tooltip("Session start time")
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

    private static String formatDuration(long ms) {
        if (ms <= 0) return "";
        long seconds = ms / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        long remSec = seconds % 60;
        if (minutes < 60) return minutes + "m " + remSec + "s";
        long hours = minutes / 60;
        long remMin = minutes % 60;
        return hours + "h " + remMin + "m";
    }
}
