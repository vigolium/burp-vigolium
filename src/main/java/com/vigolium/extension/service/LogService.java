package com.vigolium.extension.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class LogService {

    public enum Level {
        INFO,
        WARN,
        ERROR
    }

    public record LogEntry(String timestamp, Level level, String message) {}

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final List<LogEntry> entries = new ArrayList<>();
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
    private int maxEntries = 1000;

    public synchronized void addLog(Level level, String message) {
        if (entries.size() >= maxEntries) {
            entries.removeFirst();
        }
        entries.add(new LogEntry(LocalDateTime.now().format(FORMATTER), level, message));
        notifyListeners();
    }

    public synchronized List<LogEntry> getEntries() {
        return List.copyOf(entries);
    }

    public synchronized void setMaxEntries(int max) {
        this.maxEntries = max;
        while (entries.size() > maxEntries) {
            entries.removeFirst();
        }
        notifyListeners();
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public synchronized void clear() {
        entries.clear();
        notifyListeners();
    }

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    private void notifyListeners() {
        changeListeners.forEach(Runnable::run);
    }
}
