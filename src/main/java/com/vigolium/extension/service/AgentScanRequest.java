package com.vigolium.extension.service;

/**
 * Body for POST /api/agent/run/swarm. {@code httpResponseBase64} is optional — Gson omits null fields when serializing.
 */
public record AgentScanRequest(
        String httpRequestBase64, String httpResponseBase64, String url, boolean triage, String intensity) {}
