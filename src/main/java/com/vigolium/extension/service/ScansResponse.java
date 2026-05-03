package com.vigolium.extension.service;

import com.vigolium.extension.model.Scan;
import java.util.List;

public record ScansResponse(List<Scan> data, int total, int limit, int offset, boolean hasMore) {}
