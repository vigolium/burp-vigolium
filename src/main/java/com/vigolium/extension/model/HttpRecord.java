package com.vigolium.extension.model;

import java.nio.charset.StandardCharsets;

public record HttpRecord(
        String uuid,
        String scheme,
        String hostname,
        int port,
        String method,
        String path,
        String url,
        int statusCode,
        String statusPhrase,
        String responseHttpVersion,
        int responseContentLength,
        int responseTimeMs,
        String sentAt,
        String createdAt,
        String source,
        int riskScore,
        String rawRequest,
        String rawResponse) {

    /**
     * Returns the raw request as bytes. The record stores raw_request already base64-decoded into an ISO-8859-1 string
     * (1:1 byte↔char mapping) so round-tripping preserves binary content.
     */
    public byte[] rawRequestBytes() {
        return rawRequest == null ? new byte[0] : rawRequest.getBytes(StandardCharsets.ISO_8859_1);
    }

    public byte[] rawResponseBytes() {
        return rawResponse == null ? new byte[0] : rawResponse.getBytes(StandardCharsets.ISO_8859_1);
    }
}
