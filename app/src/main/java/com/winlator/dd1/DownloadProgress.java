package com.winlator.dd1;

import java.util.HashSet;
import java.util.Set;

// The downloader reports progress per depot, and its percentage arrives as a
// fraction in some callbacks and as a percentage in others, so both are read
// here and the depots are combined into one figure.
public final class DownloadProgress {
    private final Set<Integer> finished = new HashSet<>();
    private int currentDepot = -1;
    private double currentPercent;

    public static double percent(float reported) {
        double value = reported <= 1.0 ? reported * 100.0 : reported;
        return Math.max(0.0, Math.min(100.0, value));
    }

    public void onDepotProgress(int depotId, float reported) {
        currentDepot = depotId;
        currentPercent = percent(reported);
    }

    public void onDepotFinished(int depotId) {
        finished.add(depotId);
        if (depotId == currentDepot) currentPercent = 100.0;
    }

    // Unknown until the first report; the caller shows an indeterminate bar.
    public double overall() {
        return combined();
    }

    private double combined() {
        int depots = finished.size() + (currentDepot >= 0 && !finished.contains(currentDepot) ? 1 : 0);
        if (depots == 0) return -1;
        return (finished.size() * 100.0 + (finished.contains(currentDepot) ? 0 : currentPercent)) / depots;
    }
}
