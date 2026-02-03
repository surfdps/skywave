package org.wxter.skywave.client.tracker;

import org.wxter.skywave.config.SkywaveConfig;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ItemTrackerState {
    private final ConcurrentHashMap<String, Long> sessionCounts = new ConcurrentHashMap<>();
    private final ItemTrackerStorage storage;

    private volatile boolean running = false;
    private long sessionStartMillis = 0L;
    private boolean timerPaused = false;
    private long pausedAtMillis = 0L;
    private long accumulatedPausedMillis = 0L;

    public ItemTrackerState(ItemTrackerStorage storage) {
        this.storage = storage;
    }

    public void startSession() {
        if (running) return;
        running = true;
        sessionStartMillis = System.currentTimeMillis();
        timerPaused = false;
        accumulatedPausedMillis = 0;
        pausedAtMillis = 0;
        sessionCounts.clear();
    }

    public void stopSession() {
        running = false;
        timerPaused = false;
        pausedAtMillis = 0L;
        accumulatedPausedMillis = 0L;
    }

    public void reset(SkywaveConfig.DisplayMode mode) {
        if (mode == SkywaveConfig.DisplayMode.TOTAL) {
            storage.resetTotals();
            sessionCounts.clear();
            storage.save();
        } else {
            sessionCounts.clear();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public String getSessionUptimeFormatted() {
        if (!running) return "00:00:00";
        long now = System.currentTimeMillis();
        long elapsed = now - sessionStartMillis - accumulatedPausedMillis;
        if (timerPaused && pausedAtMillis > 0) elapsed = pausedAtMillis - sessionStartMillis - accumulatedPausedMillis;
        Duration d = Duration.ofMillis(Math.max(0, elapsed));
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        long seconds = d.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public double getSessionHoursElapsed() {
        if (!running) return 0.0;
        long now = System.currentTimeMillis();
        long elapsed = now - sessionStartMillis - accumulatedPausedMillis;
        if (timerPaused && pausedAtMillis > 0) elapsed = pausedAtMillis - sessionStartMillis - accumulatedPausedMillis;
        return Math.max(0.0, elapsed / 3600000.0);
    }

    public void toggleTimerPause() {
        if (!running) return;
        if (!timerPaused) {
            timerPaused = true;
            pausedAtMillis = System.currentTimeMillis();
        } else {
            timerPaused = false;
            if (pausedAtMillis > 0) accumulatedPausedMillis += System.currentTimeMillis() - pausedAtMillis;
            pausedAtMillis = 0;
        }
    }

    public boolean isTimerPaused() {
        return timerPaused;
    }

    public void recordItem(String rawName, long count) {
        if (rawName == null || rawName.isEmpty()) return;
        sessionCounts.merge(rawName, count, Long::sum);
        storage.incrementTotal(rawName, count);
        storage.save();
    }

    public Map<String, Long> getSessionCounts() {
        return sessionCounts;
    }

    public Map<String, Long> getCounts(SkywaveConfig.DisplayMode mode) {
        if (mode == SkywaveConfig.DisplayMode.TOTAL) {
            return storage.getTotalCounts();
        }
        return sessionCounts;
    }
}
