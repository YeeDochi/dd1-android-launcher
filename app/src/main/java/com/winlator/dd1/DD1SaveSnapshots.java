package com.winlator.dd1;

import com.winlator.core.FileUtils;
import com.winlator.core.StreamUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// A copy of the saves taken before anything is allowed to change them. Three are
// kept, because the point is to be able to go back and not to hoard.
public final class DD1SaveSnapshots {
    private DD1SaveSnapshots() {}

    public static final int KEPT = 3;

    private static File dir(File filesDir) {
        return new File(filesDir, "snapshots/saves");
    }

    public static File take(File filesDir, long atMillis) {
        File root = DD1Saves.root(filesDir);
        if (!DD1Saves.isSaveTree(root)) return null;

        File target = new File(dir(filesDir), Long.toString(atMillis));
        FileUtils.delete(target);
        if (!target.mkdirs()) return null;
        if (!copy(root, target)) {
            // A half-copied snapshot is worse than none: it would be restored one
            // day and look like a save.
            FileUtils.delete(target);
            return null;
        }
        prune(filesDir);
        return target;
    }

    // The other half of the promise: a snapshot nobody can go back to is
    // decoration. A snapshot is the whole save tree, so restoring one is a whole
    // tree too. What it replaces is snapshotted first, which makes going back
    // itself reversible.
    public static boolean restore(File filesDir, File snapshot) {
        if (!DD1Saves.isSaveTree(snapshot)) return false;
        // Only when the tree has moved since the last snapshot. Three is all the
        // ring holds, so going back twice must not spend two of them on states
        // that were already kept.
        if (DD1SavePolicy.worthTaking(filesDir)
                && take(filesDir, System.currentTimeMillis()) == null)
            return false;

        File root = DD1Saves.root(filesDir);
        File spare = new File(dir(filesDir), "restoring");
        FileUtils.delete(spare);
        // Copied aside first: a copy that fails part way must not have eaten the
        // tree it was replacing.
        if (!spare.mkdirs() || !copy(snapshot, spare)) {
            FileUtils.delete(spare);
            return false;
        }
        FileUtils.delete(root);
        if (root.getParentFile() != null) root.getParentFile().mkdirs();
        if (spare.renameTo(root)) return true;

        // The rename is a rename within one directory and should not fail, but if
        // it does the tree is already gone, so put it back the slow way rather
        // than leave the player with nothing.
        root.mkdirs();
        boolean recovered = copy(spare, root);
        FileUtils.delete(spare);
        return recovered;
    }

    // The keepsake, as against the ring: one whole copy of the saves in a folder
    // a file manager can reach, kept until the owner deletes it. Nothing prunes
    // it and nothing restores it from here - putting files back is the owner's
    // own act, with the game closed.
    public static boolean export(File filesDir, File destination) {
        File root = DD1Saves.root(filesDir);
        if (!DD1Saves.isSaveTree(root)) return false;
        FileUtils.delete(destination);
        if (!destination.mkdirs()) return false;
        if (copy(root, destination)) return true;
        // A half-copied backup is worse than none: it would be copied back one
        // day and look like a save.
        FileUtils.delete(destination);
        return false;
    }

    public static final int MIRRORED = 3;

    // A folder name that says when, and sorts as text in the order it happened.
    static String stamp(long atMillis) {
        return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
            .format(new Date(atMillis));
    }

    // The ring holds what the saves were before the launcher changed them, so
    // even its newest entry is a play session old. The mirror holds what they
    // are: whenever the live tree has moved, one copy of it goes outside the
    // app, and only the newest MIRRORED stay. Deleting the app takes the ring
    // with it; the mirror is what survives that.
    // The answer is a sentence for the caller to log, because the quietest
    // failure here - no storage permission - must not look like the mirror
    // never ran. This class stays clear of android.util.Log so the tests can
    // run it as plain Java.
    public static String mirror(File filesDir, File mirrorRoot, long atMillis) {
        File root = DD1Saves.root(filesDir);
        if (!DD1Saves.isSaveTree(root)) return "Nothing to mirror: no save tree";
        List<File> copies = mirrored(mirrorRoot);
        if (!copies.isEmpty() && DD1SaveSummary.changed(
                DD1SaveSummary.of(copies.get(0)), DD1SaveSummary.of(root)).isEmpty())
            return "Unchanged since " + copies.get(0).getName();
        File destination = new File(mirrorRoot, stamp(atMillis));
        if (!export(filesDir, destination))
            return "Could not write " + destination + " (storage permission?)";
        copies = mirrored(mirrorRoot);
        for (int i = MIRRORED; i < copies.size(); i++) FileUtils.delete(copies.get(i));
        return "Mirrored to " + destination;
    }

    public static List<File> mirrored(File mirrorRoot) {
        List<File> copies = new ArrayList<>();
        File[] entries = mirrorRoot.listFiles();
        if (entries == null) return copies;
        for (File entry : entries) {
            if (entry.isDirectory()) copies.add(entry);
        }
        copies.sort((left, right) -> right.getName().compareTo(left.getName()));
        return copies;
    }

    public static List<File> kept(File filesDir) {
        List<File> snapshots = new ArrayList<>();
        File[] entries = dir(filesDir).listFiles();
        if (entries == null) return snapshots;
        for (File entry : entries) {
            if (entry.isDirectory() && takenAt(entry) > 0) snapshots.add(entry);
        }
        snapshots.sort((left, right) -> Long.compare(takenAt(right), takenAt(left)));
        return snapshots;
    }

    private static void prune(File filesDir) {
        List<File> snapshots = kept(filesDir);
        for (int i = KEPT; i < snapshots.size(); i++) FileUtils.delete(snapshots.get(i));
    }

    private static long takenAt(File snapshot) {
        try {
            return Long.parseLong(snapshot.getName());
        }
        catch (NumberFormatException notASnapshot) {
            return -1;
        }
    }

    // Not FileUtils.copy: it answers true for a directory it could not read and
    // skips symbolic links, and restore() deletes the live tree on that answer.
    private static boolean copy(File from, File to) {
        File[] children = from.listFiles();
        if (children == null) return false;
        for (File child : children) {
            File target = new File(to, child.getName());
            if (child.isDirectory()) {
                if (!target.mkdirs() || !copy(child, target)) return false;
            }
            else if (!copyFile(child, target)) return false;
        }
        return true;
    }

    private static boolean copyFile(File from, File to) {
        try (InputStream in = new FileInputStream(from);
             OutputStream out = new FileOutputStream(to)) {
            return StreamUtils.copy(in, out);
        }
        catch (IOException failed) {
            return false;
        }
    }
}
