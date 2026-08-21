package com.winlator.dd1;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class DD1Workshop {
    private static final String MARKER = ".dd1-workshop";
    private static final long MAX_IMPORT_BYTES = 512L * 1024 * 1024;

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
        delete(filesDir, directoryName, false);
    }

    public static void delete(File filesDir, String directoryName, boolean disabled)
            throws IOException {
        File mods = new File(filesDir, disabled ? "game/mods-disabled" : "game/mods")
            .getCanonicalFile();
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

    public static String importZip(File filesDir, InputStream input, String sourceName)
            throws IOException {
        File staging = new File(filesDir, "local-import-staging");
        deleteTree(staging);
        if (!staging.mkdirs()) throw new IOException("Cannot create import staging directory");
        try {
            extractZip(input, staging);
            File payload = payload(staging);
            File marker = new File(payload, MARKER);
            if (marker.exists() && !marker.delete())
                throw new IOException("Cannot remove Workshop marker from local mod");

            String directoryName = importName(sourceName);
            File mods = new File(filesDir, "game/mods").getCanonicalFile();
            File disabled = new File(filesDir, "game/mods-disabled/" + directoryName);
            File destination = new File(mods, directoryName).getCanonicalFile();
            if (!mods.equals(destination.getParentFile())) throw new IOException("Invalid mod name");
            if (destination.exists() || disabled.exists())
                throw new IOException("A mod with this name already exists");
            if (!mods.isDirectory() && !mods.mkdirs())
                throw new IOException("Cannot create mods directory");
            if (!payload.renameTo(destination)) throw new IOException("Cannot install local mod");
            return directoryName;
        }
        finally {
            deleteTree(staging);
        }
    }

    public static void removeUnsubscribed(File filesDir, Collection<Long> subscribedIds)
            throws IOException {
        for (Mod mod : scan(filesDir)) {
            if (mod.publishedFileId != 0 && !subscribedIds.contains(mod.publishedFileId))
                delete(filesDir, mod.directoryName, mod.disabled);
        }
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

    private static void extractZip(InputStream input, File staging) throws IOException {
        String root = staging.getCanonicalPath() + File.separator;
        long total = 0;
        byte[] buffer = new byte[64 * 1024];
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                File target = new File(staging, entry.getName()).getCanonicalFile();
                if (!target.getPath().startsWith(root))
                    throw new IOException("ZIP contains an unsafe path");
                if (entry.isDirectory()) {
                    if (!target.isDirectory() && !target.mkdirs())
                        throw new IOException("Cannot create imported directory");
                }
                else {
                    File parent = target.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs())
                        throw new IOException("Cannot create imported directory");
                    try (FileOutputStream output = new FileOutputStream(target)) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            total += read;
                            if (total > MAX_IMPORT_BYTES)
                                throw new IOException("ZIP expands beyond 512 MB");
                            output.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static String importName(String sourceName) {
        String name = sourceName == null ? "" : sourceName.trim();
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".zip"))
            name = name.substring(0, name.length() - 4).trim();
        name = name.replaceAll("[^A-Za-z0-9._ -]", "_");
        return name.isEmpty() || name.equals(".") || name.equals("..") ? "imported-mod" : name;
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
