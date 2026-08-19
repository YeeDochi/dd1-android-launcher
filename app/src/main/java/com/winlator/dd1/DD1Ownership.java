package com.winlator.dd1;

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
}
