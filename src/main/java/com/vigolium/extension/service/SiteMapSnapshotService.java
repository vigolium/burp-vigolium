package com.vigolium.extension.service;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.vigolium.extension.config.SnapshotSettings;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

public class SiteMapSnapshotService {

    static final int CHUNK_SIZE = 100;
    static final int MAX_CHUNK_RAW_BYTES = 8 * 1024 * 1024;
    static final int MAX_RECORD_RAW_BYTES = MAX_CHUNK_RAW_BYTES;

    private final MontoyaApi api;
    private final SnapshotSettings settings;
    private final VigoliumApiService apiService;
    private final LogService logService;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "vigolium-sitemap-snapshot");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean inProgress = new AtomicBoolean(false);
    private final Map<String, String> synchronizedFingerprints = new ConcurrentHashMap<>();

    private volatile ScheduledFuture<?> automaticTask;
    private volatile Consumer<SnapshotStatus> statusListener;
    private volatile SnapshotStatus lastStatus = SnapshotStatus.idle();
    private String synchronizedDestinationIdentity;
    private boolean destinationInitialized;

    public SiteMapSnapshotService(
            MontoyaApi api, SnapshotSettings settings, VigoliumApiService apiService, LogService logService) {
        this.api = api;
        this.settings = settings;
        this.apiService = apiService;
        this.logService = logService;
    }

    public void setStatusListener(Consumer<SnapshotStatus> listener) {
        this.statusListener = listener;
        publish(lastStatus);
    }

    public void start() {
        rescheduleAutomaticSnapshot();
    }

    public void snapshotNow(String source) {
        executor.execute(() -> runSnapshot(source));
    }

    public synchronized void rescheduleAutomaticSnapshot() {
        if (automaticTask != null) {
            automaticTask.cancel(false);
            automaticTask = null;
        }
        if (!settings.isSnapshotAutoEnabled()) {
            SnapshotStatus disabled = new SnapshotStatus(
                    SnapshotStatus.State.DISABLED,
                    "Automatic snapshots disabled",
                    lastStatus.discovered(),
                    lastStatus.uploaded(),
                    lastStatus.inserted(),
                    lastStatus.updated(),
                    lastStatus.unchanged(),
                    lastStatus.failed(),
                    lastStatus.completedAt(),
                    null);
            lastStatus = disabled;
            publish(disabled);
            return;
        }
        int interval = Math.max(1, settings.getSnapshotIntervalMinutes());
        automaticTask = executor.scheduleWithFixedDelay(() -> runSnapshot("Auto"), 0, interval, TimeUnit.MINUTES);
    }

    private void runSnapshot(String source) {
        if (!inProgress.compareAndSet(false, true)) {
            logService.addLog(LogService.Level.WARN, "[Site Map Snapshot] Skipped: another snapshot is running");
            return;
        }
        publish(new SnapshotStatus(
                SnapshotStatus.State.RUNNING,
                "Reading Burp Target site map…",
                0,
                0,
                0,
                0,
                0,
                0,
                lastStatus.completedAt(),
                null));
        try {
            if (!apiService.isConfigured()) {
                throw new IllegalStateException("Vigolium server URL is not configured");
            }
            resetCacheIfDestinationChanged(apiService.destinationIdentity());

            List<HttpRequestResponse> siteMap = api.siteMap().requestResponses();
            List<PendingSnapshotRecord> pending = new ArrayList<>();
            int discovered = 0;
            int oversized = 0;
            for (HttpRequestResponse rr : siteMap) {
                if (rr == null || rr.request() == null) continue;
                if (settings.isSnapshotInScopeOnly() && !rr.request().isInScope()) continue;
                discovered++;
                PendingSnapshotRecord record = toPendingRecord(rr);
                if (record == null) {
                    oversized++;
                    logService.addLog(
                            LogService.Level.WARN,
                            "[Site Map Snapshot] Skipped oversized record: "
                                    + rr.request().url());
                    continue;
                }
                if (!record.contentFingerprint().equals(synchronizedFingerprints.get(record.identityFingerprint()))) {
                    pending.add(record);
                }
            }

            int inserted = 0;
            int updated = 0;
            int unchanged = 0;
            int failed = oversized;
            int uploaded = 0;
            String snapshotId = UUID.randomUUID().toString();
            String capturedAt = Instant.now().toString();
            int nextRecord = 0;
            int chunkIndex = 0;
            while (nextRecord < pending.size()) {
                List<SiteMapSnapshotRecord> records = new ArrayList<>();
                int chunkRawBytes = 0;
                while (nextRecord < pending.size() && records.size() < CHUNK_SIZE) {
                    PendingSnapshotRecord pendingRecord = pending.get(nextRecord);
                    if (!records.isEmpty() && chunkRawBytes + pendingRecord.rawBytes() > MAX_CHUNK_RAW_BYTES) break;
                    records.add(toSnapshotRecord(pendingRecord));
                    chunkRawBytes += pendingRecord.rawBytes();
                    nextRecord++;
                }
                SnapshotChunkResponse response = apiService.snapshotSiteMap(new SiteMapSnapshotRequest(
                        snapshotId, chunkIndex, nextRecord == pending.size(), capturedAt, records));
                uploaded += records.size();
                inserted += response.inserted();
                updated += response.updated();
                unchanged += response.unchanged();
                failed += response.skipped();
                if (response.skipped() == 0) {
                    for (SiteMapSnapshotRecord record : records) {
                        synchronizedFingerprints.put(record.identityFingerprint(), record.contentFingerprint());
                    }
                }
                chunkIndex++;
            }

            Instant completedAt = Instant.now();
            Instant nextRun = settings.isSnapshotAutoEnabled()
                    ? completedAt.plusSeconds((long) settings.getSnapshotIntervalMinutes() * 60)
                    : null;
            SnapshotStatus success = new SnapshotStatus(
                    SnapshotStatus.State.SUCCESS,
                    pending.isEmpty() && oversized == 0
                            ? "Site map already synchronized"
                            : "Site map snapshot completed",
                    discovered,
                    uploaded,
                    inserted,
                    updated,
                    unchanged,
                    failed,
                    completedAt,
                    nextRun);
            lastStatus = success;
            publish(success);
            logService.addLog(
                    LogService.Level.INFO,
                    "[Site Map Snapshot:" + source + "] discovered=" + discovered + " uploaded=" + uploaded
                            + " inserted=" + inserted + " updated=" + updated + " unchanged=" + unchanged
                            + " failed=" + failed);
        } catch (Exception e) {
            SnapshotStatus failed = new SnapshotStatus(
                    SnapshotStatus.State.FAILED,
                    "Snapshot failed: " + e.getMessage(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    1,
                    Instant.now(),
                    settings.isSnapshotAutoEnabled()
                            ? Instant.now().plusSeconds((long) settings.getSnapshotIntervalMinutes() * 60)
                            : null);
            lastStatus = failed;
            publish(failed);
            logService.addLog(LogService.Level.ERROR, "[Site Map Snapshot:" + source + "] " + e.getMessage());
        } finally {
            inProgress.set(false);
        }
    }

    private void resetCacheIfDestinationChanged(String destinationIdentity) {
        if (destinationInitialized && java.util.Objects.equals(synchronizedDestinationIdentity, destinationIdentity)) {
            return;
        }
        synchronizedFingerprints.clear();
        synchronizedDestinationIdentity = destinationIdentity;
        destinationInitialized = true;
    }

    private static PendingSnapshotRecord toPendingRecord(HttpRequestResponse rr) throws Exception {
        ByteArray requestBytes = rr.request().toByteArray();
        ByteArray responseBytes = rr.hasResponse() ? rr.response().toByteArray() : null;
        int requestLength = requestBytes.length();
        int responseLength = responseBytes == null ? 0 : responseBytes.length();
        long rawBytes = (long) requestLength + responseLength;
        if (rawBytes > MAX_RECORD_RAW_BYTES) return null;

        byte[] request = requestBytes.getBytes();
        byte[] response = responseBytes == null ? new byte[0] : responseBytes.getBytes();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(rr.request().url().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(request);
        String identityFingerprint = HexFormat.of().formatHex(digest.digest());
        digest.reset();
        digest.update(identityFingerprint.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        digest.update((byte) 0);
        digest.update(response);
        return new PendingSnapshotRecord(
                rr, identityFingerprint, HexFormat.of().formatHex(digest.digest()), Math.toIntExact(rawBytes));
    }

    private static SiteMapSnapshotRecord toSnapshotRecord(PendingSnapshotRecord pending) {
        HttpRequestResponse rr = pending.item();
        byte[] request = rr.request().toByteArray().getBytes();
        byte[] response = rr.hasResponse() ? rr.response().toByteArray().getBytes() : new byte[0];
        return new SiteMapSnapshotRecord(
                rr.request().url(),
                Base64.getEncoder().encodeToString(request),
                response.length == 0 ? null : Base64.getEncoder().encodeToString(response),
                pending.identityFingerprint(),
                pending.contentFingerprint());
    }

    private record PendingSnapshotRecord(
            HttpRequestResponse item, String identityFingerprint, String contentFingerprint, int rawBytes) {}

    private void publish(SnapshotStatus status) {
        Consumer<SnapshotStatus> listener = statusListener;
        if (listener == null) return;
        SwingUtilities.invokeLater(() -> listener.accept(status));
    }

    public synchronized void shutdown() {
        if (automaticTask != null) automaticTask.cancel(false);
        executor.shutdownNow();
    }
}
