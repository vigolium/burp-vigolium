package com.vigolium.extension.model;

import java.util.List;

public record Finding(
        int id,
        List<String> httpRecordUuids,
        String scanUuid,
        String moduleId,
        String moduleName,
        String description,
        Severity severity,
        String confidence,
        List<String> tags,
        List<String> matchedAt,
        String foundAt,
        String request,
        String response,
        String moduleType,
        String moduleShort,
        String findingSource,
        String sourceFile,
        String repoName,
        List<String> extractedResults,
        List<String> additionalEvidence,
        String findingHash,
        String createdAt) {

    public Finding {
        httpRecordUuids = httpRecordUuids == null ? List.of() : httpRecordUuids;
        tags = tags == null ? List.of() : tags;
        matchedAt = matchedAt == null ? List.of() : matchedAt;
        extractedResults = extractedResults == null ? List.of() : extractedResults;
        additionalEvidence = additionalEvidence == null ? List.of() : additionalEvidence;
    }

    public Finding(
            int id,
            List<String> httpRecordUuids,
            String scanUuid,
            String moduleId,
            String moduleName,
            String description,
            Severity severity,
            String confidence,
            List<String> tags,
            List<String> matchedAt,
            String foundAt,
            String request,
            String response) {
        this(
                id,
                httpRecordUuids,
                scanUuid,
                moduleId,
                moduleName,
                description,
                severity,
                confidence,
                tags,
                matchedAt,
                foundAt,
                request,
                response,
                "",
                "",
                "",
                "",
                "",
                List.of(),
                List.of(),
                "",
                "");
    }

    public record Evidence(String request, String response) {}

    /**
     * Parse additional_evidence entries. Each entry is a raw request/response pair separated by the
     * literal delimiter line "---------" (preceded/followed by a newline).
     */
    public static Evidence parseEvidence(String raw) {
        if (raw == null || raw.isEmpty()) return new Evidence("", "");
        String[] parts = raw.split("\\n-{9}\\n", 2);
        String request = parts.length > 0 ? parts[0] : "";
        String response = parts.length > 1 ? parts[1] : "";
        return new Evidence(request, response);
    }
}
