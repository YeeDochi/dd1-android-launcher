package com.winlator.dd1;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// Where the game lives. Inside the app it is safe and fast, and nothing but the
// app can reach it - which is the complaint: Darkest Dungeon is modded by
// editing its files, and a DLL or a patched script has to go where the game
// is. In the phone's storage it sits at DD1/game, where any file manager can
// open it, it outlives the app being removed, and the build that installs
// beside Winlator can use the same copy instead of downloading 4 GB again.
//
// What moves is everything that describes the install: the game tree, the
// download and import staging beside it (a rename cannot cross between the two
// places, so staging has to be on the same side as what it is renamed into),
// and the record of DLC versions. Saves stay in the Wine prefix.
public final class DD1GameLocation {
    public static final String APP = "app";
    public static final String STORAGE = "storage";
    private static final String KEY = "game_location";

    private DD1GameLocation() {}

    public static String chosen(Context context) {
        return context.getSharedPreferences("dd1", Context.MODE_PRIVATE)
            .getString(KEY, APP);
    }

    static void choose(Context context, String location) {
        context.getSharedPreferences("dd1", Context.MODE_PRIVATE).edit()
            .putString(KEY, location).commit();
    }

    // The directory that holds game/, staging/ and the rest, for the place
    // chosen. Everything that used to take the app's files directory for these
    // takes this instead.
    public static File root(Context context) {
        return rootFor(context, chosen(context));
    }

    public static File rootFor(Context context, String location) {
        return STORAGE.equals(location) ? storageRoot() : context.getFilesDir();
    }

    public static File storageRoot() {
        return new File(Environment.getExternalStorageDirectory(), "DD1");
    }

    public static File gameDir(Context context) {
        return new File(root(context), "game");
    }

    public interface Progress {
        void copied(long bytes, long total);
    }

    public interface Stop {
        boolean requested();
    }

    // Moves the install from one root to the other. A copy, not a rename: app
    // data and shared storage are different mounts. The copy goes to a side name
    // first, so a move that is stopped or runs out of room leaves the old install
    // as it was and nothing half-copied where the game is looked for.
    //
    // A game already at the destination - put there by the other build, or by a
    // move that finished copying and was interrupted after - is adopted rather
    // than copied over.
    static void move(File from, File to, Progress progress, Stop stop) throws IOException {
        File source = new File(from, "game");
        File target = new File(to, "game");
        if (!to.isDirectory() && !to.mkdirs()) throw new IOException("Cannot create " + to);

        if (!DD1Game.validate(target).valid && source.isDirectory()) {
            File side = new File(to, "game-moving");
            delete(side);
            long total = size(source);
            long room = to.getUsableSpace();
            if (room < total + DD1Workshop.KEEP_FREE_BYTES)
                throw new IOException("Not enough free space: moving the game needs "
                    + (total / (1024 * 1024)) + " MB");
            long[] done = {0};
            try {
                copy(source, side, done, total, progress, stop);
            }
            catch (IOException | RuntimeException error) {
                delete(side);
                throw error;
            }
            if (stop.requested()) {
                delete(side);
                return;
            }
            delete(target);
            if (!side.renameTo(target)) throw new IOException("Cannot put the game in " + to);
        }
        copyFile(new File(from, DD1DlcVersions.FILE), new File(to, DD1DlcVersions.FILE));

        delete(source);
        for (String name : new String[] {"staging", "workshop-staging", "local-import-staging"})
            delete(new File(from, name));
        new File(from, DD1DlcVersions.FILE).delete();
    }

    // Media apps would otherwise index the game's art and sound as the owner's
    // pictures and music.
    static void hideFromMedia(File root) {
        File game = new File(root, "game");
        if (!game.isDirectory()) return;
        try {
            new File(game, ".nomedia").createNewFile();
        }
        catch (IOException ignored) {}
    }

    private static long size(File file) {
        if (file.isFile()) return file.length();
        long total = 0;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) total += size(child);
        return total;
    }

    private static void copy(File from, File to, long[] done, long total, Progress progress,
            Stop stop) throws IOException {
        if (stop.requested()) return;
        if (from.isDirectory()) {
            if (!to.isDirectory() && !to.mkdirs()) throw new IOException("Cannot create " + to);
            File[] children = from.listFiles();
            if (children == null) throw new IOException("Cannot read " + from);
            for (File child : children) copy(child, new File(to, child.getName()), done, total,
                progress, stop);
            return;
        }
        byte[] buffer = new byte[256 * 1024];
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                done[0] += read;
            }
        }
        progress.copied(done[0], total);
    }

    private static void copyFile(File from, File to) throws IOException {
        if (!from.isFile()) return;
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
    }

    static void delete(File file) throws IOException {
        if (!file.exists() && !java.nio.file.Files.isSymbolicLink(file.toPath())) return;
        if (file.isDirectory() && !java.nio.file.Files.isSymbolicLink(file.toPath())) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot read " + file);
            for (File child : children) delete(child);
        }
        if (!file.delete()) throw new IOException("Cannot delete " + file);
    }
}
