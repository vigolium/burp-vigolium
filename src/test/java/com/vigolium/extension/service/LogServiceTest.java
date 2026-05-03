package com.vigolium.extension.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LogServiceTest {

    private LogService logService;

    @BeforeEach
    void setUp() {
        logService = new LogService();
    }

    // ──── Basic logging ────

    @Test
    void addLog_storesEntry() {
        logService.addLog(LogService.Level.INFO, "test message");

        var entries = logService.getEntries();
        assertEquals(1, entries.size());
        assertEquals(LogService.Level.INFO, entries.get(0).level());
        assertEquals("test message", entries.get(0).message());
        assertNotNull(entries.get(0).timestamp());
    }

    @Test
    void addLog_allLevels() {
        logService.addLog(LogService.Level.INFO, "info");
        logService.addLog(LogService.Level.WARN, "warn");
        logService.addLog(LogService.Level.ERROR, "error");

        var entries = logService.getEntries();
        assertEquals(3, entries.size());
        assertEquals(LogService.Level.INFO, entries.get(0).level());
        assertEquals(LogService.Level.WARN, entries.get(1).level());
        assertEquals(LogService.Level.ERROR, entries.get(2).level());
    }

    @Test
    void addLog_preservesOrderChronologically() {
        logService.addLog(LogService.Level.INFO, "first");
        logService.addLog(LogService.Level.INFO, "second");
        logService.addLog(LogService.Level.INFO, "third");

        var entries = logService.getEntries();
        assertEquals("first", entries.get(0).message());
        assertEquals("second", entries.get(1).message());
        assertEquals("third", entries.get(2).message());
    }

    // ──── Max entries eviction ────

    @Test
    void defaultMaxEntries_is1000() {
        assertEquals(1000, logService.getMaxEntries());
    }

    @Test
    void maxEntries_evictsOldest() {
        logService.setMaxEntries(3);

        logService.addLog(LogService.Level.INFO, "msg1");
        logService.addLog(LogService.Level.INFO, "msg2");
        logService.addLog(LogService.Level.INFO, "msg3");
        logService.addLog(LogService.Level.INFO, "msg4");

        var entries = logService.getEntries();
        assertEquals(3, entries.size());
        assertEquals("msg2", entries.get(0).message());
        assertEquals("msg4", entries.get(2).message());
    }

    @Test
    void maxEntries_evictsMultipleWhenReduced() {
        logService.addLog(LogService.Level.INFO, "msg1");
        logService.addLog(LogService.Level.INFO, "msg2");
        logService.addLog(LogService.Level.INFO, "msg3");
        logService.addLog(LogService.Level.INFO, "msg4");
        logService.addLog(LogService.Level.INFO, "msg5");

        logService.setMaxEntries(2);

        var entries = logService.getEntries();
        assertEquals(2, entries.size());
        assertEquals("msg4", entries.get(0).message());
        assertEquals("msg5", entries.get(1).message());
    }

    @Test
    void setMaxEntries_500() {
        logService.setMaxEntries(500);
        assertEquals(500, logService.getMaxEntries());
    }

    @Test
    void setMaxEntries_5000() {
        logService.setMaxEntries(5000);
        assertEquals(5000, logService.getMaxEntries());
    }

    // ──── Clear ────

    @Test
    void clear_removesAll() {
        logService.addLog(LogService.Level.ERROR, "error");
        logService.addLog(LogService.Level.INFO, "info");
        logService.clear();

        assertTrue(logService.getEntries().isEmpty());
    }

    // ──── Change listeners ────

    @Test
    void changeListener_notifiedOnAdd() {
        AtomicInteger count = new AtomicInteger();
        logService.addChangeListener(count::incrementAndGet);

        logService.addLog(LogService.Level.WARN, "warning");

        assertEquals(1, count.get());
    }

    @Test
    void changeListener_notifiedOnClear() {
        AtomicInteger count = new AtomicInteger();
        logService.addChangeListener(count::incrementAndGet);

        logService.clear();

        assertEquals(1, count.get());
    }

    @Test
    void changeListener_notifiedOnSetMaxEntries() {
        AtomicInteger count = new AtomicInteger();
        logService.addChangeListener(count::incrementAndGet);

        logService.setMaxEntries(500);

        assertEquals(1, count.get());
    }

    @Test
    void multipleChangeListeners() {
        AtomicInteger a = new AtomicInteger();
        AtomicInteger b = new AtomicInteger();
        logService.addChangeListener(a::incrementAndGet);
        logService.addChangeListener(b::incrementAndGet);

        logService.addLog(LogService.Level.INFO, "msg");

        assertEquals(1, a.get());
        assertEquals(1, b.get());
    }

    // ──── Immutability ────

    @Test
    void getEntries_returnsImmutableCopy() {
        logService.addLog(LogService.Level.INFO, "msg");

        var entries = logService.getEntries();
        assertThrows(UnsupportedOperationException.class, () -> entries.add(null));
    }

    // ──── Thread safety ────

    @Test
    void threadSafety_concurrentAddAndRead() throws Exception {
        int threads = 8;
        int perThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            int threadId = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        logService.addLog(LogService.Level.INFO, "t" + threadId + "-" + i);
                        logService.getEntries(); // concurrent read
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        // All entries should be present (default max 1000, total 800)
        assertEquals(threads * perThread, logService.getEntries().size());
    }

    @Test
    void threadSafety_concurrentAddWithEviction() throws Exception {
        logService.setMaxEntries(50);
        int threads = 4;
        int perThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        logService.addLog(LogService.Level.INFO, "msg-" + i);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        // Should never exceed maxEntries
        assertTrue(logService.getEntries().size() <= 50);
    }
}
