package com.winlator.dd1;

import java.util.LinkedHashSet;
import java.util.Set;

// The downloader reports progress per depot, and its percentage arrives as a
// fraction in some callbacks and as a percentage in others, so both are read
// here and the depots are combined into one figure.
public final class DownloadProgress {
    private final Set<Integer> finished = new LinkedHashSet<>();
    private final Set<Integer> seen = new LinkedHashSet<>();
    private int currentDepot = -1;
    private double currentPercent;
    private boolean allSeen;

    public static double percent(float reported) {
        double value = reported <= 1.0 ? reported * 100.0 : reported;
        return Math.max(0.0, Math.min(100.0, value));
    }

    public void onDepotProgress(int depotId, float reported) {
        seen.add(depotId);
        currentDepot = depotId;
        currentPercent = percent(reported);
    }

    public void onDepotFinished(int depotId) {
        seen.add(depotId);
        finished.add(depotId);
        if (depotId == currentDepot) currentPercent = 100.0;
    }

    // Which depot of the ones seen so far is being fetched. The total is only
    // known as depots arrive, so it grows during the download.
    public int currentIndex() {
        int index = 0;
        for (int depotId : seen) {
            index++;
            if (depotId == currentDepot) return index;
        }
        return 0;
    }

    public int depotCount() {
        return seen.size();
    }

    // Depots arrive one at a time, so the total is a guess until the downloader
    // says it is done handing them out.
    public boolean totalKnown() {
        return allSeen;
    }

    public void onAllDepotsSeen() {
        allSeen = true;
    }

    // Unknown until the first report; the caller shows an indeterminate bar.
    public double overall() {
        return combined();
    }

    // Until a depot has finished there is no way to tell how much of the whole
    // download one depot represents, so the caller keeps an indeterminate bar.
    private double combined() {
        if (finished.isEmpty()) return -1;
        int depots = finished.size() + (finished.contains(currentDepot) ? 0 : 1);
        return (finished.size() * 100.0 + (finished.contains(currentDepot) ? 0 : currentPercent)) / depots;
    }
}
