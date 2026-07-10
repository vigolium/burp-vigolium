package com.vigolium.extension.service;

public record SiteMapSnapshotRecord(
        String url,
        String requestBase64,
        String responseBase64,
        String identityFingerprint,
        String contentFingerprint) {}
