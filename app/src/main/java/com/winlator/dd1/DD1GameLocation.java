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
// app can reach it. In the phone's storage it sits at DD1/game, where any file
// manager can open it - but every file the game opens there goes through
// Android's shared-storage layer, and a dungeon or the hamlet opens thousands.
// On an S22 and an S25+ a scene change took minutes, up to twenty (issue #4),
// against seconds inside the app. So the game stays inside the app, and what a
// person has to reach goes outside on its own: mods in DD1/mods, copied in, and
// replacement files and DLLs in DD1/dll, laid over the game at Play
// (DD1Overrides).
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

    // Inside the app, unless the app holds nothing and a whole game already sits
    // in DD1/game - a reinstall, or an install from 0.1.13 or 0.1.14 - which is
    // used where it is rather than downloaded again; the home screen then offers
    // to bring it in. Decided once and remembered, so a game deleted later does
    // not quietly change where the next one goes.
    public static String chosen(Context context) {
        String chosen = context.getSharedPreferences("dd1", Context.MODE_PRIVATE)
            .getString(KEY, null);
        if (chosen != null) return chosen;
        String decided = defaultFor(context.getFilesDir(), storageRoot());
        choose(context, decided);
        return decided;
    }

    static String defaultFor(File appFiles, File storage) {
        if (new File(appFiles, "game").exists() || new File(appFiles, "staging").exists())
            return APP;
        return DD1Game.validate(new File(storage, "game")).valid ? STORAGE : APP;
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

    // A whole game with every DLC is about 3.7 GB. Below this much free space
    // the move is not tried on its own: sizing the old copy first means walking
    // thousands of files through Android's storage layer, only to say no.
    public static final long ROOM_FOR_A_MOVE = 4L * 1024 * 1024 * 1024 + DD1Workshop.KEEP_FREE_BYTES;

    public static final class NoRoom extends IOException {
        public final long needed;

        NoRoom(long needed) {
            super("Not enough free space: moving the game needs " + (needed / (1024 * 1024)) + " MB");
            this.needed = needed;
        }
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
    //
    // The old copy is not removed here: once the new one is in place the caller
    // records the new location first, so a source that will not delete cleanly
    // cannot leave the launcher pointing at a half-removed game.
    static void move(File from, File to, Progress progress, Stop stop) throws IOException {
        File source = new File(from, "game");
        File target = new File(to, "game");
        if (!to.isDirectory() && !to.mkdirs()) throw new IOException("Cannot create " + to);

        if (!DD1Game.validate(target).valid && source.isDirectory()) {
            File side = new File(to, "game-moving");
            delete(side);
            if (!side.mkdirs()) throw new IOException("Cannot create " + side);
            noMedia(side);
            long total = size(source);
            long room = to.getUsableSpace();
            if (room < total + DD1Workshop.KEEP_FREE_BYTES)
                throw new NoRoom(total + DD1Workshop.KEEP_FREE_BYTES);
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
    }

    // What the move left behind at the old place: the game and the partial
    // downloads and imports that cannot follow it. In phone storage every file
    // removed is a round trip through Android's storage layer, and a game is
    // thousands of files: on an S22 this ran for minutes after the copy had
    // reached 100%, with nothing on screen to say the move was still going. So
    // each file removed is reported. The count runs up without a total: finding
    // the total would walk the same slow tree once more before removing a thing.
    static void clearSource(File from, Progress progress) throws IOException {
        long[] done = {0};
        for (String name : new String[] {"game", "staging", "workshop-staging", "local-import-staging"})
            delete(new File(from, name), done, progress);
        new File(from, DD1DlcVersions.FILE).delete();
    }

    // Media apps would otherwise index the game's art and sound as the owner's
    // pictures and music.
    static void hideFromMedia(File root) {
        File game = new File(root, "game");
        if (game.isDirectory()) noMedia(game);
    }

    // Also written into the folders a download fills, so art does not reach the
    // gallery while it arrives. Harmless inside the app, where nothing scans.
    static void noMedia(File dir) {
        try {
            new File(dir, ".nomedia").createNewFile();
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
        delete(file, new long[] {0}, (done, total) -> {});
    }

    private static void delete(File file, long[] done, Progress progress) throws IOException {
        if (!file.exists() && !java.nio.file.Files.isSymbolicLink(file.toPath())) return;
        if (file.isDirectory() && !java.nio.file.Files.isSymbolicLink(file.toPath())) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot read " + file);
            for (File child : children) delete(child, done, progress);
        }
        if (!file.delete()) throw new IOException("Cannot delete " + file);
        if (++done[0] % 50 == 0) progress.copied(done[0], 0);
    }
}
