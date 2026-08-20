package com.winlator.dd1;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class DD1Workshop {
    private static final String MARKER = ".dd1-workshop";

    public static final class Mod {
        public final String directoryName;
        public final long publishedFileId;
        public final long updatedAt;
        public final String title;

        private Mod(String directoryName, long publishedFileId, long updatedAt, String title) {
            this.directoryName = directoryName;
            this.publishedFileId = publishedFileId;
            this.updatedAt = updatedAt;
            this.title = title;
        }
    }

    private DD1Workshop() {}

    public static List<Mod> scan(File filesDir) {
        File[] directories = new File(filesDir, "game/mods").listFiles(File::isDirectory);
        if (directories == null) return Collections.emptyList();
        Arrays.sort(directories, Comparator.comparing(File::getName));
        List<Mod> result = new ArrayList<>();
        for (File directory : directories) result.add(read(directory));
        return result;
    }

    public static File staging(File filesDir, long publishedFileId) throws IOException {
        File directory = new File(filesDir, "workshop-staging/" + publishedFileId);
        deleteTree(directory);
        if (!directory.mkdirs()) throw new IOException("Cannot create Workshop staging directory");
        return directory;
    }

    public static void promote(File filesDir, long publishedFileId, long updatedAt, String title)
            throws IOException {
        File staging = new File(filesDir, "workshop-staging/" + publishedFileId);
        File payload = payload(staging);
        Files.write(new File(payload, MARKER).toPath(), marker(publishedFileId, updatedAt, title));

        File mods = new File(filesDir, "game/mods");
        if (!mods.isDirectory() && !mods.mkdirs()) throw new IOException("Cannot create mods directory");
        File active = new File(mods, Long.toString(publishedFileId));
        File backup = new File(mods, publishedFileId + ".dd1-backup");
        deleteTree(backup);

        boolean backedUp = active.exists();
        if (backedUp && !active.renameTo(backup)) throw new IOException("Cannot preserve installed mod");
        if (!payload.renameTo(active)) {
            if (backedUp && !backup.renameTo(active))
                throw new IOException("Cannot install or restore Workshop mod " + publishedFileId);
            throw new IOException("Cannot install Workshop mod " + publishedFileId);
        }
        deleteTree(backup);
        deleteTree(staging);
    }

    public static void delete(File filesDir, String directoryName) throws IOException {
        File mods = new File(filesDir, "game/mods").getCanonicalFile();
        if (directoryName == null || directoryName.isEmpty() || new File(directoryName).isAbsolute())
            throw new IOException("Invalid mod directory");
        File target = new File(mods, directoryName).getCanonicalFile();
        if (!mods.equals(target.getParentFile())) throw new IOException("Invalid mod directory");
        deleteTree(target);
    }

    private static Mod read(File directory) {
        try {
            List<String> lines = Files.readAllLines(new File(directory, MARKER).toPath(),
                StandardCharsets.UTF_8);
            if (lines.size() < 3) throw new IOException("Incomplete marker");
            long id = Long.parseLong(lines.get(0));
            long updated = Long.parseLong(lines.get(1));
            if (id <= 0 || !directory.getName().equals(Long.toString(id)))
                throw new IOException("Mismatched marker");
            return new Mod(directory.getName(), id, updated, lines.get(2));
        }
        catch (Exception ignored) {
            return new Mod(directory.getName(), 0, 0, directory.getName());
        }
    }

    private static File payload(File staging) throws IOException {
        if (Files.isSymbolicLink(staging.toPath())) throw new IOException("Workshop staging is a link");
        if (new File(staging, "project.xml").isFile()) return staging;
        File[] children = staging.listFiles(File::isDirectory);
        if (children != null && children.length == 1 && !Files.isSymbolicLink(children[0].toPath())
                && new File(children[0], "project.xml").isFile()) return children[0];
        throw new IOException("Workshop item has no project.xml");
    }

    private static byte[] marker(long id, long updatedAt, String title) {
        String safeTitle = title == null ? Long.toString(id) : title.replace('\n', ' ').replace('\r', ' ');
        return (id + "\n" + updatedAt + "\n" + safeTitle + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static void deleteTree(File file) throws IOException {
        if (!file.exists() && !Files.isSymbolicLink(file.toPath())) return;
        if (file.isDirectory() && !Files.isSymbolicLink(file.toPath())) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot read " + file.getName());
            for (File child : children) deleteTree(child);
        }
        if (!file.delete()) throw new IOException("Cannot delete " + file.getName());
    }
}
