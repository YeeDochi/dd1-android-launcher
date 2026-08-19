package com.winlator.dd1;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// The launcher's view of the mods directory: which mods exist, in what order,
// and which are on. Mods can appear or vanish outside the launcher, so the
// stored list is reconciled against a directory scan before it is used.
public final class ModList {
    private static final class Entry {
        final String id;
        boolean enabled;

        Entry(String id, boolean enabled) {
            this.id = id;
            this.enabled = enabled;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    private ModList() {}

    // One line per mod: "+id" is enabled, "-id" is disabled. Anything else is
    // ignored, because a damaged list is not worth failing over - the directory
    // scan rebuilds it.
    public static ModList parse(String stored) {
        ModList list = new ModList();
        if (stored == null) return list;
        for (String line : stored.split("\n")) {
            String row = line.trim();
            if (row.length() < 2) continue;
            char state = row.charAt(0);
            if (state != '+' && state != '-') continue;
            list.entries.add(new Entry(row.substring(1), state == '+'));
        }
        return list;
    }

    public void reconcile(Collection<String> scannedIds) {
        Set<String> present = new LinkedHashSet<>(scannedIds);
        List<Entry> kept = new ArrayList<>();
        for (Entry entry : entries) {
            if (present.remove(entry.id)) kept.add(entry);
        }
        for (String added : present) kept.add(new Entry(added, true));
        entries.clear();
        entries.addAll(kept);
    }

    public List<String> ids() {
        List<String> result = new ArrayList<>();
        for (Entry entry : entries) result.add(entry.id);
        return result;
    }

    public boolean isEnabled(String id) {
        Entry entry = find(id);
        return entry != null && entry.enabled;
    }

    public void setEnabled(String id, boolean enabled) {
        Entry entry = find(id);
        if (entry != null) entry.enabled = enabled;
    }

    public void move(String id, int delta) {
        int from = indexOf(id);
        int to = from + delta;
        if (from < 0 || to < 0 || to >= entries.size()) return;
        entries.add(to, entries.remove(from));
    }

    // What the game reads: the enabled mods, in order.
    public List<String> loadOrder() {
        List<String> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.enabled) result.add(entry.id);
        }
        return result;
    }

    public String serialize() {
        StringBuilder text = new StringBuilder();
        for (Entry entry : entries) text.append(entry.enabled ? '+' : '-').append(entry.id).append('\n');
        return text.toString();
    }

    private Entry find(String id) {
        int index = indexOf(id);
        return index < 0 ? null : entries.get(index);
    }

    private int indexOf(String id) {
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).id.equals(id)) return index;
        }
        return -1;
    }
}
