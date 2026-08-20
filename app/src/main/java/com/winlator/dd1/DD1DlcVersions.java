package com.winlator.dd1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

// Which version of each DLC is on disk. Without it an installed DLC and an
// out-of-date one look the same, and the screen would have to claim one or the
// other. The file lives beside the game rather than inside it, so replacing the
// whole tree does not take the record with it.
public final class DD1DlcVersions {
    private DD1DlcVersions() {}

    private static final String FILE = "dlc-versions";

    public static Map<Integer, String> installed(File filesDir) {
        Map<Integer, String> versions = new LinkedHashMap<>();
        File file = new File(filesDir, FILE);
        if (!file.isFile()) return versions;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int separator = line.indexOf('=');
                if (separator <= 0) continue;
                try {
                    versions.put(Integer.parseInt(line.substring(0, separator).trim()),
                        line.substring(separator + 1).trim());
                }
                catch (NumberFormatException notAnId) {
                    // A line nobody wrote on purpose says nothing about a version.
                }
            }
        }
        catch (IOException unreadable) {
            return new LinkedHashMap<>();
        }
        return versions;
    }

    public static void record(File filesDir, int appId, String manifest) {
        Map<Integer, String> versions = installed(filesDir);
        versions.put(appId, manifest);
        write(filesDir, versions);
    }

    // A DLC installed before this record existed has no line, and reading that as
    // an update would offer every owner a fresh download of content they already
    // have. What the launcher put on disk was the version Steam was offering when
    // it was fetched, so that is what is taken as installed. If Steam has moved
    // since, the next change to the manifest says so.
    public static void adopt(File filesDir, Iterable<Integer> installedAppIds,
            DD1DepotCatalog catalog) {
        Map<Integer, String> versions = installed(filesDir);
        boolean changed = false;
        for (int appId : installedAppIds) {
            if (versions.containsKey(appId)) continue;
            String offered = catalog.manifestOf(appId);
            if (offered == null) continue;
            versions.put(appId, offered);
            changed = true;
        }
        if (changed) write(filesDir, versions);
    }

    public static void forget(File filesDir, Iterable<Integer> appIds) {
        Map<Integer, String> versions = installed(filesDir);
        for (int appId : appIds) versions.remove(appId);
        write(filesDir, versions);
    }

    private static void write(File filesDir, Map<Integer, String> versions) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<Integer, String> entry : versions.entrySet())
            text.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        try (FileWriter writer = new FileWriter(new File(filesDir, FILE))) {
            writer.write(text.toString());
        }
        catch (IOException unwritable) {
            // Losing the record only costs the offer to update; it must not stop
            // the install that just succeeded.
        }
    }
}
