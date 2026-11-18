package com.winlator.dd1;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// The slots each side holds. Working a slot at a time is also what keeps the two
// files Steam keeps in the root out of every transfer, and those are exactly the
// ones its listing names wrongly.
public final class DD1SaveSlots {
    private DD1SaveSlots() {}

    public static List<DD1SaveSlot> local(File filesDir) {
        List<DD1SaveSlot> slots = new ArrayList<>();
        for (File profile : DD1Saves.profiles(DD1Saves.root(filesDir))) {
            DD1SaveSlot slot = DD1SaveSlot.of(profile);
            if (slot != null) slots.add(slot);
        }
        return slots;
    }

    public static List<String> cloudSlotNames(DD1CloudListing listing) {
        Set<String> names = new LinkedHashSet<>();
        for (DD1SaveSummary.Entry file : listing.files()) {
            String slot = slotOf(file.path);
            if (slot != null) names.add(slot);
        }
        List<String> sorted = new ArrayList<>(names);
        sorted.sort((left, right) ->
            Integer.compare(DD1Saves.slotOf(left), DD1Saves.slotOf(right)));
        return sorted;
    }

    // Steam named the two files it keeps in the tree's root as though they were
    // inside profile_0, and a slot transfer duly carried them into it: fourteen
    // files became sixteen, and the game reads them from the root, so the copies
    // were inert clutter. What lives where is settled by the local tree - a name
    // the root already has is a root file whatever the cloud calls it.
    public static List<DD1SaveSummary.Entry> filesOf(DD1CloudListing listing, String slot,
            Set<String> namesAtRoot) {
        List<DD1SaveSummary.Entry> files = new ArrayList<>();
        for (DD1SaveSummary.Entry file : listing.files()) {
            if (!slot.equals(slotOf(file.path))) continue;
            if (namesAtRoot.contains(basenameOf(file.path))) continue;
            files.add(file);
        }
        return files;
    }

    // What the cloud holds for a slot that the slot here no longer has. The game
    // writes persist.raid.json, persist.map.json and the loading screen only
    // during an expedition and deletes them on the way home; an upload that left
    // the cloud's copies in place handed a PC a hamlet save with the last
    // dungeon still attached. Root files are never in it (filesOf).
    public static List<String> staleInCloud(DD1CloudListing listing, String slot,
            Set<String> namesAtRoot, java.util.Collection<String> local) {
        List<String> stale = new ArrayList<>();
        if (!listing.known()) return stale;
        Set<String> kept = new java.util.HashSet<>();
        for (String path : local) kept.add(path.toLowerCase(java.util.Locale.ROOT));
        for (DD1SaveSummary.Entry file : filesOf(listing, slot, namesAtRoot)) {
            if (!kept.contains(file.path.toLowerCase(java.util.Locale.ROOT))) stale.add(file.path);
        }
        return stale;
    }

    // What the tree's own root holds, by name. The caller reads it once and hands
    // it in, because deciding this per file would stat the directory per file.
    public static Set<String> namesAtRoot(File filesDir) {
        Set<String> names = new LinkedHashSet<>();
        File[] entries = DD1Saves.root(filesDir).listFiles();
        if (entries == null) return names;
        for (File entry : entries) {
            if (entry.isFile()) names.add(entry.getName());
        }
        return names;
    }

    private static String basenameOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    // Anything not under a profile_N belongs to no slot: the options file and
    // Steam's own marker both live in the root.
    private static String slotOf(String path) {
        int slash = path.indexOf('/');
        if (slash <= 0) return null;
        String head = path.substring(0, slash);
        return DD1Saves.slotOf(head) < 0 ? null : head;
    }
}
