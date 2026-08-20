package com.winlator.dd1;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

// What a save tree looks like, in enough detail to tell one state from another
// without reading the files again. The digest is what decides, not the
// modification time: syncing on timestamps is how other launchers have lost
// saves.
public final class DD1SaveSummary {
    private DD1SaveSummary() {}

    // Steam's per-file limit. Anything larger is not a save Steam will hold, and
    // sending it would fail the batch it travelled in.
    private static final long MAX_BYTES = 100L * 1024 * 1024;

    public static final class Entry {
        public final String path;
        public final long length;
        public final long modifiedMillis;
        public final String sha1;

        public Entry(String path, long length, long modifiedMillis, String sha1) {
            this.path = path;
            this.length = length;
            this.modifiedMillis = modifiedMillis;
            this.sha1 = sha1;
        }
    }

    public static List<Entry> of(File root) {
        List<Entry> entries = new ArrayList<>();
        collect(root, "", entries);
        Collections.sort(entries, new java.util.Comparator<Entry>() {
            @Override
            public int compare(Entry left, Entry right) {
                return left.path.compareTo(right.path);
            }
        });
        return entries;
    }

    private static void collect(File file, String prefix, List<Entry> entries) {
        File[] children = file.listFiles();
        if (children == null) return;
        for (File child : children) {
            String path = prefix.isEmpty() ? child.getName() : prefix + "/" + child.getName();
            if (child.isDirectory()) collect(child, path, entries);
            else entries.add(new Entry(path, child.length(), child.lastModified(), sha1(child)));
        }
    }

    // Named rather than counted, so a log can say which files moved and an
    // upload can send only those.
    public static List<String> changed(List<Entry> before, List<Entry> after) {
        Map<String, String> was = digests(before);
        Map<String, String> is = digests(after);
        TreeSet<String> paths = new TreeSet<>();
        paths.addAll(was.keySet());
        paths.addAll(is.keySet());
        List<String> changed = new ArrayList<>();
        for (String path : paths) {
            String left = was.get(path);
            String right = is.get(path);
            if (left == null || right == null || !left.equals(right)) changed.add(path);
        }
        return changed;
    }

    private static Map<String, String> digests(List<Entry> entries) {
        Map<String, String> digests = new LinkedHashMap<>();
        for (Entry entry : entries) digests.put(entry.path, entry.sha1);
        return digests;
    }

    // A path that climbs out of the tree, or a file Steam will not hold, has no
    // business in a transfer in either direction.
    public static boolean acceptable(Entry entry) {
        if (entry.path.isEmpty()) return false;
        if (entry.path.startsWith("/") || entry.path.contains("\\")) return false;
        for (String part : entry.path.split("/")) {
            if (part.equals("..")) return false;
        }
        return entry.length <= MAX_BYTES;
    }

    private static String sha1(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            try (InputStream in = new FileInputStream(file)) {
                int read;
                while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
            }
            StringBuilder text = new StringBuilder();
            for (byte value : digest.digest()) text.append(String.format("%02x", value));
            return text.toString();
        }
        catch (NoSuchAlgorithmException | IOException unreadable) {
            // An unreadable file is not an empty one, and must not compare equal
            // to anything - including to itself on the next pass.
            return "unreadable:" + file.getName() + ":" + file.lastModified();
        }
    }
}
