package com.winlator.dd1;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class DD1Workshop {
    private static final String MARKER = ".dd1-workshop";

    public static final class Mod {
        public final String directoryName;
        public final long publishedFileId;
        public final long updatedAt;
        public final String title;
        public final boolean disabled;

        private Mod(String directoryName, long publishedFileId, long updatedAt, String title,
                boolean disabled) {
            this.directoryName = directoryName;
            this.publishedFileId = publishedFileId;
            this.updatedAt = updatedAt;
            this.title = title;
            this.disabled = disabled;
        }
    }

    private DD1Workshop() {}

    public static List<Mod> scan(File filesDir) {
        List<Mod> result = new ArrayList<>();
        scanRoot(new File(filesDir, "game/mods"), false, result);
        scanRoot(new File(filesDir, "game/mods-disabled"), true, result);
        return result;
    }

    private static void scanRoot(File root, boolean disabled, List<Mod> result) {
        File[] directories = root.listFiles(File::isDirectory);
        if (directories == null) return;
        Arrays.sort(directories, Comparator.comparing(File::getName));
        for (File directory : directories) result.add(read(directory, disabled));
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
        rejectLinks(payload);
        Files.write(new File(payload, MARKER).toPath(), marker(publishedFileId, updatedAt, title));

        File disabled = new File(filesDir, "game/mods-disabled/" + publishedFileId);
        File mods = new File(filesDir, disabled.isDirectory() ? "game/mods-disabled" : "game/mods");
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

    public static void disable(File filesDir, String directoryName) throws IOException {
        move(filesDir, directoryName, "mods", "mods-disabled");
    }

    public static void enable(File filesDir, String directoryName) throws IOException {
        move(filesDir, directoryName, "mods-disabled", "mods");
    }

    private static Mod read(File directory, boolean disabled) {
        try {
            List<String> lines = Files.readAllLines(new File(directory, MARKER).toPath(),
                StandardCharsets.UTF_8);
            if (lines.size() < 3) throw new IOException("Incomplete marker");
            long id = Long.parseLong(lines.get(0));
            long updated = Long.parseLong(lines.get(1));
            if (id <= 0 || !directory.getName().equals(Long.toString(id)))
                throw new IOException("Mismatched marker");
            return new Mod(directory.getName(), id, updated, lines.get(2), disabled);
        }
        catch (Exception ignored) {
            return new Mod(directory.getName(), 0, 0, directory.getName(), disabled);
        }
    }

    private static void move(File filesDir, String directoryName, String fromName, String toName)
            throws IOException {
        if (directoryName == null || directoryName.isEmpty() || new File(directoryName).isAbsolute())
            throw new IOException("Invalid mod directory");
        File game = new File(filesDir, "game");
        File from = new File(game, fromName).getCanonicalFile();
        File to = new File(game, toName).getCanonicalFile();
        File sourcePath = new File(from, directoryName);
        if (Files.isSymbolicLink(sourcePath.toPath())) throw new IOException("Mod directory is a link");
        File source = sourcePath.getCanonicalFile();
        File destination = new File(to, directoryName).getCanonicalFile();
        if (!from.equals(source.getParentFile()) || !to.equals(destination.getParentFile()))
            throw new IOException("Invalid mod directory");
        if (!source.isDirectory()) throw new IOException("Mod directory no longer exists");
        if (destination.exists()) throw new IOException("Mod already exists in the destination");
        if (!to.isDirectory() && !to.mkdirs()) throw new IOException("Cannot create mod directory");
        if (!source.renameTo(destination)) throw new IOException("Cannot move mod directory");
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

    private static void rejectLinks(File file) throws IOException {
        if (Files.isSymbolicLink(file.toPath()))
            throw new IOException("Workshop payload contains a symbolic link");
        if (!file.isDirectory()) return;
        File[] children = file.listFiles();
        if (children == null) throw new IOException("Cannot read Workshop payload");
        for (File child : children) rejectLinks(child);
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
