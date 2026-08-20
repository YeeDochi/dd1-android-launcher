package com.winlator.dd1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Which way the saves should move, or that nobody but the player can say. The
// last synced state is what makes the question answerable: without it, two
// different saves are just two different saves.
public final class DD1CloudPlan {
    public enum Action { NOTHING, UPLOAD, DOWNLOAD, CONFLICT, LOCAL_ONLY }

    private final Action action;
    private final List<String> paths;

    private DD1CloudPlan(Action action, List<String> paths) {
        this.action = action;
        this.paths = Collections.unmodifiableList(new ArrayList<>(paths));
    }

    public Action action() {
        return action;
    }

    public List<String> paths() {
        return paths;
    }

    public static DD1CloudPlan between(List<DD1SaveSummary.Entry> local,
            DD1CloudListing cloud, List<DD1SaveSummary.Entry> lastSynced) {
        // Not knowing is not the same as nothing being there, and acting on the
        // difference is how progress gets overwritten.
        if (!cloud.known()) return new DD1CloudPlan(Action.LOCAL_ONLY,
            Collections.<String>emptyList());

        List<String> localMoved = DD1SaveSummary.changed(lastSynced, local);
        List<String> cloudMoved = DD1SaveSummary.changed(lastSynced, cloud.files());
        if (localMoved.isEmpty() && cloudMoved.isEmpty())
            return new DD1CloudPlan(Action.NOTHING, Collections.<String>emptyList());
        if (cloudMoved.isEmpty()) return new DD1CloudPlan(Action.UPLOAD, localMoved);
        if (localMoved.isEmpty()) return new DD1CloudPlan(Action.DOWNLOAD, cloudMoved);

        // Both sides moved. There is no answer here that is not a guess, and a
        // guess costs somebody a campaign.
        List<String> both = new ArrayList<>(localMoved);
        for (String path : cloudMoved) {
            if (!both.contains(path)) both.add(path);
        }
        Collections.sort(both);
        return new DD1CloudPlan(Action.CONFLICT, both);
    }
}
