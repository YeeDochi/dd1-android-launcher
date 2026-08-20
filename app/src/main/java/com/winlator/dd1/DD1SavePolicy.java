package com.winlator.dd1;

import java.io.File;
import java.util.List;

// Whether a snapshot would say anything the ones already kept do not. Three
// slots are all there are, so a launch that changed nothing must not spend one -
// three of those in a row would push out the state worth going back to.
public final class DD1SavePolicy {
    private DD1SavePolicy() {}

    public static boolean worthTaking(File filesDir) {
        File root = DD1Saves.root(filesDir);
        if (!DD1Saves.isSaveTree(root)) return false;
        List<DD1SaveSummary.Entry> now = DD1SaveSummary.of(root);
        for (File snapshot : DD1SaveSnapshots.kept(filesDir)) {
            if (DD1SaveSummary.changed(DD1SaveSummary.of(snapshot), now).isEmpty()) return false;
        }
        return true;
    }
}
