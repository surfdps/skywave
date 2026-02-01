package org.wxter.skywave.client.tracker;

import java.util.Map;

public interface ItemTrackerStorage {
    Map<String, Long> getTotalCounts();

    void incrementTotal(String name, long delta);

    void resetTotals();

    void save();
}
