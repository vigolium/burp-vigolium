package com.vigolium.extension.service;

import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;

public class RequestCounters {

    private final AtomicInteger sentCount = new AtomicInteger();
    private final AtomicInteger pendingCount = new AtomicInteger();
    private final AtomicInteger failedCount = new AtomicInteger();

    private Runnable onChanged;

    public void setOnChanged(Runnable callback) {
        this.onChanged = callback;
    }

    public int getSentCount() {
        return sentCount.get();
    }

    public int getPendingCount() {
        return pendingCount.get();
    }

    public int getFailedCount() {
        return failedCount.get();
    }

    public void incrementPending() {
        pendingCount.incrementAndGet();
        fireChanged();
    }

    public void markSent() {
        pendingCount.decrementAndGet();
        sentCount.incrementAndGet();
        fireChanged();
    }

    public void markFailed() {
        pendingCount.decrementAndGet();
        failedCount.incrementAndGet();
        fireChanged();
    }

    public void reset() {
        sentCount.set(0);
        pendingCount.set(0);
        failedCount.set(0);
        fireChanged();
    }

    private void fireChanged() {
        if (onChanged != null) {
            SwingUtilities.invokeLater(onChanged);
        }
    }
}
