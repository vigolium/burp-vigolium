package com.vigolium.extension.service;

import java.util.List;

public record SiteMapSnapshotRequest(
        String snapshotId,
        int chunkIndex,
        boolean finalChunk,
        String capturedAt,
        List<SiteMapSnapshotRecord> records) {}
