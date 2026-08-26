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
    // Which local mods the synchronised folder is answerable for. Without it a
    // folder that lost a mod could take a hand-imported ZIP with it, and choosing
    // the wrong folder once would delete every local mod on the device.
    private static final String SOURCE = ".dd1-local";
    static final long MAX_IMPORT_BYTES = 512L * 1024 * 1024;

    public static final class Mod {
        public final String directoryName;
        public final long publishedFileId;
        public final long updatedAt;
        public final String title;
        public final boolean disabled;
        public final boolean fromFolder;

        private Mod(String directoryName, long publishedFileId, long updatedAt, String title,
                boolean disabled, boolean fromFolder) {
            this.directoryName = directoryName;
            this.publishedFileId = publishedFileId;
            this.updatedAt = updatedAt;
            this.title = title;
            this.disabled = disabled;
            this.fromFolder = fromFolder;
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

    public static String importZip(File filesDir, InputStream input, String sourceName,
            boolean fromFolder) throws IOException {
        File staging = importStaging(filesDir);
        try {
            extractZip(input, staging);
            return installImport(filesDir, sourceName, fromFolder);
        }
        finally {
            deleteTree(staging);
        }
    }

    // A chosen ZIP goes where a mod dropped from a computer goes, unpacked, so a
    // local mod has one home and the list has one thing to be synchronised
    // against. Staging sits in the same folder because a rename cannot cross from
    // app data to the shared volume.
    public static String unpackInto(File folder, InputStream input, String sourceName)
            throws IOException {
        File staging = new File(folder, ".dd1-unpacking");
        deleteTree(staging);
        if (!staging.isDirectory() && !staging.mkdirs())
            throw new IOException("Cannot write to " + folder);
        try {
            extractZip(input, staging);
            File payload = payload(staging);
            String name = importName(sourceName);
            File destination = new File(folder, name);
            if (!folder.equals(destination.getParentFile()))
                throw new IOException("Invalid mod name");
            if (destination.exists()) throw new IOException("A mod with this name already exists");
            if (!payload.renameTo(destination))
                throw new IOException("Cannot unpack into " + folder);
            return name;
        }
        finally {
            deleteTree(staging);
        }
    }

    // A folder the user dropped where the game cannot see it. Its bytes arrive
    // through a document provider rather than a path, so the caller copies them
    // in here; what happens after that is what a ZIP gets.
    public static File importStaging(File filesDir) throws IOException {
        File staging = new File(filesDir, "local-import-staging");
        deleteTree(staging);
        if (!staging.mkdirs()) throw new IOException("Cannot create import staging directory");
        return staging;
    }

    // Cheap enough to ask before copying anything: a folder that is synchronised
    // every time the screen opens must not read hundreds of megabytes to find out
    // it has nothing to do.
    public static boolean alreadyInstalled(File filesDir, String sourceName) {
        String name = importName(sourceName);
        return new File(filesDir, "game/mods/" + name).exists()
            || new File(filesDir, "game/mods-disabled/" + name).exists();
    }

    // The directory a folder entry would land in, so a synchronisation can tell
    // what is still there from what is gone by the same name it installed under.
    public static String installedName(String sourceName) {
        return importName(sourceName);
    }

    public static void discardImport(File filesDir) {
        try {
            deleteTree(new File(filesDir, "local-import-staging"));
        }
        catch (IOException halfDeleted) {
            // The next import starts by clearing it, so a leftover is not a failure
            // worth reporting over the one that got here.
        }
    }

    public static String installImport(File filesDir, String sourceName, boolean fromFolder)
            throws IOException {
        File staging = new File(filesDir, "local-import-staging");
        try {
            File payload = payload(staging);
            File marker = new File(payload, MARKER);
            if (marker.exists() && !marker.delete())
                throw new IOException("Cannot remove Workshop marker from local mod");
            if (fromFolder) Files.write(new File(payload, SOURCE).toPath(),
                sourceName.getBytes(StandardCharsets.UTF_8));

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

    public static boolean cameFromFolder(File filesDir, String directoryName) {
        for (Mod mod : scan(filesDir))
            if (mod.directoryName.equals(directoryName)) return mod.fromFolder;
        return false;
    }

    // Deleting a mod the folder put there has to reach the folder, or the next
    // synchronisation puts it straight back. Matched by the name it installed
    // under, so a ZIP is found by what it unpacked to and not by its own spelling.
    public static void removeFromFolder(File folder, String directoryName) throws IOException {
        File[] entries = folder.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            if (entry.getName().startsWith(".")) continue;
            if (installedName(entry.getName()).equals(directoryName)) deleteTree(entry);
        }
    }

    // The other half of synchronising a folder: a mod that is no longer in it goes,
    // and nothing else does. Only mods the folder put there carry the marker, so a
    // ZIP imported by hand and every Workshop mod are left alone.
    public static List<String> removeMissingFromFolder(File filesDir, Collection<String> present)
            throws IOException {
        List<String> removed = new ArrayList<>();
        for (Mod mod : scan(filesDir)) {
            if (!mod.fromFolder || present.contains(mod.directoryName)) continue;
            delete(filesDir, mod.directoryName, mod.disabled);
            removed.add(mod.directoryName);
        }
        return removed;
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
            return new Mod(directory.getName(), id, updated, lines.get(2), disabled, false);
        }
        catch (Exception ignored) {
            return new Mod(directory.getName(), 0, 0, directory.getName(), disabled,
                new File(directory, SOURCE).isFile());
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
