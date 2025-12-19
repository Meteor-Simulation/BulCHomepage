package com.bulc.homepage.licensing.query.view;

import java.util.List;
import java.util.Map;

public record PolicySnapshotView(
        int maxActivations,
        int maxConcurrentSessions,
        int gracePeriodDays,
        int allowOfflineDays,
        List<String> entitlements
) {
    @SuppressWarnings("unchecked")
    public static PolicySnapshotView from(Map<String, Object> policySnapshot) {
        if (policySnapshot == null) {
            return new PolicySnapshotView(1, 1, 0, 0, List.of());
        }

        return new PolicySnapshotView(
                getInt(policySnapshot, "maxActivations", 1),
                getInt(policySnapshot, "maxConcurrentSessions", 1),
                getInt(policySnapshot, "gracePeriodDays", 0),
                getInt(policySnapshot, "allowOfflineDays", 0),
                (List<String>) policySnapshot.getOrDefault("entitlements", List.of())
        );
    }

    private static int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
}
