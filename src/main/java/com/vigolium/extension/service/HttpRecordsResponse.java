package com.vigolium.extension.service;

import com.vigolium.extension.model.HttpRecord;
import java.util.List;

public record HttpRecordsResponse(List<HttpRecord> data, int total, int limit, int offset, boolean hasMore) {}
