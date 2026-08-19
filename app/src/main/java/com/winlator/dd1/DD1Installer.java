package com.winlator.dd1;

import java.io.File;

public final class DD1Installer {
    private DD1Installer() {}

    private static final String COMPLETE_MARKER = "download-complete";

    // The downloader writes this once it has handed over every depot. Without it
    // a staging tree is a download that stopped partway, and promoting that gives
    // the player a broken install that still looks valid.
    public static void markDownloadComplete(File filesDir) {
        try {
            new File(filesDir, "staging").mkdirs();
            new File(filesDir, "staging/" + COMPLETE_MARKER).createNewFile();
        }
        catch (java.io.IOException ignored) {}
    }

    public static Result activate(File filesDir) {
        File stagingRoot = new File(filesDir, "staging");
        File staging = new File(stagingRoot, "game");
        if (!new File(stagingRoot, COMPLETE_MARKER).isFile())
            return new Result(false, "Download did not finish");
        DD1Game.Validation validation = DD1Game.validate(staging);
        if (!validation.valid) return new Result(false, "Missing " + validation.missingPath);

        File active = new File(filesDir, "game");
        File previous = new File(stagingRoot, "previous-game");
        delete(previous);
        if (active.exists() && !active.renameTo(previous))
            return new Result(false, "Unable to preserve installed game");
        if (!staging.renameTo(active)) {
            if (previous.exists()) previous.renameTo(active);
            return new Result(false, "Unable to activate downloaded game");
        }
        delete(previous);
        new File(stagingRoot, COMPLETE_MARKER).delete();
        return new Result(true, null);
    }

    // Frees the installed game and any interrupted download. The Wine prefix is
    // left alone because the saves live in it.
    public static boolean uninstall(File filesDir) {
        delete(new File(filesDir, "game"));
        delete(new File(filesDir, "staging"));
        return !new File(filesDir, "game").exists() && !new File(filesDir, "staging").exists();
    }

    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        if (file.exists()) file.delete();
    }

    public static final class Result {
        public final boolean success;
        public final String error;

        private Result(boolean success, String error) {
            this.success = success;
            this.error = error;
        }
    }
}
