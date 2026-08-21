package com.winlator.dd1;

import com.winlator.core.FileUtils;

import java.io.File;

public final class DD1Installer {
    private DD1Installer() {}

    private static final String COMPLETE_MARKER = "download-complete";
    private static final String ATTEMPT_MARKER = "download-started";

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

    // The downloader trusts whatever it finds on disk when it resumes: a depot
    // whose files are the right size is reported complete with zero bytes
    // transferred, so a file left half written by a killed process is accepted
    // for good and the install is quietly broken. Only a download that ended in
    // a working install may be built on; an interrupted one takes its staging
    // tree with it, at the cost of fetching the whole 4 GB again.
    public static File beginDownload(File filesDir) {
        File stagingRoot = new File(filesDir, "staging");
        File staging = new File(stagingRoot, "game");
        File attempt = new File(stagingRoot, ATTEMPT_MARKER);
        if (attempt.isFile()) {
            FileUtils.delete(staging);
            new File(stagingRoot, COMPLETE_MARKER).delete();
        }
        staging.mkdirs();
        try {
            attempt.createNewFile();
        }
        catch (java.io.IOException ignored) {}
        return staging;
    }

    // Puts one or more downloaded DLC into a game that is already installed.
    // activate() cannot do it: a staging tree holding a DLC has no launch
    // executable and it would rightly refuse the whole thing. Each DLC lands
    // whole or not at all, and one failing leaves the ones before it in place -
    // they are separate content.
    public static Result merge(File filesDir, java.util.Map<Integer, String> versions) {
        File staging = new File(filesDir, "staging/game/dlc");
        File installed = new File(filesDir, "game/dlc");
        for (java.util.Map.Entry<Integer, String> entry : versions.entrySet()) {
            File staged = folderOf(staging, entry.getKey());
            if (staged == null)
                return new Result(false, "Missing downloaded content for " + entry.getKey());
            // The downloader allocates every file before it fetches any content,
            // so a right-sized folder of zeros is what a download that never
            // delivered looks like.
            if (!hasContent(staged))
                return new Result(false, "Downloaded content for " + entry.getKey() + " is empty");

            installed.mkdirs();
            File target = new File(installed, staged.getName());
            FileUtils.delete(target);
            if (!staged.renameTo(target))
                return new Result(false, "Unable to install content for " + entry.getKey());
            DD1DlcVersions.record(filesDir, entry.getKey(), entry.getValue());
        }
        // The tree did its job, and leaving the attempt on record would make the
        // next download throw away a staging directory for no reason.
        FileUtils.delete(new File(filesDir, "staging/game"));
        new File(filesDir, "staging/" + ATTEMPT_MARKER).delete();
        new File(filesDir, "staging/" + COMPLETE_MARKER).delete();
        return new Result(true, null);
    }

    // Folders are named "<appid>_<title>" and the title is Steam's, not ours.
    private static File folderOf(File dlcDir, int appId) {
        File[] entries = dlcDir.listFiles();
        if (entries == null) return null;
        for (File entry : entries) {
            if (entry.isDirectory() && DlcInstallFilter.appIdOf(entry.getName()) == appId)
                return entry;
        }
        return null;
    }

    private static boolean hasContent(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return false;
            for (File child : children) {
                if (hasContent(child)) return true;
            }
            return false;
        }
        byte[] head = new byte[64];
        try (java.io.InputStream in = new java.io.FileInputStream(file)) {
            int read = in.read(head);
            for (int i = 0; i < read; i++) {
                if (head[i] != 0) return true;
            }
            return false;
        }
        catch (java.io.IOException unreadable) {
            return false;
        }
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
        FileUtils.delete(previous);
        if (active.exists() && !active.renameTo(previous))
            return new Result(false, "Unable to preserve installed game");
        if (!staging.renameTo(active)) {
            if (previous.exists()) previous.renameTo(active);
            return new Result(false, "Unable to activate downloaded game");
        }
        FileUtils.delete(previous);
        new File(stagingRoot, COMPLETE_MARKER).delete();
        new File(stagingRoot, ATTEMPT_MARKER).delete();
        return new Result(true, null);
    }

    // Frees the installed game and any interrupted download. The Wine prefix is
    // left alone because the saves live in it.
    public static boolean uninstall(File filesDir) {
        FileUtils.delete(new File(filesDir, "game"));
        FileUtils.delete(new File(filesDir, "staging"));
        return !new File(filesDir, "game").exists() && !new File(filesDir, "staging").exists();
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
