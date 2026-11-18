package com.winlator.dd1;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Where a save stands between this phone and Steam Cloud, for the light beside
// its name. Compared file by file on the SHA-1 the cloud listing already carries,
// so telling "the same" from "different" costs no download.
public final class DD1SaveSync {
    public enum State {
        SAME,       // green: both sides hold the same files
        DIFFERENT,  // orange: both sides, not the same - one needs the other
        CLOUD_ONLY, // yellow
        LOCAL_ONLY  // red: not backed up anywhere
    }

    private DD1SaveSync() {}

    // local: the slot's files here, named as in the cloud (profile_N/...).
    // cloud: the slot's files in the cloud listing (DD1SaveSlots.filesOf).
    public static State of(List<DD1SaveSummary.Entry> local, List<DD1SaveSummary.Entry> cloud) {
        boolean here = hasGame(local);
        boolean there = hasGame(cloud);
        if (here && !there) return State.LOCAL_ONLY;
        if (!here) return State.CLOUD_ONLY;
        return digests(local).equals(digests(cloud)) ? State.SAME : State.DIFFERENT;
    }

    // A slot is a save only with its game file; one holding nothing but the
    // tutorial record is not shown at all.
    public static boolean hasGame(List<DD1SaveSummary.Entry> files) {
        for (DD1SaveSummary.Entry file : files)
            if (file.path.toLowerCase(Locale.ROOT).endsWith("/persist.game.json") && file.length > 0)
                return true;
        return false;
    }

    // The newest file of a side: when it was last saved, as far as the files say.
    public static long newest(List<DD1SaveSummary.Entry> files) {
        long newest = 0;
        for (DD1SaveSummary.Entry file : files) newest = Math.max(newest, file.modifiedMillis);
        return newest;
    }

    private static Map<String, String> digests(List<DD1SaveSummary.Entry> files) {
        Map<String, String> digests = new HashMap<>();
        for (DD1SaveSummary.Entry file : files)
            digests.put(file.path.toLowerCase(Locale.ROOT),
                file.sha1 == null ? "" : file.sha1.toLowerCase(Locale.ROOT));
        return digests;
    }
}
