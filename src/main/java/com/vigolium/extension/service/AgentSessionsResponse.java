package com.vigolium.extension.service;

import com.vigolium.extension.model.AgentSession;
import java.util.List;

public record AgentSessionsResponse(List<AgentSession> data, int total, int limit, int offset, boolean hasMore) {}
