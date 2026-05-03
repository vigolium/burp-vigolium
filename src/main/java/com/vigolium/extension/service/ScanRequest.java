package com.vigolium.extension.service;

/**
 * Body for POST /api/scan-request. {@code httpResponseBase64}, {@code modules} and {@code timeout} are optional —
 * Gson will omit null fields when serializing. {@code modules} is a comma-separated list (use {@code "all"} to run
 * every module); {@code timeout} is a Go-duration string.
 */
public record ScanRequest(
        String httpRequestBase64, String httpResponseBase64, String targetUrl, String modules, String timeout) {

    public ScanRequest(String httpRequestBase64, String targetUrl) {
        this(httpRequestBase64, null, targetUrl, null, null);
    }
}
