package com.winlator.dd1;

import com.winlator.core.FileUtils;

import java.io.File;
import java.util.function.IntFunction;

// A profile the runtime could not finish making leaves the launcher unable to
// start at all. ContainerManager reads every xuser-N directory's .container and
// hands the bytes to new String(...); a directory without one answers null there,
// which is a NullPointerException out of onResume, on every launch, for good.
//
// The gap that makes one is in the creation itself: the directory is made, a tar
// is unpacked into it, and only then is the config written. A process killed in
// that window - which is most of a first run, and most likely while a download is
// competing for the device - leaves the directory behind.
//
// So before anything reads them, every profile directory is made readable again.
// What is inside decides how: a tree the game has saved into keeps everything and
// is given a config back, and one with nothing to lose goes, because it was never
// finished. The config text is asked for rather than built here, so this stays
// file work that can be tested without a device - and so the GPU is only asked
// about when there is actually something to repair.
public abstract class DD1ProfileRepair {
    private DD1ProfileRepair() {}

    private static final String PREFIX = "xuser-";

    public static void repair(File filesDir, IntFunction<String> configForId) {
        File[] entries = new File(filesDir, "rootfs/home").listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            int id = idOf(entry);
            if (id < 0 || !entry.isDirectory()) continue;
            File config = new File(entry, ".container");
            if (config.isFile() && config.length() > 0) continue;
            if (DD1Saves.isSaveTree(DD1Saves.saveTreeIn(entry)))
                FileUtils.writeString(config, configForId.apply(id));
            else FileUtils.delete(entry);
        }
    }

    private static int idOf(File entry) {
        String name = entry.getName();
        if (!name.startsWith(PREFIX)) return -1;
        try {
            return Integer.parseInt(name.substring(PREFIX.length()));
        }
        catch (NumberFormatException notAProfile) {
            return -1;
        }
    }
}
