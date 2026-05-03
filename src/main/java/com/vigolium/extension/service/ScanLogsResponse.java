package com.vigolium.extension.service;

import com.vigolium.extension.model.ScanLogEntry;
import java.util.List;

public record ScanLogsResponse(List<ScanLogEntry> logs, int total) {}
