package com.winlator.dd1;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class DD1Ownership {
    public static boolean ownsApp(Map<Integer, List<Integer>> packageApps, int appId) {
        if (packageApps == null) return false;
        for (List<Integer> appIds : packageApps.values()) {
            if (appIds != null && appIds.contains(appId)) return true;
        }
        return false;
    }

    // Every other app that ships in a package containing the game is DLC for it.
    public static List<Integer> dlcAppIds(Map<Integer, List<Integer>> packageApps, int appId) {
        List<Integer> result = new ArrayList<>();
        if (packageApps == null) return result;
        for (List<Integer> appIds : packageApps.values()) {
            if (appIds == null || !appIds.contains(appId)) continue;
            for (int candidate : appIds) {
                if (candidate != appId && !result.contains(candidate)) result.add(candidate);
            }
        }
        return result;
    }
}
