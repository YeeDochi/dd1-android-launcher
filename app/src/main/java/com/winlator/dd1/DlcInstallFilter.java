package com.winlator.dd1;

import java.io.File;
import java.util.Collection;

// Steam ships the DLC alongside the game, so the user's choice is applied to the
// downloaded tree before it becomes the installed one.
public final class DlcInstallFilter {
    private DlcInstallFilter() {}

    public static void apply(File gameDir, Collection<Integer> selected) {
        File[] entries = new File(gameDir, "dlc").listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            int appId = appIdOf(entry.getName());
            if (appId > 0 && !selected.contains(appId)) delete(entry);
        }
    }

    // Which DLC is on disk. The folder name is the only record of it, so the
    // screen that offers to remove any of it has to read them back.
    public static java.util.List<Integer> installed(File gameDir) {
        java.util.List<Integer> found = new java.util.ArrayList<>();
        File[] entries = new File(gameDir, "dlc").listFiles();
        if (entries == null) return found;
        for (File entry : entries) {
            int appId = appIdOf(entry.getName());
            if (appId > 0) found.add(appId);
        }
        return found;
    }

    // Folders are named "<appid>_<title>"; anything else is not ours to remove.
    private static int appIdOf(String name) {
        int separator = name.indexOf('_');
        if (separator <= 0) return -1;
        try {
            return Integer.parseInt(name.substring(0, separator));
        }
        catch (NumberFormatException notAnId) {
            return -1;
        }
    }

    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }
}
