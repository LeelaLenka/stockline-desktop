package com.bharat.stockline.service;

import com.bharat.stockline.dao.ActivityDao;
import com.bharat.stockline.model.ActivityEntry;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Real-time updates across every running copy of the app.
 * Every change writes a row to the activity table; each app checks for new rows every 2 seconds
 * (one indexed query), so a sale made on one computer appears on all the others almost instantly.
 */
public class LiveFeed {

    private final ActivityDao activity;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "live-feed");
        t.setDaemon(true);
        return t;
    });
    private volatile long lastSeenId;

    public LiveFeed(ActivityDao activity) {
        this.activity = activity;
    }

    /**
     * @param onChanges called (on the background thread) with new activity rows
     * @param onStatus  called with true when the database answers, false when it doesn't
     */
    public void start(Consumer<List<ActivityEntry>> onChanges, Consumer<Boolean> onStatus) {
        try {
            lastSeenId = activity.maxId();
        } catch (Exception e) {
            lastSeenId = 0;
        }
        timer.scheduleWithFixedDelay(() -> {
            try {
                List<ActivityEntry> fresh = activity.since(lastSeenId);
                onStatus.accept(true);
                if (!fresh.isEmpty()) {
                    lastSeenId = fresh.get(fresh.size() - 1).id();
                    onChanges.accept(fresh);
                }
            } catch (Exception e) {
                onStatus.accept(false);
            }
        }, 2, 2, TimeUnit.SECONDS);
    }

    public void stop() {
        timer.shutdownNow();
    }
}
