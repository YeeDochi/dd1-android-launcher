package com.winlator.dd1;

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

    public DD1WorkshopItem(long publishedFileId, String title, String description,
            String previewUrl, long fileSize, int subscriptions, float score,
            long updatedAt, boolean downloadable) {
        this.publishedFileId = publishedFileId;
        this.title = title;
        this.description = description;
        this.previewUrl = previewUrl;
        this.fileSize = fileSize;
        this.subscriptions = subscriptions;
        this.score = score;
        this.updatedAt = updatedAt;
        this.downloadable = downloadable;
    }
}
