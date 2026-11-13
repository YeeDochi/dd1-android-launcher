package com.winlator.dd1;

import com.winlator.core.FileUtils;

import java.io.File;

public final class DD1Installer {
    private DD1Installer() {}

    private static final String COMPLETE_MARKER = "download-complete";
    private static final String ATTEMPT_MARKER = "download-started";
    // JavaSteam's DepotDownloader.CONFIG_DIR plus the file its DepotConfigStore
    // keeps: depot id -> manifest id of what it believes is fully on disk.
    static final String DEPOT_RECORD = ".DepotDownloader/depot.config";

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

    // An interrupted download is picked up where it stopped, not thrown away.
    // Throwing it away is what made a slow or flaky connection hopeless: every
    // stall started the 4 GB over, so the data went and the bar never moved.
    //
    // What the downloader trusts on resume is the depot record it keeps in the
    // tree. A depot it never finished is already marked as unknown there, and an
    // unknown depot has every chunk of every file checked against the manifest's
    // checksum - a file of zeros or one cut off halfway fails that and is fetched
    // again. A depot it did finish is taken on its word, by file hash alone. The
    // record is dropped here so that no depot is taken on its word: the check is
    // a read of what is already on the phone, which costs minutes, not data.
    public static File beginDownload(File filesDir) {
        File stagingRoot = new File(filesDir, "staging");
        File staging = new File(stagingRoot, "game");
        File attempt = new File(stagingRoot, ATTEMPT_MARKER);
        if (attempt.isFile()) {
            new File(staging, DEPOT_RECORD).delete();
            new File(stagingRoot, COMPLETE_MARKER).delete();
        }
        staging.mkdirs();
        try {
            attempt.createNewFile();
        }
        catch (java.io.IOException ignored) {}
        return staging;
    }

    // A repair downloads into the installed game itself. With the depot record
    // gone the downloader checks every chunk of every file it ships against the
    // manifest and fetches the ones that differ, and it has no record of a file
    // it never shipped, so a mod or a dropped-in DLL is not its business.
    public static File beginRepair(File filesDir) {
        File game = new File(filesDir, "game");
        new File(game, DEPOT_RECORD).delete();
        return game;
    }

    public static Result checkRepaired(File filesDir) {
        DD1Game.Validation validation = DD1Game.validate(new File(filesDir, "game"));
        return validation.valid ? new Result(true, null)
            : new Result(false, "Missing " + validation.missingPath);
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
        carryOver(previous, active, "mods");
        carryOver(previous, active, "mods-disabled");
        FileUtils.delete(previous);
        new File(stagingRoot, COMPLETE_MARKER).delete();
        new File(stagingRoot, ATTEMPT_MARKER).delete();
        return new Result(true, null);
    }

    // Mods live inside the game tree, so replacing the tree used to delete them:
    // every Workshop mod was fetched again from the start and every local one
    // copied in again, and a reinstall or a game update looked like the mod
    // manager forgetting everything. Each mod the old tree had and the new one
    // does not is moved across; anything the download itself brought wins.
    static void carryOver(File previous, File active, String folder) {
        File from = new File(previous, folder);
        File[] mods = from.listFiles();
        if (mods == null) return;
        File to = new File(active, folder);
        if (!to.isDirectory() && !to.mkdirs()) return;
        for (File mod : mods) {
            File target = new File(to, mod.getName());
            if (!target.exists()) mod.renameTo(target);
        }
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
