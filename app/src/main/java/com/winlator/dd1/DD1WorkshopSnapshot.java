package com.winlator.dd1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DD1WorkshopSnapshot {
    public enum Phase { LOADING, READY, SYNCING, ERROR }
    public enum State { INSTALL, UPDATE, CURRENT, ORPHAN, LOCAL, SKIPPED }

    public static final class Row {
        public final String directoryName;
        public final long publishedFileId;
        public final String title;
        public final State state;
        public final boolean installed;

        private Row(String directoryName, long publishedFileId, String title, State state,
                boolean installed) {
            this.directoryName = directoryName;
            this.publishedFileId = publishedFileId;
            this.title = title;
            this.state = state;
            this.installed = installed;
        }
    }

    public final Phase phase;
    public final List<Row> rows;
    public final String message;
    public final int progress;
    public final List<String> log;
    private final List<ModSyncPlan.Subscribed> syncItems;

    private DD1WorkshopSnapshot(Phase phase, List<Row> rows,
            List<ModSyncPlan.Subscribed> syncItems, String message, int progress,
            List<String> log) {
        this.phase = phase;
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        this.syncItems = Collections.unmodifiableList(new ArrayList<>(syncItems));
        this.message = message;
        this.progress = progress;
        this.log = Collections.unmodifiableList(new ArrayList<>(log));
    }

    public static DD1WorkshopSnapshot loading() {
        return new DD1WorkshopSnapshot(Phase.LOADING, Collections.emptyList(),
            Collections.emptyList(), null, 0, Collections.emptyList());
    }

    public static DD1WorkshopSnapshot error(String message) {
        return new DD1WorkshopSnapshot(Phase.ERROR, Collections.emptyList(),
            Collections.emptyList(), message, 0, Collections.singletonList(message));
    }

    public static DD1WorkshopSnapshot ready(List<ModSyncPlan.Subscribed> subscribed,
            List<DD1Workshop.Mod> scanned) {
        List<ModSyncPlan.Installed> installed = new ArrayList<>();
        Map<Long, DD1Workshop.Mod> workshop = new HashMap<>();
        for (DD1Workshop.Mod mod : scanned) {
            if (mod.publishedFileId == 0) continue;
            workshop.put(mod.publishedFileId, mod);
            installed.add(new ModSyncPlan.Installed(mod.publishedFileId, mod.updatedAt, true));
        }
        ModSyncPlan plan = ModSyncPlan.of(subscribed, installed);
        Set<Long> updates = ids(plan.update);
        updates.addAll(ids(plan.disabledUpdate));
        Set<Long> current = new HashSet<>(plan.upToDate);
        Set<Long> skipped = new HashSet<>();
        for (ModSyncPlan.Skipped item : plan.skipped) skipped.add(item.publishedFileId);

        List<Row> rows = new ArrayList<>();
        Set<Long> subscribedIds = new HashSet<>();
        for (ModSyncPlan.Subscribed item : subscribed) {
            subscribedIds.add(item.publishedFileId);
            DD1Workshop.Mod local = workshop.get(item.publishedFileId);
            State state = skipped.contains(item.publishedFileId) ? State.SKIPPED
                : local == null ? State.INSTALL
                : updates.contains(item.publishedFileId) ? State.UPDATE
                : current.contains(item.publishedFileId) ? State.CURRENT : State.SKIPPED;
            rows.add(new Row(local == null ? Long.toString(item.publishedFileId)
                : local.directoryName, item.publishedFileId, item.title, state, local != null));
        }
        for (DD1Workshop.Mod mod : scanned) {
            if (mod.publishedFileId == 0)
                rows.add(new Row(mod.directoryName, 0, mod.title, State.LOCAL, true));
            else if (!subscribedIds.contains(mod.publishedFileId))
                rows.add(new Row(mod.directoryName, mod.publishedFileId, mod.title,
                    State.ORPHAN, true));
        }

        List<ModSyncPlan.Subscribed> sync = new ArrayList<>(plan.install);
        sync.addAll(plan.update);
        sync.addAll(plan.disabledUpdate);
        return new DD1WorkshopSnapshot(Phase.READY, rows, sync, null, 0,
            Collections.emptyList());
    }

    public DD1WorkshopSnapshot syncing(String message, int progress, List<String> log) {
        return new DD1WorkshopSnapshot(Phase.SYNCING, rows, syncItems, message, progress, log);
    }

    public boolean syncable() {
        return phase == Phase.READY && !syncItems.isEmpty();
    }

    public List<ModSyncPlan.Subscribed> syncItems() {
        return syncItems;
    }

    public Row find(long publishedFileId) {
        for (Row row : rows) if (row.publishedFileId == publishedFileId) return row;
        return null;
    }

    public Row findDirectory(String directoryName) {
        for (Row row : rows) if (row.directoryName.equals(directoryName)) return row;
        return null;
    }

    private static Set<Long> ids(List<ModSyncPlan.Subscribed> items) {
        Set<Long> result = new HashSet<>();
        for (ModSyncPlan.Subscribed item : items) result.add(item.publishedFileId);
        return result;
    }
}
