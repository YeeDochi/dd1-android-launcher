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

    // DLC arrives in its own package, so ownership is read across all of them
    // and the caller decides which apps belong to the game.
    public static List<Integer> ownedAppIds(Map<Integer, List<Integer>> packageApps) {
        List<Integer> result = new ArrayList<>();
        if (packageApps == null) return result;
        for (List<Integer> appIds : packageApps.values()) {
            if (appIds == null) continue;
            for (int appId : appIds) {
                if (!result.contains(appId)) result.add(appId);
            }
        }
        return result;
    }
}
