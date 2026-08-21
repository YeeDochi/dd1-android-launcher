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
        public final boolean disabled;

        private Row(String directoryName, long publishedFileId, String title, State state,
                boolean installed, boolean disabled) {
            this.directoryName = directoryName;
            this.publishedFileId = publishedFileId;
            this.title = title;
            this.state = state;
            this.installed = installed;
            this.disabled = disabled;
        }
    }

    public static final class Card {
        public final DD1WorkshopItem item;
        public final boolean subscribed;
        public final boolean installed;
        public final boolean disabled;
        public final boolean updateAvailable;

        private Card(DD1WorkshopItem item, boolean subscribed, boolean installed,
                boolean disabled, boolean updateAvailable) {
            this.item = item;
            this.subscribed = subscribed;
            this.installed = installed;
            this.disabled = disabled;
            this.updateAvailable = updateAvailable;
        }
    }

    public final Phase phase;
    public final List<Row> rows;
    public final String message;
    public final int progress;
    public final List<String> log;
    public final List<Card> browse;
    public final String query;
    public final int sort;
    public final int page;
    public final int total;
    public final boolean browseLoading;
    public final String browseError;
    private final List<ModSyncPlan.Subscribed> syncItems;

    private DD1WorkshopSnapshot(Phase phase, List<Row> rows,
            List<ModSyncPlan.Subscribed> syncItems, String message, int progress,
            List<String> log, List<Card> browse, String query, int sort, int page,
            int total, boolean browseLoading, String browseError) {
        this.phase = phase;
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        this.syncItems = Collections.unmodifiableList(new ArrayList<>(syncItems));
        this.message = message;
        this.progress = progress;
        this.log = Collections.unmodifiableList(new ArrayList<>(log));
        this.browse = Collections.unmodifiableList(new ArrayList<>(browse));
        this.query = query;
        this.sort = sort;
        this.page = page;
        this.total = total;
        this.browseLoading = browseLoading;
        this.browseError = browseError;
    }

    public static DD1WorkshopSnapshot loading() {
        return new DD1WorkshopSnapshot(Phase.LOADING, Collections.emptyList(),
            Collections.emptyList(), null, 0, Collections.emptyList(), Collections.emptyList(),
            "", 0, 0, 0, false, null);
    }

    public static DD1WorkshopSnapshot error(String message) {
        return new DD1WorkshopSnapshot(Phase.ERROR, Collections.emptyList(),
            Collections.emptyList(), message, 0, Collections.singletonList(message),
            Collections.emptyList(), "", 0, 0, 0, false, null);
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
                : local.directoryName, item.publishedFileId, item.title, state, local != null,
                local != null && local.disabled));
        }
        for (DD1Workshop.Mod mod : scanned) {
            if (mod.publishedFileId == 0)
                rows.add(new Row(mod.directoryName, 0, mod.title, State.LOCAL, true, mod.disabled));
            else if (!subscribedIds.contains(mod.publishedFileId))
                rows.add(new Row(mod.directoryName, mod.publishedFileId, mod.title,
                    State.ORPHAN, true, mod.disabled));
        }

        List<ModSyncPlan.Subscribed> sync = new ArrayList<>(plan.install);
        sync.addAll(plan.update);
        sync.addAll(plan.disabledUpdate);
        return new DD1WorkshopSnapshot(Phase.READY, rows, sync, null, 0,
            Collections.emptyList(), Collections.emptyList(), "", 0, 0, 0, false, null);
    }

    public DD1WorkshopSnapshot syncing(String message, int progress, List<String> log) {
        return copy(Phase.SYNCING, rows, syncItems, message, progress, log, browse, query, sort,
            page, total, browseLoading, browseError);
    }

    public DD1WorkshopSnapshot browseLoading(String query, int sort, int page) {
        return copy(phase, rows, syncItems, message, progress, log, browse, query, sort, page,
            total, true, null);
    }

    public DD1WorkshopSnapshot withBrowse(List<DD1WorkshopItem> items, String query, int sort,
            int page, int total, boolean append, String error) {
        List<Card> cards = append ? new ArrayList<>(browse) : new ArrayList<>();
        for (DD1WorkshopItem item : items) {
            Row row = find(item.publishedFileId);
            boolean subscribed = row != null && row.state != State.ORPHAN && row.state != State.LOCAL;
            cards.add(new Card(item, subscribed, row != null && row.installed,
                row != null && row.disabled, row != null && row.state == State.UPDATE));
        }
        return copy(phase, rows, syncItems, message, progress, log, cards, query, sort, page,
            total, false, error);
    }

    public DD1WorkshopSnapshot browseFailed(String error) {
        return copy(phase, rows, syncItems, message, progress, log, browse, query, sort, page,
            total, false, error);
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

    public Card findBrowse(long publishedFileId) {
        for (Card card : browse) if (card.item.publishedFileId == publishedFileId) return card;
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

    private static DD1WorkshopSnapshot copy(Phase phase, List<Row> rows,
            List<ModSyncPlan.Subscribed> syncItems, String message, int progress, List<String> log,
            List<Card> browse, String query, int sort, int page, int total, boolean browseLoading,
            String browseError) {
        return new DD1WorkshopSnapshot(phase, rows, syncItems, message, progress, log, browse,
            query, sort, page, total, browseLoading, browseError);
    }
}
