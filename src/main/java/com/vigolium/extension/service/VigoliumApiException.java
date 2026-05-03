package com.vigolium.extension.service;

public class VigoliumApiException extends RuntimeException {

    private final int statusCode;

    public VigoliumApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
