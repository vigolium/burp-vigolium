package com.vigolium.extension.service;

import com.vigolium.extension.model.Finding;
import java.util.List;

public record FindingsResponse(List<Finding> data, int total, int limit, int offset, boolean hasMore) {}
