package com.winlator.dd1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DD1WorkshopItem {
    public final long publishedFileId;
    public final String title;
    public final String description;
    public final String previewUrl;
    public final long fileSize;
    public final int subscriptions;
    public final float score;
    public final long updatedAt;
    public final boolean downloadable;
    public final List<String> previewUrls;

    public DD1WorkshopItem(long publishedFileId, String title, String description,
            String previewUrl, long fileSize, int subscriptions, float score,
            long updatedAt, boolean downloadable) {
        this(publishedFileId, title, description, previewUrl, fileSize, subscriptions, score,
            updatedAt, downloadable, previewUrl == null || previewUrl.isEmpty()
                ? Collections.emptyList() : Collections.singletonList(previewUrl));
    }

    public DD1WorkshopItem(long publishedFileId, String title, String description,
            String previewUrl, long fileSize, int subscriptions, float score,
            long updatedAt, boolean downloadable, List<String> previewUrls) {
        this.publishedFileId = publishedFileId;
        this.title = title;
        this.description = description;
        this.previewUrl = previewUrl;
        this.fileSize = fileSize;
        this.subscriptions = subscriptions;
        this.score = score;
        this.updatedAt = updatedAt;
        this.downloadable = downloadable;
        this.previewUrls = Collections.unmodifiableList(new ArrayList<>(previewUrls));
    }
}
