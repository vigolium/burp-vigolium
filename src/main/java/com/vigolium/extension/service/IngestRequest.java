package com.vigolium.extension.service;

import burp.api.montoya.http.message.HttpRequestResponse;
import java.util.Base64;

public record IngestRequest(String inputMode, String url, String httpRequestBase64, String httpResponseBase64) {

    private static final Base64.Encoder BASE64 = Base64.getEncoder();

    public static IngestRequest fromRequestResponse(HttpRequestResponse rr) {
        return new IngestRequest(
                "burp_base64",
                rr.request().url(),
                BASE64.encodeToString(rr.request().toByteArray().getBytes()),
                rr.hasResponse()
                        ? BASE64.encodeToString(rr.response().toByteArray().getBytes())
                        : null);
    }
}
