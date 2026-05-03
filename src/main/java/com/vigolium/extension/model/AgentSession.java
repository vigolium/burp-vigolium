package com.vigolium.extension.model;

import java.util.List;

public record AgentSession(
        String uuid,
        String mode,
        String status,
        String agentName,
        String templateId,
        String targetUrl,
        String inputType,
        String currentPhase,
        List<String> phasesRun,
        int findingCount,
        int recordCount,
        int savedCount,
        long durationMs,
        String startedAt,
        String completedAt,
        String createdAt) {

    public AgentSession {
        phasesRun = phasesRun == null ? List.of() : phasesRun;
    }

    public boolean isRunning() {
        return "running".equalsIgnoreCase(status);
    }
}
