package com.winlator.dd1;

import com.winlator.core.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

// A slot fetched out of the cloud waits here until the player has seen what it
// holds. Nothing in the save tree moves until they say so, and then only after a
// snapshot - the same shape as the game's own staging directory, for the same
// reason.
public final class DD1SaveStaging {
    private DD1SaveStaging() {}

    public static File dir(File filesDir, String slot) {
        return new File(filesDir, "staging/saves/" + slot);
    }

    public static void clear(File filesDir, String slot) {
        FileUtils.delete(dir(filesDir, slot));
    }

    public static boolean put(File filesDir, String slot, String path, byte[] content) {
        // An empty file is what a transfer that failed leaves behind. Refusing it
        // here is what keeps it from ever reaching the save tree.
        if (content == null || content.length == 0) return false;
        if (!path.startsWith(slot + "/")) return false;
        String inside = path.substring(slot.length() + 1);
        if (inside.isEmpty() || inside.contains("..") || inside.startsWith("/")) return false;

        File target = new File(dir(filesDir, slot), inside);
        if (target.getParentFile() != null) target.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(target)) {
            out.write(content);
            return true;
        }
        catch (Exception failed) {
            return false;
        }
    }

    public static DD1SaveSlot describe(File filesDir, String slot) {
        return DD1SaveSlot.of(dir(filesDir, slot));
    }

    public static boolean apply(File filesDir, String slot) {
        File staged = dir(filesDir, slot);
        // A staged slot with no readable game file in it is not a save, and
        // replacing a real one with it would be the whole disaster in one step.
        if (DD1SaveSlot.of(staged) == null) return false;
        // Only when the tree has moved since the last one: three slots are all
        // there are, and spending them on copies of the same state pushes out the
        // one worth going back to.
        if (DD1SavePolicy.worthTaking(filesDir)
                && DD1SaveSnapshots.take(filesDir, System.currentTimeMillis()) == null)
            return false;

        File live = new File(DD1Saves.root(filesDir), slot);
        FileUtils.delete(live);
        File parent = live.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
        return staged.renameTo(live);
    }
}
