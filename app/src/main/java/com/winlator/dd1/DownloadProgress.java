package com.winlator.dd1;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The downloader reports progress for the depot it is working on, and nothing
// about the whole job, so that is what is shown. Guessing an overall figure made
// a resumed download open at 88% and stall there.
public final class DownloadProgress {
    private static final Pattern MANIFEST =
        Pattern.compile("depot\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

    private final Set<Integer> finished = new LinkedHashSet<>();
    private final Set<Integer> known = new LinkedHashSet<>();
    private int currentDepot;
    private double currentPercent = -1;

    public static double percent(float reported) {
        double value = reported <= 1.0 ? reported * 100.0 : reported;
        return Math.max(0.0, Math.min(100.0, value));
    }

    // The downloader fetches every manifest before it fetches any content, so
    // its status lines are the only place the part count appears in advance.
    public void onStatus(String message) {
        if (message == null) return;
        Matcher matcher = MANIFEST.matcher(message);
        if (matcher.find()) known.add(Integer.parseInt(matcher.group(1)));
    }

    public void onDepotProgress(int depotId, float reported) {
        if (depotId > 0) known.add(depotId);
        if (depotId > 0 && depotId != currentDepot) {
            currentDepot = depotId;
            currentPercent = -1;
        }
        if (reported > 0) currentPercent = percent(reported);
    }

    public void onDepotFinished(int depotId) {
        if (depotId > 0) {
            finished.add(depotId);
            known.add(depotId);
        }
        currentPercent = -1;
    }

    public int currentDepot() {
        return currentDepot;
    }

    public int finishedCount() {
        return finished.size();
    }

    // "3/9" once the manifests have been seen. The part number never runs past
    // the total, which the manifest lines settle before any content arrives.
    public String part() {
        int total = known.size();
        int current = Math.min(finished.size() + 1, Math.max(total, 1));
        return total > 0 ? current + "/" + total : String.valueOf(current);
    }

    // Negative until the current depot has reported something.
    public double currentPercent() {
        return currentPercent;
    }
}
