package com.winlator.dd1;

import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Files a person wants laid over the game: a proxy DLL beside Darkest.exe, an
// edited script, a replaced sound bank. The game stays inside the app, where it
// loads in seconds instead of minutes (issue #4), and DD1/overrides mirrors its
// tree - DD1/overrides/_windows/win64/foo.dll lands at _windows/win64/foo.dll.
//
// Applied at Play. A file that replaces one of the game's is kept aside first
// and put back when the override leaves the folder; a file the game never had is
// simply removed again. What has been laid down is recorded inside the game
// tree, so the record and the originals move and are deleted with the game.
//
// Names are matched to the game's own without regard to case: Windows ignores
// it and Wine follows suit, so darkest.exe in the folder is Darkest.exe in the
// game, and copying it under its own spelling would leave two files Wine cannot
// tell apart.
public final class DD1Overrides {
    public static final String STATE = ".dd1-overrides";
    private static final String RECORD = "applied.txt";
    private static final String ORIGINALS = "originals";

    private DD1Overrides() {}

    public static File folder() {
        return new File(Environment.getExternalStorageDirectory(), "DD1/overrides");
    }

    // Made on first open, and kept out of the gallery like the game's own art.
    public static void open(android.content.Context context) {
        File overrides = folder();
        if (!overrides.isDirectory()) overrides.mkdirs();
        DD1GameLocation.noMedia(overrides);
        DD1FileManager.open(context, overrides);
    }

    public static final class Result {
        public final List<String> applied = new ArrayList<>();
        public final List<String> restored = new ArrayList<>();
        public final List<String> skipped = new ArrayList<>();
        // Taken out of the folder, but the game's own copy kept aside is gone, so
        // the file was removed and the game's file check has to bring it back.
        public final List<String> lost = new ArrayList<>();

        public boolean changed() {
            return !applied.isEmpty() || !restored.isEmpty() || !lost.isEmpty();
        }
    }

    private static final class Entry {
        long sourceSize;
        long sourceTime;
        long targetSize;
        long targetTime;
        boolean original;
    }

    // An absent folder means no overrides, and everything laid down before is
    // undone. A folder that is there but cannot be listed - storage access taken
    // back - is an error, not an empty folder: reading it as empty would strip
    // every DLL a person added the moment the permission lapsed.
    //
    // The record is written however this ends. An original is moved aside before
    // its override is copied, so a run that stops halfway - a full disk, a file
    // that will not copy - must still say what it moved; read without that, the
    // next run would take the override for the game's own file and keep it over
    // the real one.
    public static Result apply(File overrides, File gameDir) throws IOException {
        Result result = new Result();
        File state = new File(gameDir, STATE);
        Map<String, Entry> record = read(new File(state, RECORD));
        if (record.isEmpty() && !overrides.isDirectory()) return result;

        Map<String, File> sources = new LinkedHashMap<>();
        if (overrides.isDirectory()) collect(overrides, "", sources);

        boolean dirty = false;
        try {
            Set<String> seen = new HashSet<>();
            for (Map.Entry<String, File> each : sources.entrySet()) {
                String path = resolve(gameDir, each.getKey());
                File source = each.getValue();
                File target = new File(gameDir, path);
                if (target.isDirectory() || path.equals(STATE) || path.startsWith(STATE + "/")
                        || blockedByFile(gameDir, path)) {
                    result.skipped.add(each.getKey());
                    continue;
                }
                seen.add(path);
                Entry entry = record.get(path);
                boolean ours = entry != null && target.isFile()
                    && target.length() == entry.targetSize
                    && target.lastModified() == entry.targetTime;
                if (ours && entry.sourceSize == source.length()
                        && entry.sourceTime == source.lastModified()) continue;
                File kept = new File(state, ORIGINALS + "/" + path);
                if (entry == null) {
                    entry = new Entry();
                    entry.original = target.isFile();
                    if (entry.original) keep(target, kept);
                }
                // Something else wrote the file since the override went down - a
                // repair, a game or DLC update. That is the game's file now, and
                // the one to give back when the override leaves.
                else if (!ours && target.isFile()) {
                    keep(target, kept);
                    entry.original = true;
                }
                // Recorded before the copy: the original is already aside.
                entry.targetSize = -1;
                record.put(path, entry);
                dirty = true;
                copy(source, target);
                entry.sourceSize = source.length();
                entry.sourceTime = source.lastModified();
                entry.targetSize = target.length();
                entry.targetTime = target.lastModified();
                result.applied.add(path);
            }

            for (String path : new ArrayList<>(record.keySet())) {
                if (seen.contains(path)) continue;
                Entry entry = record.get(path);
                File target = new File(gameDir, path);
                File kept = new File(state, ORIGINALS + "/" + path);
                if (entry.original && kept.isFile()) {
                    keep(kept, target);
                    result.restored.add(path);
                }
                else {
                    if (target.isFile() && !target.delete())
                        throw new IOException("Cannot remove " + path);
                    (entry.original ? result.lost : result.restored).add(path);
                }
                record.remove(path);
                dirty = true;
            }
        }
        finally {
            if (dirty) write(new File(state, RECORD), record);
        }
        return result;
    }

    // DD1/overrides/Darkest.exe/x.dll asks for a folder where the game has a
    // file; there is no such place to put it.
    private static boolean blockedByFile(File gameDir, String path) {
        File parent = new File(gameDir, path).getParentFile();
        while (parent != null && !parent.equals(gameDir)) {
            if (parent.isFile()) return true;
            parent = parent.getParentFile();
        }
        return false;
    }

    private static void collect(File dir, String prefix, Map<String, File> into)
            throws IOException {
        File[] children = dir.listFiles();
        if (children == null) throw new IOException("Cannot read " + dir);
        for (File child : children) {
            String name = child.getName();
            if (name.startsWith(".") || Files.isSymbolicLink(child.toPath())) continue;
            String path = prefix + name;
            if (child.isDirectory()) collect(child, path + "/", into);
            else if (child.isFile()) into.put(path, child);
        }
    }

    // Each step takes the game's spelling where an entry by that name exists in
    // any case, and the folder's own where nothing does yet.
    static String resolve(File gameDir, String path) {
        StringBuilder resolved = new StringBuilder();
        File current = gameDir;
        for (String part : path.split("/")) {
            String name = part;
            String[] names = current.list();
            if (names != null) {
                for (String existing : names) {
                    if (existing.equals(part)) {
                        name = existing;
                        break;
                    }
                    if (existing.equalsIgnoreCase(part)) name = existing;
                }
            }
            if (resolved.length() > 0) resolved.append('/');
            resolved.append(name);
            current = new File(current, name);
        }
        return resolved.toString();
    }

    // A rename where it can be one - the original and the game share a mount -
    // and a copy where it cannot.
    private static void keep(File from, File to) throws IOException {
        File parent = to.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IOException("Cannot create " + parent);
        if (to.exists() && !to.delete()) throw new IOException("Cannot replace " + to);
        if (from.renameTo(to)) return;
        copy(from, to);
        if (!from.delete()) throw new IOException("Cannot remove " + from);
    }

    // Through a side name, so a copy cut short leaves the game's file whole
    // rather than half overwritten.
    private static void copy(File from, File to) throws IOException {
        File parent = to.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IOException("Cannot create " + parent);
        File side = new File(parent, ".dd1-override-" + to.getName());
        try (InputStream in = new FileInputStream(from);
             OutputStream out = new FileOutputStream(side)) {
            byte[] buffer = new byte[256 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
        catch (IOException error) {
            side.delete();
            throw error;
        }
        if (to.exists() && !to.delete()) {
            side.delete();
            throw new IOException("Cannot replace " + to);
        }
        if (!side.renameTo(to)) {
            side.delete();
            throw new IOException("Cannot put " + to);
        }
    }

    private static Map<String, Entry> read(File file) throws IOException {
        Map<String, Entry> record = new LinkedHashMap<>();
        if (!file.isFile()) return record;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\t");
                if (fields.length != 6) continue;
                try {
                    Entry entry = new Entry();
                    entry.sourceSize = Long.parseLong(fields[1]);
                    entry.sourceTime = Long.parseLong(fields[2]);
                    entry.targetSize = Long.parseLong(fields[3]);
                    entry.targetTime = Long.parseLong(fields[4]);
                    entry.original = "1".equals(fields[5]);
                    record.put(fields[0], entry);
                }
                catch (NumberFormatException damaged) {
                    // A line that does not read is dropped, as if never written.
                }
            }
        }
        return record;
    }

    private static void write(File file, Map<String, Entry> record) throws IOException {
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Entry> each : record.entrySet()) {
            Entry entry = each.getValue();
            text.append(each.getKey()).append('\t').append(entry.sourceSize).append('\t')
                .append(entry.sourceTime).append('\t').append(entry.targetSize).append('\t')
                .append(entry.targetTime).append('\t').append(entry.original ? '1' : '0')
                .append('\n');
        }
        File side = new File(parent, RECORD + ".new");
        try (OutputStream out = new FileOutputStream(side)) {
            out.write(text.toString().getBytes(StandardCharsets.UTF_8));
        }
        if (file.exists() && !file.delete()) throw new IOException("Cannot replace " + file);
        if (!side.renameTo(file)) throw new IOException("Cannot write " + file);
    }
}
