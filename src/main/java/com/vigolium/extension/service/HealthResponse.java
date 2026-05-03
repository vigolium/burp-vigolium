package com.vigolium.extension.service;

public record HealthResponse(String status, String version, long latencyMs) {}
