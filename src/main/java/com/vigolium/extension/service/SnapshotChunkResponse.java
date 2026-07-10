package com.vigolium.extension.service;

import java.util.List;

public record SnapshotChunkResponse(
        int received, int inserted, int updated, int unchanged, int skipped, List<String> errors) {}
