package com.winlator.dd1;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
        Collections.sort(sorted, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                return Integer.compare(DD1Saves.slotOf(left), DD1Saves.slotOf(right));
            }
        });
        return sorted;
    }

    public static List<DD1SaveSummary.Entry> filesOf(DD1CloudListing listing, String slot) {
        List<DD1SaveSummary.Entry> files = new ArrayList<>();
        for (DD1SaveSummary.Entry file : listing.files()) {
            if (slot.equals(slotOf(file.path))) files.add(file);
        }
        return files;
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
