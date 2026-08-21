package com.winlator.dd1;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// What a Workshop synchronization would do, decided before anything is touched.
// Nothing here downloads or deletes; the caller applies the plan.
public final class ModSyncPlan {
    public static final class Subscribed {
        public final long publishedFileId;
        public final String title;
        public final long updatedAt;
        public final boolean downloadable;
        public final long hcontentFile;

        public Subscribed(long publishedFileId, String title, long updatedAt, boolean downloadable) {
            this(publishedFileId, title, updatedAt, downloadable, 0);
        }

        public Subscribed(long publishedFileId, String title, long updatedAt,
                boolean downloadable, long hcontentFile) {
            this.publishedFileId = publishedFileId;
            this.title = title;
            this.updatedAt = updatedAt;
            this.downloadable = downloadable;
            this.hcontentFile = hcontentFile;
        }
    }

    public static final class Installed {
        public final long publishedFileId;
        public final long updatedAt;
        public final boolean enabled;

        public Installed(long publishedFileId, long updatedAt, boolean enabled) {
            this.publishedFileId = publishedFileId;
            this.updatedAt = updatedAt;
            this.enabled = enabled;
        }
    }

    public static final class Skipped {
        public final long publishedFileId;
        public final String title;
        public final String reason;

        Skipped(long publishedFileId, String title, String reason) {
            this.publishedFileId = publishedFileId;
            this.title = title;
            this.reason = reason;
        }
    }

    public final List<Subscribed> install = new ArrayList<>();
    public final List<Subscribed> update = new ArrayList<>();
    public final List<Subscribed> disabledUpdate = new ArrayList<>();
    public final List<Long> upToDate = new ArrayList<>();
    public final List<Installed> orphan = new ArrayList<>();
    public final List<Skipped> skipped = new ArrayList<>();

    private ModSyncPlan() {}

    public static ModSyncPlan of(Collection<Subscribed> subscribed, Collection<Installed> installed) {
        ModSyncPlan plan = new ModSyncPlan();
        Map<Long, Installed> local = new HashMap<>();
        for (Installed item : installed) local.put(item.publishedFileId, item);

        for (Subscribed item : subscribed) {
            Installed current = local.remove(item.publishedFileId);
            if (!item.downloadable) {
                plan.skipped.add(new Skipped(item.publishedFileId, item.title, "no downloadable content"));
                continue;
            }
            if (current == null) plan.install.add(item);
            else if (item.updatedAt <= current.updatedAt) plan.upToDate.add(item.publishedFileId);
            else if (current.enabled) plan.update.add(item);
            else plan.disabledUpdate.add(item);
        }

        // The caller removes these only after Steam returned a complete subscription list.
        plan.orphan.addAll(local.values());
        return plan;
    }

    public boolean hasWork() {
        return !install.isEmpty() || !update.isEmpty() || !disabledUpdate.isEmpty()
            || !orphan.isEmpty() || !skipped.isEmpty() || !upToDate.isEmpty();
    }
}
