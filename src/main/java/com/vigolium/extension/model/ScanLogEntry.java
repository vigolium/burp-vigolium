package com.vigolium.extension.model;

public record ScanLogEntry(int id, String scanUuid, String level, String phase, String message, String createdAt) {}
