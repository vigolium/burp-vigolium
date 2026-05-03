package com.vigolium.extension.model;

public record Scan(
        String uuid,
        String name,
        String status,
        String scanSource,
        String scanMode,
        String sourceType,
        String modules,
        int totalFindings,
        int processedCount,
        String startedAt,
        String finishedAt,
        String createdAt) {

    public boolean isRunning() {
        return "running".equalsIgnoreCase(status) || "paused".equalsIgnoreCase(status);
    }

    public boolean isPaused() {
        return "paused".equalsIgnoreCase(status);
    }
}
