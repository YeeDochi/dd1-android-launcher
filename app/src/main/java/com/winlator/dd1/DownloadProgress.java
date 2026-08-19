package com.winlator.dd1;

import java.util.LinkedHashSet;
import java.util.Set;

// The downloader reports progress for the depot it is working on, and nothing
// about the whole job, so that is what is shown. Guessing an overall figure made
// a resumed download open at 88% and stall there.
public final class DownloadProgress {
    private final Set<Integer> finished = new LinkedHashSet<>();
    private int currentDepot;
    private double currentPercent = -1;

    public static double percent(float reported) {
        double value = reported <= 1.0 ? reported * 100.0 : reported;
        return Math.max(0.0, Math.min(100.0, value));
    }

    public void onDepotProgress(int depotId, float reported) {
        if (depotId > 0 && depotId != currentDepot) {
            currentDepot = depotId;
            currentPercent = -1;
        }
        if (reported > 0) currentPercent = percent(reported);
    }

    public void onDepotFinished(int depotId) {
        if (depotId > 0) finished.add(depotId);
        currentPercent = -1;
    }

    public int currentDepot() {
        return currentDepot;
    }

    public int finishedCount() {
        return finished.size();
    }

    // Negative until the current depot has reported something.
    public double currentPercent() {
        return currentPercent;
    }
}
