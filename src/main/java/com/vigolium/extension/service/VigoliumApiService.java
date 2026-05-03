package com.vigolium.extension.service;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vigolium.extension.model.AgentSession;
import com.vigolium.extension.model.Finding;
import com.vigolium.extension.model.HttpRecord;
import com.vigolium.extension.model.Scan;
import com.vigolium.extension.model.ScanLogEntry;
import com.vigolium.extension.model.Severity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class VigoliumApiService {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_RETRIES = 2;
    private static final long[] BACKOFF_MS = {1000, 2000};

    private final Supplier<String> serverUrlSupplier;
    private final Supplier<String> apiKeySupplier;
    private final OkHttpClient client;
    private final Gson gson;

    public VigoliumApiService(Supplier<String> serverUrlSupplier, Supplier<String> apiKeySupplier) {
        this(
                serverUrlSupplier,
                apiKeySupplier,
                new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(10, TimeUnit.SECONDS)
                        .build());
    }

    public VigoliumApiService(
            Supplier<String> serverUrlSupplier, Supplier<String> apiKeySupplier, OkHttpClient client) {
        this.serverUrlSupplier = serverUrlSupplier;
        this.apiKeySupplier = apiKeySupplier;
        this.client = client;
        this.gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
    }

    public HealthResponse health() {
        long start = System.currentTimeMillis();
        Request request = newRequestBuilder("/health").get().build();
        String body = executeWithRetry(request);
        long latencyMs = System.currentTimeMillis() - start;

        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        String status = json.has("status") ? json.get("status").getAsString() : "unknown";
        String version = json.has("version") ? json.get("version").getAsString() : "unknown";

        return new HealthResponse(status, version, latencyMs);
    }

    public void ingest(IngestRequest ingestRequest) {
        String json = gson.toJson(ingestRequest);
        Request request = newRequestBuilder("/api/ingest-http")
                .post(RequestBody.create(json, JSON))
                .build();
        executeWithRetry(request);
    }

    public ScanResponse scan(ScanRequest scanRequest) {
        String json = gson.toJson(scanRequest);
        Request request = newRequestBuilder("/api/scan-request")
                .post(RequestBody.create(json, JSON))
                .build();
        String body = executeWithRetry(request);
        return gson.fromJson(body, ScanResponse.class);
    }

    public String agentScan(AgentScanRequest agentScanRequest) {
        String json = gson.toJson(agentScanRequest);
        Request request = newRequestBuilder("/api/agent/run/swarm")
                .post(RequestBody.create(json, JSON))
                .build();
        return executeWithRetry(request);
    }

    // -----------------------------------------------------------------
    // Findings
    // -----------------------------------------------------------------

    public Finding findingById(int id) {
        Request request = newRequestBuilder("/api/findings/" + id).get().build();
        String body = executeWithRetry(request);
        JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
        return parseFinding(obj);
    }

    public void deleteFinding(int id) {
        Request request = newRequestBuilder("/api/findings/" + id).delete().build();
        executeWithRetry(request);
    }

    public FindingsResponse findings(FindingsQuery query) {
        HttpUrl baseUrl = parseUrl("/api/findings");
        HttpUrl.Builder urlBuilder = baseUrl.newBuilder()
                .addQueryParameter("limit", String.valueOf(query.getLimit()))
                .addQueryParameter("offset", String.valueOf(query.getOffset()));

        addQueryIfPresent(urlBuilder, "domain", query.getDomain());
        addQueryIfPresent(urlBuilder, "severity", query.getSeverity());
        addQueryIfPresent(urlBuilder, "module_name", query.getModuleName());
        addQueryIfPresent(urlBuilder, "module_type", query.getModuleType());
        addQueryIfPresent(urlBuilder, "finding_source", query.getFindingSource());
        addQueryIfPresent(urlBuilder, "scan_id", query.getScanId());
        addQueryIfPresent(urlBuilder, "repo_name", query.getRepoName());
        addQueryIfPresent(urlBuilder, "search", query.getSearch());
        addQueryIfPresent(urlBuilder, "sort", query.getSort());
        addQueryIfPresent(urlBuilder, "order", query.getOrder());

        Request request =
                bearer(new Request.Builder().url(urlBuilder.build())).get().build();
        String body = executeWithRetry(request);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();

        List<Finding> findings = parseFindingsArray(json.getAsJsonArray("data"));
        int total = getInt(json, "total", 0);
        int limit = getInt(json, "limit", query.getLimit());
        int offset = getInt(json, "offset", query.getOffset());
        boolean hasMore = getBool(json, "has_more", false);

        return new FindingsResponse(findings, total, limit, offset, hasMore);
    }

    private List<Finding> parseFindingsArray(JsonArray array) {
        List<Finding> findings = new ArrayList<>();
        if (array == null) return findings;
        for (JsonElement element : array) {
            findings.add(parseFinding(element.getAsJsonObject()));
        }
        return findings;
    }

    private Finding parseFinding(JsonObject obj) {
        return new Finding(
                getInt(obj, "id", 0),
                getStringList(obj, "http_record_uuids"),
                getStr(obj, "scan_uuid"),
                getStr(obj, "module_id"),
                getStr(obj, "module_name"),
                getStr(obj, "description"),
                Severity.fromString(getStr(obj, "severity")),
                getStr(obj, "confidence"),
                getStringList(obj, "tags"),
                getStringList(obj, "matched_at"),
                getStr(obj, "found_at"),
                getStr(obj, "request"),
                getStr(obj, "response"),
                getStr(obj, "module_type"),
                getStr(obj, "module_short"),
                getStr(obj, "finding_source"),
                getStr(obj, "source_file"),
                getStr(obj, "repo_name"),
                getStringList(obj, "extracted_results"),
                getStringList(obj, "additional_evidence"),
                getStr(obj, "finding_hash"),
                getStr(obj, "created_at"));
    }

    // -----------------------------------------------------------------
    // HTTP Records
    // -----------------------------------------------------------------

    public HttpRecordsResponse httpRecords(HttpRecordsQuery query) {
        HttpUrl baseUrl = parseUrl("/api/http-records");
        HttpUrl.Builder urlBuilder = baseUrl.newBuilder()
                .addQueryParameter("limit", String.valueOf(query.getLimit()))
                .addQueryParameter("offset", String.valueOf(query.getOffset()));

        addQueryIfPresent(urlBuilder, "domain", query.getDomain());
        addQueryIfPresent(urlBuilder, "method", query.getMethod());
        addQueryIfPresent(urlBuilder, "path", query.getPath());
        addQueryIfPresent(urlBuilder, "status_code", query.getStatusCode());
        addQueryIfPresent(urlBuilder, "content_type", query.getContentType());
        addQueryIfPresent(urlBuilder, "search", query.getSearch());
        addQueryIfPresent(urlBuilder, "source", query.getSource());
        if (query.getMinRisk() != null) {
            urlBuilder.addQueryParameter("min_risk", String.valueOf(query.getMinRisk()));
        }
        addQueryIfPresent(urlBuilder, "remark", query.getRemark());
        addQueryIfPresent(urlBuilder, "sort", query.getSort());
        addQueryIfPresent(urlBuilder, "order", query.getOrder());

        Request request =
                bearer(new Request.Builder().url(urlBuilder.build())).get().build();
        String body = executeWithRetry(request);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();

        List<HttpRecord> records = new ArrayList<>();
        JsonArray data = json.getAsJsonArray("data");
        if (data != null) {
            for (JsonElement el : data) {
                records.add(parseHttpRecord(el.getAsJsonObject()));
            }
        }
        int total = getInt(json, "total", 0);
        int limit = getInt(json, "limit", query.getLimit());
        int offset = getInt(json, "offset", query.getOffset());
        boolean hasMore = getBool(json, "has_more", false);
        return new HttpRecordsResponse(records, total, limit, offset, hasMore);
    }

    /**
     * Triggers /api/scan-all-records to scan every HTTP record in the current project. Returns the scan_uuid of the
     * new scan, or throws with the server error message (e.g. 409 when a scan is already running for that project).
     * {@code modules} and {@code timeout} are optional — null/blank uses server defaults (all modules).
     */
    public String scanAllRecords(List<String> modules, String timeout) {
        JsonObject body = new JsonObject();
        if (modules != null && !modules.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (String m : modules) arr.add(m);
            body.add("modules", arr);
        }
        if (timeout != null && !timeout.isBlank()) {
            body.addProperty("timeout", timeout);
        }

        Request request = newRequestBuilder("/api/scan-all-records")
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        String resp = executeWithRetry(request);
        JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
        // Server returns "scan_uuid"; tolerate "scan_id" as a fallback.
        String uuid = getStr(json, "scan_uuid");
        return uuid.isEmpty() ? getStr(json, "scan_id") : uuid;
    }

    /**
     * Triggers /api/scan-records for a specific list of UUIDs. Returns the scan_id of the new scan, or throws with the
     * server error message (e.g. 409 when a scan is already running). {@code enableModules} is optional — when empty
     * or null, the server runs all modules.
     */
    public String scanRecords(List<String> recordUuids, List<String> enableModules) {
        JsonObject body = new JsonObject();
        JsonArray arr = new JsonArray();
        for (String uuid : recordUuids) arr.add(uuid);
        body.add("record_uuids", arr);

        if (enableModules != null && !enableModules.isEmpty()) {
            JsonArray modules = new JsonArray();
            for (String m : enableModules) modules.add(m);
            body.add("enable_modules", modules);
        }

        Request request = newRequestBuilder("/api/scan-records")
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        String resp = executeWithRetry(request);
        JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
        return getStr(json, "scan_id");
    }

    public HttpRecord httpRecordByUuid(String uuid) {
        Request request = newRequestBuilder("/api/http-records/" + uuid).get().build();
        String body = executeWithRetry(request);
        return parseHttpRecord(JsonParser.parseString(body).getAsJsonObject());
    }

    public void deleteHttpRecord(String uuid) {
        Request request =
                newRequestBuilder("/api/http-records/" + uuid).delete().build();
        executeWithRetry(request);
    }

    private HttpRecord parseHttpRecord(JsonObject obj) {
        return new HttpRecord(
                getStr(obj, "uuid"),
                getStr(obj, "scheme"),
                getStr(obj, "hostname"),
                getInt(obj, "port", 0),
                getStr(obj, "method"),
                getStr(obj, "path"),
                getStr(obj, "url"),
                getInt(obj, "status_code", 0),
                getStr(obj, "status_phrase"),
                getStr(obj, "response_http_version"),
                getInt(obj, "response_content_length", 0),
                getInt(obj, "response_time_ms", 0),
                getStr(obj, "sent_at"),
                getStr(obj, "created_at"),
                getStr(obj, "source"),
                getInt(obj, "risk_score", 0),
                maybeDecodeBase64(getStr(obj, "raw_request")),
                maybeDecodeBase64(getStr(obj, "raw_response")));
    }

    /**
     * Raw HTTP blobs from the backend are base64-encoded. Decode to an ISO-8859-1 string (1:1 byte↔char) so binary
     * payloads survive the String round-trip. Falls back to the original value if decoding fails.
     */
    private static String maybeDecodeBase64(String s) {
        if (s == null || s.isEmpty()) return s;
        try {
            byte[] decoded = Base64.getDecoder().decode(s);
            return new String(decoded, StandardCharsets.ISO_8859_1);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    // -----------------------------------------------------------------
    // Scans
    // -----------------------------------------------------------------

    public ScansResponse scans(int limit, int offset) {
        HttpUrl baseUrl = parseUrl("/api/scans");
        HttpUrl.Builder urlBuilder = baseUrl.newBuilder()
                .addQueryParameter("limit", String.valueOf(limit))
                .addQueryParameter("offset", String.valueOf(offset));

        Request request =
                bearer(new Request.Builder().url(urlBuilder.build())).get().build();
        String body = executeWithRetry(request);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();

        List<Scan> scans = new ArrayList<>();
        JsonArray data = json.getAsJsonArray("data");
        if (data != null) {
            for (JsonElement el : data) {
                scans.add(parseScan(el.getAsJsonObject()));
            }
        }
        int total = getInt(json, "total", 0);
        boolean hasMore = getBool(json, "has_more", false);
        return new ScansResponse(scans, total, limit, offset, hasMore);
    }

    public void stopScan(String uuid) {
        Request request = newRequestBuilder("/api/scans/" + uuid + "/stop")
                .post(RequestBody.create("", JSON))
                .build();
        executeWithRetry(request);
    }

    public void pauseScan(String uuid) {
        Request request = newRequestBuilder("/api/scans/" + uuid + "/pause")
                .post(RequestBody.create("", JSON))
                .build();
        executeWithRetry(request);
    }

    public void resumeScan(String uuid) {
        Request request = newRequestBuilder("/api/scans/" + uuid + "/resume")
                .post(RequestBody.create("", JSON))
                .build();
        executeWithRetry(request);
    }

    public void deleteScan(String uuid) {
        Request request = newRequestBuilder("/api/scans/" + uuid).delete().build();
        executeWithRetry(request);
    }

    public ScanLogsResponse scanLogs(String scanUuid, String level, String phase, int limit, int offset) {
        HttpUrl baseUrl = parseUrl("/api/scans/" + scanUuid + "/logs");
        HttpUrl.Builder urlBuilder = baseUrl.newBuilder()
                .addQueryParameter("limit", String.valueOf(limit))
                .addQueryParameter("offset", String.valueOf(offset));
        addQueryIfPresent(urlBuilder, "level", level);
        addQueryIfPresent(urlBuilder, "phase", phase);

        Request request =
                bearer(new Request.Builder().url(urlBuilder.build())).get().build();
        String body = executeWithRetry(request);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();

        List<ScanLogEntry> logs = new ArrayList<>();
        JsonArray arr = json.getAsJsonArray("logs");
        if (arr != null) {
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                logs.add(new ScanLogEntry(
                        getInt(o, "id", 0),
                        getStr(o, "scan_uuid"),
                        getStr(o, "level"),
                        getStr(o, "phase"),
                        getStr(o, "message"),
                        getStr(o, "created_at")));
            }
        }
        int total = getInt(json, "total", logs.size());
        return new ScanLogsResponse(logs, total);
    }

    private Scan parseScan(JsonObject obj) {
        return new Scan(
                getStr(obj, "uuid"),
                getStr(obj, "name"),
                getStr(obj, "status"),
                getStr(obj, "scan_source"),
                getStr(obj, "scan_mode"),
                getStr(obj, "source_type"),
                getStr(obj, "modules"),
                getInt(obj, "total_findings", 0),
                getInt(obj, "processed_count", 0),
                getStr(obj, "started_at"),
                getStr(obj, "finished_at"),
                getStr(obj, "created_at"));
    }

    // -----------------------------------------------------------------
    // Agent Sessions
    // -----------------------------------------------------------------

    public AgentSessionsResponse agentSessions(String mode, int limit, int offset) {
        HttpUrl baseUrl = parseUrl("/api/agent/sessions");
        HttpUrl.Builder urlBuilder = baseUrl.newBuilder()
                .addQueryParameter("limit", String.valueOf(limit))
                .addQueryParameter("offset", String.valueOf(offset));
        addQueryIfPresent(urlBuilder, "mode", mode);

        Request request =
                bearer(new Request.Builder().url(urlBuilder.build())).get().build();
        String body = executeWithRetry(request);
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();

        List<AgentSession> sessions = new ArrayList<>();
        JsonArray data = json.getAsJsonArray("data");
        if (data != null) {
            for (JsonElement el : data) {
                sessions.add(parseAgentSession(el.getAsJsonObject()));
            }
        }
        int total = getInt(json, "total", 0);
        boolean hasMore = getBool(json, "has_more", false);
        return new AgentSessionsResponse(sessions, total, limit, offset, hasMore);
    }

    public String agentSessionLogs(String sessionId) {
        HttpUrl baseUrl = parseUrl("/api/agent/sessions/" + sessionId + "/logs");
        HttpUrl url = baseUrl.newBuilder().addQueryParameter("strip", "1").build();
        Request request = bearer(new Request.Builder().url(url)).get().build();
        return executeWithRetry(request);
    }

    private AgentSession parseAgentSession(JsonObject obj) {
        List<String> phasesRun = new ArrayList<>();
        if (obj.has("phases_run") && obj.get("phases_run").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("phases_run")) {
                phasesRun.add(el.getAsString());
            }
        }
        return new AgentSession(
                getStr(obj, "uuid"),
                getStr(obj, "mode"),
                getStr(obj, "status"),
                getStr(obj, "agent_name"),
                getStr(obj, "template_id"),
                getStr(obj, "target_url"),
                getStr(obj, "input_type"),
                getStr(obj, "current_phase"),
                phasesRun,
                getInt(obj, "finding_count", 0),
                getInt(obj, "record_count", 0),
                getInt(obj, "saved_count", 0),
                getLong(obj, "duration_ms", 0),
                getStr(obj, "started_at"),
                getStr(obj, "completed_at"),
                getStr(obj, "created_at"));
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    public boolean isConfigured() {
        String url = serverUrlSupplier.get();
        return url != null && !url.isBlank();
    }

    private HttpUrl parseUrl(String path) {
        HttpUrl base = HttpUrl.parse(resolveServerUrl() + path);
        if (base == null) {
            throw new VigoliumApiException(0, "Invalid server URL: " + serverUrlSupplier.get());
        }
        return base;
    }

    private String resolveServerUrl() {
        String url = serverUrlSupplier.get();
        if (url != null && url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private Request.Builder newRequestBuilder(String path) {
        return bearer(new Request.Builder().url(resolveServerUrl() + path));
    }

    private Request.Builder bearer(Request.Builder builder) {
        String key = apiKeySupplier.get();
        if (key != null && !key.isBlank()) {
            builder.header("Authorization", "Bearer " + key);
        }
        return builder;
    }

    private static void addQueryIfPresent(HttpUrl.Builder b, String key, String value) {
        if (value != null && !value.isBlank()) {
            b.addQueryParameter(key, value);
        }
    }

    private static String getStr(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : "";
    }

    private static int getInt(JsonObject obj, String key, int def) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsInt() : def;
    }

    private static long getLong(JsonObject obj, String key, long def) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsLong() : def;
    }

    private static boolean getBool(JsonObject obj, String key, boolean def) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsBoolean() : def;
    }

    private static List<String> getStringList(JsonObject obj, String key) {
        List<String> result = new ArrayList<>();
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray(key)) {
                if (!el.isJsonNull()) {
                    result.add(el.getAsString());
                }
            }
        }
        return result;
    }

    private String executeWithRetry(Request request) {
        IOException lastException = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(BACKOFF_MS[attempt - 1]);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new VigoliumApiException(0, "Request interrupted");
                }
            }

            try (Response response = client.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";

                if (response.isSuccessful()) {
                    return body;
                }

                if (response.code() >= 400 && response.code() < 500) {
                    throw new VigoliumApiException(response.code(), "API error: " + response.code() + " - " + body);
                }

                lastException = new IOException("Server error: " + response.code() + " - " + body);
            } catch (VigoliumApiException e) {
                throw e;
            } catch (IOException e) {
                lastException = e;
            }
        }

        throw new VigoliumApiException(
                0,
                "Request failed after " + MAX_RETRIES + " retries: "
                        + (lastException != null ? lastException.getMessage() : "unknown error"));
    }
}
