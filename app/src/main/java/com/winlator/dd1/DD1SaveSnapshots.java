package com.winlator.dd1;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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
        delete(target);
        if (!target.mkdirs()) return null;
        if (!copy(root, target)) {
            // A half-copied snapshot is worse than none: it would be restored one
            // day and look like a save.
            delete(target);
            return null;
        }
        prune(filesDir);
        return target;
    }

    public static List<File> kept(File filesDir) {
        List<File> snapshots = new ArrayList<>();
        File[] entries = dir(filesDir).listFiles();
        if (entries == null) return snapshots;
        for (File entry : entries) {
            if (entry.isDirectory() && takenAt(entry) > 0) snapshots.add(entry);
        }
        Collections.sort(snapshots, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return Long.compare(takenAt(right), takenAt(left));
            }
        });
        return snapshots;
    }

    private static void prune(File filesDir) {
        List<File> snapshots = kept(filesDir);
        for (int i = KEPT; i < snapshots.size(); i++) delete(snapshots.get(i));
    }

    private static long takenAt(File snapshot) {
        try {
            return Long.parseLong(snapshot.getName());
        }
        catch (NumberFormatException notASnapshot) {
            return -1;
        }
    }

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
        byte[] buffer = new byte[8192];
        try (InputStream in = new FileInputStream(from);
             OutputStream out = new FileOutputStream(to)) {
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return true;
        }
        catch (IOException failed) {
            return false;
        }
    }

    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }
}
