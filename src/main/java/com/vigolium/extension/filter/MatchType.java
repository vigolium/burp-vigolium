package com.vigolium.extension.filter;

public enum MatchType {
    FILE_EXTENSION("File extension"),
    HTTP_METHOD("HTTP method"),
    URL("URL"),
    CONTENT_TYPE("Content-Type"),
    STATUS_CODE("Status code"),
    HOST("Host"),
    REQUEST("Request");

    private final String label;

    MatchType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
