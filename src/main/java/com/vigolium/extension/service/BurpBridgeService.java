package com.vigolium.extension.service;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Annotations;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vigolium.extension.config.BridgeSettings;
import fi.iki.elonen.NanoHTTPD;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** Loopback-only HTTP bridge for exchanging traffic with Vigolium. */
public class BurpBridgeService {

    private static final int MAX_REFERENCES = 10_000;
    private static final int MAX_REQUEST_BYTES = 64 * 1024;
    private static final int MAX_INSPECT_BYTES = 4 * 1024 * 1024;
    private static final int MAX_SITE_MAP_MESSAGE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_WRITE_BODY_BYTES = 24 * 1024 * 1024;

    private final MontoyaApi api;
    private final BridgeSettings settings;
    private final LogService logService;
    private final SiteMapItemFactory siteMapItemFactory;
    private final Map<String, BridgeItem> references = new LinkedHashMap<>();

    private volatile Consumer<BridgeStatus> statusListener;
    private volatile BridgeStatus status = BridgeStatus.disabled();
    private volatile BridgeHttpServer server;
    private volatile BridgeBinding bridgeBinding;

    public BurpBridgeService(MontoyaApi api, BridgeSettings settings, LogService logService) {
        this(api, settings, logService, BurpBridgeService::createSiteMapItem);
    }

    BurpBridgeService(
            MontoyaApi api, BridgeSettings settings, LogService logService, SiteMapItemFactory siteMapItemFactory) {
        this.api = api;
        this.settings = settings;
        this.logService = logService;
        this.siteMapItemFactory = siteMapItemFactory;
    }

    public void setStatusListener(Consumer<BridgeStatus> listener) {
        this.statusListener = listener;
        publish(status);
    }

    public void start() {
        restart();
    }

    public synchronized void restart() {
        stopServer();
        if (!settings.isBridgeEnabled()) {
            updateStatus(BridgeStatus.disabled());
            return;
        }

        String listenUrl = settings.getBridgeListenUrl();
        updateStatus(new BridgeStatus(BridgeStatus.State.STARTING, "Starting bridge listener…", listenUrl, null, null));
        try {
            ListenAddress listen = parseListenAddress(listenUrl);
            bridgeBinding = new BridgeBinding(listen);
            BridgeHttpServer next = new BridgeHttpServer(listen.address().getHostAddress(), listen.port());
            next.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            server = next;

            String normalized = "http://" + formatHost(listen.address()) + ":" + listen.port();
            Instant startedAt = Instant.now();
            updateStatus(new BridgeStatus(
                    BridgeStatus.State.LISTENING, "Bridge listening on " + normalized, normalized, startedAt, null));
            logService.addLog(LogService.Level.INFO, "[Bridge] Listening on " + normalized);
        } catch (Exception e) {
            bridgeBinding = null;
            String message = "Bridge listener failed: " + e.getMessage();
            updateStatus(new BridgeStatus(BridgeStatus.State.ERROR, message, listenUrl, null, null));
            logService.addLog(LogService.Level.ERROR, "[Bridge] " + e.getMessage());
        }
    }

    /** Tests the configured listener without blocking Swing's event dispatch thread. */
    public CompletableFuture<ConnectionTestResult> testConnection() {
        String listenUrl = settings.getBridgeListenUrl();
        return CompletableFuture.supplyAsync(() -> {
            try {
                ListenAddress listen = parseListenAddress(listenUrl);
                URI healthUri = URI.create("http://" + formatHost(listen.address()) + ":" + listen.port() + "/health");
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(2))
                        .build();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(healthUri)
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();
                java.net.http.HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    return new ConnectionTestResult(false, "Bridge connection failed: HTTP " + response.statusCode());
                }
                JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
                if (!"ok".equals(getString(body, "status"))
                        || !"vigolium-burp-bridge".equals(getString(body, "service"))) {
                    return new ConnectionTestResult(false, "Bridge connection failed: unexpected health response");
                }
                return new ConnectionTestResult(true, "Bridge connection successful");
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null || message.isBlank()) message = e.getClass().getSimpleName();
                return new ConnectionTestResult(false, "Bridge connection failed: " + message);
            }
        });
    }

    private NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session) {
        BridgeBinding currentBinding = bridgeBinding;
        if (currentBinding == null) {
            return writeRejected(NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, "bridge is not ready");
        }
        if (!currentBinding.acceptsHost(session.getHeaders().get("host"))) {
            return writeRejected(NanoHTTPD.Response.Status.FORBIDDEN, "unexpected Host header");
        }
        if (!currentBinding.acceptsOrigin(session.getHeaders().get("origin"))) {
            return writeRejected(NanoHTTPD.Response.Status.FORBIDDEN, "unexpected Origin header");
        }
        String path = session.getUri();
        if (path.equals("/") || path.equals("/health")) return handleRoot(session);
        if (path.equals("/api/burp-bridge/search")) return handleJsonEndpoint(session, this::search);
        if (path.equals("/api/burp-bridge/inspect")) return handleJsonEndpoint(session, this::inspect);
        if (path.equals("/api/burp-bridge/sitemap"))
            return handleJsonEndpoint(session, this::addToSiteMap, MAX_WRITE_BODY_BYTES);
        return writeError(NanoHTTPD.Response.Status.NOT_FOUND, "not found");
    }

    private NanoHTTPD.Response handleRoot(NanoHTTPD.IHTTPSession session) {
        if (session.getMethod() != NanoHTTPD.Method.GET) {
            return writeError(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "method not allowed");
        }
        JsonObject response = new JsonObject();
        response.addProperty("status", "ok");
        response.addProperty("service", "vigolium-burp-bridge");
        response.addProperty("read_only", false);
        response.addProperty("loopback_only", true);
        response.addProperty("in_scope_only", settings.isBridgeInScopeOnly());
        response.addProperty("authentication", "none");
        JsonArray capabilities = new JsonArray();
        capabilities.add("search_burp_items");
        capabilities.add("inspect_burp_item");
        capabilities.add("add_sitemap_item");
        response.add("capabilities", capabilities);
        return writeJson(NanoHTTPD.Response.Status.OK, response);
    }

    private NanoHTTPD.Response handleJsonEndpoint(NanoHTTPD.IHTTPSession session, JsonEndpoint endpoint) {
        return handleJsonEndpoint(session, endpoint, MAX_REQUEST_BYTES);
    }

    private NanoHTTPD.Response handleJsonEndpoint(
            NanoHTTPD.IHTTPSession session, JsonEndpoint endpoint, int maxBodyBytes) {
        if (session.getMethod() != NanoHTTPD.Method.POST) {
            return writeError(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "method not allowed");
        }
        try {
            String contentLengthValue = session.getHeaders().get("content-length");
            if (contentLengthValue == null) {
                return writeError(NanoHTTPD.Response.Status.BAD_REQUEST, "content-length is required");
            }
            int contentLength = Integer.parseInt(contentLengthValue);
            if (contentLength < 0 || contentLength > maxBodyBytes) {
                String limit = maxBodyBytes >= 1024 * 1024
                        ? (maxBodyBytes / (1024 * 1024)) + " MiB"
                        : (maxBodyBytes / 1024) + " KiB";
                return writeError(NanoHTTPD.Response.Status.BAD_REQUEST, "request exceeds " + limit);
            }
            byte[] body = session.getInputStream().readNBytes(contentLength);
            if (body.length != contentLength) {
                return writeError(NanoHTTPD.Response.Status.BAD_REQUEST, "incomplete request body");
            }
            JsonObject args = body.length == 0
                    ? new JsonObject()
                    : JsonParser.parseString(new String(body, StandardCharsets.UTF_8))
                            .getAsJsonObject();
            JsonObject result = endpoint.execute(args);
            BridgeStatus current = status;
            updateStatus(new BridgeStatus(
                    BridgeStatus.State.LISTENING,
                    current.message(),
                    current.listenUrl(),
                    current.startedAt(),
                    Instant.now()));
            return writeJson(NanoHTTPD.Response.Status.OK, result);
        } catch (IllegalArgumentException e) {
            return writeError(NanoHTTPD.Response.Status.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            logService.addLog(LogService.Level.ERROR, "[Bridge] Request failed: " + e.getMessage());
            return writeError(NanoHTTPD.Response.Status.INTERNAL_ERROR, e.getMessage());
        }
    }

    private JsonObject search(JsonObject args) {
        SearchCriteria criteria = SearchCriteria.from(args, settings.isBridgeInScopeOnly());
        List<BridgeItem> matches = new ArrayList<>();
        if (criteria.location().equals("proxy_history")) {
            for (ProxyHttpRequestResponse item :
                    api.proxy().history(proxyItem -> criteria.matches(BridgeItem.fromProxy(proxyItem)))) {
                matches.add(BridgeItem.fromProxy(item));
            }
        } else {
            for (burp.api.montoya.http.message.HttpRequestResponse item : api.siteMap()
                    .requestResponses(node -> criteria.matches(BridgeItem.fromSiteMap(node.requestResponse())))) {
                matches.add(BridgeItem.fromSiteMap(item));
            }
        }

        matches.sort(criteria.comparator());
        int total = matches.size();
        int from = Math.min(criteria.offset(), total);
        int to = cappedEndIndex(total, from, criteria.limit());

        JsonArray records = new JsonArray();
        for (BridgeItem item : matches.subList(from, to)) {
            String ref = remember(item);
            JsonObject summary = new JsonObject();
            summary.addProperty("ref", ref);
            summary.addProperty("location", criteria.location());
            summary.addProperty("method", item.request().method());
            summary.addProperty("url", item.request().url());
            summary.addProperty(
                    "request_hash", sha256(item.request().toByteArray().getBytes()));
            summary.addProperty("status", item.hasResponse() ? item.response().statusCode() : 0);
            if (item.hasResponse())
                summary.addProperty("mime_type", item.response().mimeType().toString());
            if (item.notes() != null && !item.notes().isBlank()) summary.addProperty("notes", item.notes());
            if (item.time() != null) summary.addProperty("time", item.time().toString());
            records.add(summary);
        }
        JsonObject output = new JsonObject();
        output.addProperty("total", total);
        output.addProperty("offset", from);
        output.addProperty("returned", records.size());
        output.addProperty("has_more", to < total);
        output.add("records", records);
        return output;
    }

    private JsonObject inspect(JsonObject args) {
        String ref = getString(args, "ref");
        if (ref.isBlank()) throw new IllegalArgumentException("ref is required");
        BridgeItem item;
        synchronized (references) {
            item = references.get(ref);
        }
        if (item == null) throw new IllegalArgumentException("Burp ref expired or unknown; search again");
        int maxBytes = args.has("max_bytes") ? args.get("max_bytes").getAsInt() : 16384;
        maxBytes = Math.max(1024, Math.min(maxBytes, MAX_INSPECT_BYTES));
        byte[] request = item.request().toByteArray().getBytes();
        byte[] response = item.hasResponse() ? item.response().toByteArray().getBytes() : new byte[0];
        JsonObject output = new JsonObject();
        output.addProperty("ref", ref);
        output.addProperty("url", item.request().url());
        // Small interactive inspections keep the legacy text fields. Larger
        // persistence reads use base64 only so binary messages are not doubled
        // (or heavily JSON-escaped) in the response.
        if (maxBytes <= MAX_REQUEST_BYTES) output.addProperty("request", clip(request, maxBytes));
        output.addProperty(
                "request_base64",
                Base64.getEncoder().encodeToString(Arrays.copyOf(request, Math.min(request.length, maxBytes))));
        output.addProperty("request_truncated", request.length > maxBytes);
        if (item.hasResponse()) {
            if (maxBytes <= MAX_REQUEST_BYTES) output.addProperty("response", clip(response, maxBytes));
            output.addProperty(
                    "response_base64",
                    Base64.getEncoder().encodeToString(Arrays.copyOf(response, Math.min(response.length, maxBytes))));
            output.addProperty("response_truncated", response.length > maxBytes);
        }
        return output;
    }

    private JsonObject addToSiteMap(JsonObject args) {
        String inputMode = getString(args, "input_mode");
        if (!inputMode.isBlank() && !"burp_base64".equals(inputMode)) {
            throw new IllegalArgumentException("input_mode must be burp_base64");
        }
        String url = getString(args, "url");
        if (url.isBlank()) throw new IllegalArgumentException("url is required");
        URI target = URI.create(url);
        if (target.getHost() == null
                || !("http".equalsIgnoreCase(target.getScheme()) || "https".equalsIgnoreCase(target.getScheme()))) {
            throw new IllegalArgumentException("url must be an absolute http or https URL");
        }
        byte[] request = decodeBase64(args, "http_request_base64", true);
        byte[] response = decodeBase64(args, "http_response_base64", false);
        if (request.length > MAX_SITE_MAP_MESSAGE_BYTES || response.length > MAX_SITE_MAP_MESSAGE_BYTES) {
            throw new IllegalArgumentException("request or response exceeds 8 MiB");
        }
        String source = getString(args, "source");
        if (source.isBlank()) source = "vigolium";
        source = source.replace('\r', ' ').replace('\n', ' ').strip();
        if (source.length() > 80) source = source.substring(0, 80);

        HttpRequestResponse item = siteMapItemFactory.create(url, request, response, source);
        api.siteMap().add(item);
        logService.addLog(LogService.Level.INFO, "[Bridge] Added 1 item to Target Site map from " + source);

        JsonObject output = new JsonObject();
        output.addProperty("added", 1);
        output.addProperty("url", item.request().url());
        output.addProperty("request_hash", sha256(request));
        output.addProperty("message", "added 1 item to Burp Target Site map");
        return output;
    }

    private static byte[] decodeBase64(JsonObject args, String name, boolean required) {
        String value = getString(args, name);
        if (value.isBlank()) {
            if (required) throw new IllegalArgumentException(name + " is required");
            return new byte[0];
        }
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(name + " is not valid base64");
        }
    }

    private static HttpRequestResponse createSiteMapItem(
            String url, byte[] rawRequest, byte[] rawResponse, String source) {
        HttpService service = HttpService.httpService(url);
        HttpRequest request = HttpRequest.httpRequest(service, ByteArray.byteArray(rawRequest));
        HttpResponse response =
                rawResponse.length == 0 ? null : HttpResponse.httpResponse(ByteArray.byteArray(rawResponse));
        Annotations annotations = Annotations.annotations("Imported from " + source + " via Vigolium bridge");
        return HttpRequestResponse.httpRequestResponse(request, response, annotations);
    }

    private String remember(BridgeItem item) {
        String ref = UUID.randomUUID().toString();
        synchronized (references) {
            references.put(ref, item);
            while (references.size() > MAX_REFERENCES) {
                references.remove(references.keySet().iterator().next());
            }
        }
        return ref;
    }

    static int cappedEndIndex(int total, int from, int limit) {
        int requested = limit == 0 ? MAX_REFERENCES : limit;
        return (int) Math.min((long) total, (long) from + Math.min(requested, MAX_REFERENCES));
    }

    private static ListenAddress parseListenAddress(String value) throws Exception {
        URI uri = URI.create(value == null ? "" : value.trim());
        if (!"http".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Bridge listener URL must use http://");
        }
        if (uri.getHost() == null || uri.getPort() < 1) {
            throw new IllegalArgumentException("Bridge listener URL must include a host and port");
        }
        if (uri.getRawPath() != null
                && !uri.getRawPath().isEmpty()
                && !uri.getRawPath().equals("/")) {
            throw new IllegalArgumentException("Bridge listener URL must not include a path");
        }
        InetAddress address = InetAddress.getByName(uri.getHost());
        if (!address.isLoopbackAddress()) {
            throw new IllegalArgumentException("Bridge listener must bind to a loopback address");
        }
        return new ListenAddress(address, uri.getPort(), uri.getHost());
    }

    private static String formatAuthority(String host, int port) {
        String normalized = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        return (normalized.contains(":") ? "[" + normalized + "]" : normalized) + ":" + port;
    }

    private static String normalizeHost(String host) {
        String normalized = host == null ? "" : host.strip().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        while (normalized.endsWith(".")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    private static String formatHost(InetAddress address) {
        String host = address.getHostAddress();
        return host.contains(":") ? "[" + host + "]" : host;
    }

    private static NanoHTTPD.Response writeError(NanoHTTPD.Response.Status status, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message == null ? "unknown error" : message);
        return writeJson(status, error);
    }

    private static NanoHTTPD.Response writeRejected(NanoHTTPD.Response.Status status, String message) {
        NanoHTTPD.Response response = writeError(status, message);
        response.closeConnection(true);
        return response;
    }

    private static NanoHTTPD.Response writeJson(NanoHTTPD.Response.Status status, JsonObject body) {
        NanoHTTPD.Response response =
                NanoHTTPD.newFixedLengthResponse(status, "application/json; charset=utf-8", body.toString());
        response.addHeader("Cache-Control", "no-store");
        response.addHeader("X-Content-Type-Options", "nosniff");
        return response;
    }

    private void updateStatus(BridgeStatus next) {
        status = next;
        publish(next);
    }

    private void publish(BridgeStatus value) {
        Consumer<BridgeStatus> listener = statusListener;
        if (listener != null) SwingUtilities.invokeLater(() -> listener.accept(value));
    }

    public synchronized void shutdown() {
        stopServer();
        updateStatus(BridgeStatus.disabled());
    }

    private void stopServer() {
        BridgeHttpServer current = server;
        server = null;
        bridgeBinding = null;
        if (current != null) current.stop();
        synchronized (references) {
            references.clear();
        }
    }

    private static String getString(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull()
                ? object.get(name).getAsString()
                : "";
    }

    private static String clip(byte[] value, int maxBytes) {
        return new String(value, 0, Math.min(value.length, maxBytes), StandardCharsets.ISO_8859_1);
    }

    private static String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder output = new StringBuilder(digest.length * 2);
            for (byte b : digest) output.append(String.format("%02x", b & 0xff));
            return output.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    @FunctionalInterface
    private interface JsonEndpoint {
        JsonObject execute(JsonObject args);
    }

    @FunctionalInterface
    interface SiteMapItemFactory {
        HttpRequestResponse create(String url, byte[] rawRequest, byte[] rawResponse, String source);
    }

    private final class BridgeHttpServer extends NanoHTTPD {
        private BridgeHttpServer(String hostname, int port) {
            super(hostname, port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            return handleRequest(session);
        }
    }

    private record ListenAddress(InetAddress address, int port, String configuredHost) {}

    private record BridgeBinding(ListenAddress listen) {
        boolean acceptsHost(String hostHeader) {
            Authority authority = Authority.parse(hostHeader, 80);
            if (authority == null || authority.port() != listen.port()) return false;
            String requestedHost = normalizeHost(authority.host());
            return requestedHost.equals(normalizeHost(listen.configuredHost()))
                    || requestedHost.equals(normalizeHost(listen.address().getHostAddress()));
        }

        boolean acceptsOrigin(String originHeader) {
            if (originHeader == null || originHeader.isBlank()) return true;
            try {
                URI origin = URI.create(originHeader);
                if (!"http".equalsIgnoreCase(origin.getScheme())
                        || origin.getHost() == null
                        || origin.getRawUserInfo() != null
                        || (origin.getRawPath() != null && !origin.getRawPath().isEmpty())
                        || origin.getRawQuery() != null
                        || origin.getRawFragment() != null) {
                    return false;
                }
                return acceptsHost(formatAuthority(origin.getHost(), origin.getPort() < 0 ? 80 : origin.getPort()));
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }

    private record Authority(String host, int port) {
        static Authority parse(String value, int defaultPort) {
            if (value == null || value.isBlank() || value.indexOf(',') >= 0) return null;
            try {
                URI uri = URI.create("http://" + value.trim());
                if (uri.getHost() == null
                        || uri.getRawUserInfo() != null
                        || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                        || uri.getRawQuery() != null
                        || uri.getRawFragment() != null) {
                    return null;
                }
                return new Authority(uri.getHost(), uri.getPort() < 0 ? defaultPort : uri.getPort());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    public record ConnectionTestResult(boolean successful, String message) {}

    private record BridgeItem(
            HttpRequest request, HttpResponse response, boolean hasResponse, String notes, Instant time) {
        static BridgeItem fromSiteMap(burp.api.montoya.http.message.HttpRequestResponse item) {
            return new BridgeItem(
                    item.request(),
                    item.hasResponse() ? item.response() : null,
                    item.hasResponse(),
                    item.annotations() != null ? item.annotations().notes() : "",
                    null);
        }

        static BridgeItem fromProxy(ProxyHttpRequestResponse item) {
            return new BridgeItem(
                    item.finalRequest(),
                    item.hasResponse() ? item.response() : null,
                    item.hasResponse(),
                    item.annotations() != null ? item.annotations().notes() : "",
                    item.time() != null ? item.time().toInstant() : null);
        }

        boolean contains(String text) {
            if (text == null || text.isBlank()) return true;
            if (request.contains(text, false)) return true;
            return hasResponse && response.contains(text, false);
        }
    }

    private record SearchCriteria(
            String location,
            String host,
            Set<String> methods,
            String path,
            Set<Integer> statuses,
            String mimeType,
            List<String> searchTerms,
            List<String> excludeTerms,
            Instant fromTime,
            Instant toTime,
            boolean inScopeOnly,
            int limit,
            int offset,
            String sortBy,
            boolean sortAscending) {

        static SearchCriteria from(JsonObject args, boolean bridgeInScopeOnly) {
            String location = getString(args, "location");
            if (!location.equals("proxy_history")) location = "sitemap";
            Set<String> methods = new HashSet<>();
            if (args.has("methods") && args.get("methods").isJsonArray()) {
                for (JsonElement method : args.getAsJsonArray("methods")) {
                    methods.add(method.getAsString().toUpperCase(Locale.ROOT));
                }
            }
            Set<Integer> statuses = new HashSet<>();
            if (args.has("status") && args.get("status").isJsonArray()) {
                for (JsonElement status : args.getAsJsonArray("status")) statuses.add(status.getAsInt());
            }
            List<String> searchTerms = stringList(args, "search_terms");
            String legacyText = getString(args, "text");
            if (!legacyText.isBlank()) searchTerms.add(legacyText);
            addIfPresent(searchTerms, getString(args, "header"));
            addIfPresent(searchTerms, getString(args, "body"));
            List<String> excludeTerms = stringList(args, "exclude_terms");
            addIfPresent(excludeTerms, getString(args, "exclude_header"));
            addIfPresent(excludeTerms, getString(args, "exclude_body"));
            int limit = args.has("limit") ? args.get("limit").getAsInt() : 50;
            return new SearchCriteria(
                    location,
                    getString(args, "host"),
                    methods,
                    getString(args, "path"),
                    statuses,
                    getString(args, "mime_type"),
                    searchTerms,
                    excludeTerms,
                    parseInstant(getString(args, "from")),
                    parseInstant(getString(args, "to")),
                    bridgeInScopeOnly
                            || (args.has("in_scope_only")
                                    && args.get("in_scope_only").getAsBoolean()),
                    Math.max(0, Math.min(limit, 5000)),
                    args.has("offset") ? Math.max(0, args.get("offset").getAsInt()) : 0,
                    getString(args, "sort"),
                    "asc".equalsIgnoreCase(getString(args, "order")));
        }

        private static List<String> stringList(JsonObject args, String name) {
            List<String> values = new ArrayList<>();
            if (args.has(name) && args.get(name).isJsonArray()) {
                for (JsonElement value : args.getAsJsonArray(name)) addIfPresent(values, value.getAsString());
            }
            return values;
        }

        private static void addIfPresent(List<String> values, String value) {
            if (value != null && !value.isBlank()) values.add(value);
        }

        private static Instant parseInstant(String value) {
            return value == null || value.isBlank() ? null : Instant.parse(value);
        }

        Comparator<BridgeItem> comparator() {
            Comparator<BridgeItem> comparator =
                    switch (sortBy) {
                        case "method" -> Comparator.comparing(
                                item -> item.request().method(), String.CASE_INSENSITIVE_ORDER);
                        case "path" -> Comparator.comparing(
                                item -> item.request().path(), String.CASE_INSENSITIVE_ORDER);
                        case "status", "status_code" -> Comparator.comparingInt(
                                item -> item.hasResponse() ? item.response().statusCode() : 0);
                        case "url" -> Comparator.comparing(
                                item -> item.request().url(), String.CASE_INSENSITIVE_ORDER);
                        default -> Comparator.comparing(item -> item.time() == null ? Instant.EPOCH : item.time());
                    };
            comparator = comparator.thenComparing(item -> item.request().url(), String.CASE_INSENSITIVE_ORDER);
            return sortAscending ? comparator : comparator.reversed();
        }

        boolean matches(BridgeItem item) {
            if (inScopeOnly && !item.request().isInScope()) return false;
            if ((fromTime != null || toTime != null) && item.time() == null) return false;
            if (fromTime != null && item.time().isBefore(fromTime)) return false;
            if (toTime != null && item.time().isAfter(toTime)) return false;
            if (!hostMatches(item.request(), host)) return false;
            if (!methods.isEmpty() && !methods.contains(item.request().method().toUpperCase(Locale.ROOT))) return false;
            String pathPattern = path.replace("*", "");
            if (!pathPattern.isBlank()
                    && !item.request().path().toLowerCase(Locale.ROOT).contains(pathPattern.toLowerCase(Locale.ROOT))) {
                return false;
            }
            if (!statuses.isEmpty()
                    && (!item.hasResponse()
                            || !statuses.contains((int) item.response().statusCode()))) {
                return false;
            }
            if (!mimeType.isBlank()
                    && (!item.hasResponse()
                            || !item.response()
                                    .mimeType()
                                    .toString()
                                    .toLowerCase(Locale.ROOT)
                                    .contains(mimeType.toLowerCase(Locale.ROOT)))) {
                return false;
            }
            for (String term : searchTerms) {
                if (!item.contains(term)) return false;
            }
            for (String term : excludeTerms) {
                if (item.contains(term)) return false;
            }
            return true;
        }

        private static boolean hostMatches(HttpRequest request, String pattern) {
            if (pattern == null || pattern.isBlank()) return true;
            String requestHost = "";
            if (request.httpService() != null)
                requestHost = request.httpService().host();
            if (requestHost == null || requestHost.isBlank()) {
                try {
                    requestHost = URI.create(request.url()).getHost();
                } catch (IllegalArgumentException ignored) {
                    return false;
                }
            }
            requestHost = normalizeHost(requestHost);
            String normalizedPattern = normalizeHost(pattern);
            if (normalizedPattern.isBlank()) return true;
            if (!normalizedPattern.contains("*")) return requestHost.equals(normalizedPattern);

            StringBuilder regex = new StringBuilder("^");
            for (int i = 0; i < normalizedPattern.length(); i++) {
                char c = normalizedPattern.charAt(i);
                if (c == '*') regex.append(".*");
                else if ("\\.[]{}()+-^$|?".indexOf(c) >= 0) regex.append('\\').append(c);
                else regex.append(c);
            }
            return requestHost.matches(regex.append('$').toString());
        }
    }
}
