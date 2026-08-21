package com.winlator.dd1;

import java.util.ArrayList;
import java.util.List;
import java.net.URI;

import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.PublishedFileDetails;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_GetUserFiles_Request;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_QueryFiles_Request;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_GetDetails_Request;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_Subscribe_Request;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_Unsubscribe_Request;
import in.dragonbra.javasteam.depotdownloader.data.PubFileItem;

public final class DD1WorkshopCatalog {
    private DD1WorkshopCatalog() {}

    public static CPublishedFile_GetUserFiles_Request request(long steamId, int page) {
        return CPublishedFile_GetUserFiles_Request.newBuilder()
            .setSteamid(steamId)
            .setAppid(DD1SteamEvents.APP_ID)
            .setType("mysubscriptions")
            .setPage(page)
            .setNumperpage(100)
            .build();
    }

    public static PubFileItem download(long publishedFileId, String stagingPath) {
        return new PubFileItem(DD1SteamEvents.APP_ID, publishedFileId, false,
            stagingPath, false, false);
    }

    public static CPublishedFile_QueryFiles_Request query(String text, int sort, int page) {
        String search = text == null ? "" : text.trim();
        return CPublishedFile_QueryFiles_Request.newBuilder()
            .setQueryType(search.isEmpty() ? sort : 9)
            .setPage(Math.max(1, page))
            .setNumperpage(20)
            .setCreatorAppid(DD1SteamEvents.APP_ID)
            .setAppid(DD1SteamEvents.APP_ID)
            .setSearchText(search)
            .setReturnDetails(true)
            .setReturnVoteData(true)
            .setReturnShortDescription(true)
            .build();
    }

    public static CPublishedFile_GetDetails_Request details(long publishedFileId) {
        return CPublishedFile_GetDetails_Request.newBuilder()
            .addPublishedfileids(publishedFileId)
            .setAppid(DD1SteamEvents.APP_ID)
            .setIncludevotes(true)
            .setShortDescription(true)
            .build();
    }

    public static CPublishedFile_GetDetails_Request fullDetails(long publishedFileId) {
        return CPublishedFile_GetDetails_Request.newBuilder()
            .addPublishedfileids(publishedFileId)
            .setAppid(DD1SteamEvents.APP_ID)
            .setIncludevotes(true)
            .setIncludetags(true)
            .setIncludeadditionalpreviews(true)
            .setShortDescription(false)
            .build();
    }

    public static CPublishedFile_Subscribe_Request subscribe(long publishedFileId) {
        return CPublishedFile_Subscribe_Request.newBuilder()
            .setPublishedfileid(publishedFileId)
            .setAppid(DD1SteamEvents.APP_ID)
            .setListType(1)
            .setNotifyClient(true)
            .setIncludeDependencies(true)
            .build();
    }

    public static CPublishedFile_Unsubscribe_Request unsubscribe(long publishedFileId) {
        return CPublishedFile_Unsubscribe_Request.newBuilder()
            .setPublishedfileid(publishedFileId)
            .setAppid(DD1SteamEvents.APP_ID)
            .setListType(1)
            .setNotifyClient(true)
            .build();
    }

    public static long directId(String text) {
        if (text == null) return 0;
        String value = text.trim();
        try {
            if (value.matches("[0-9]+")) return positive(Long.parseLong(value));
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (host == null || !(host.equals("steamcommunity.com")
                    || host.equals("www.steamcommunity.com"))
                    || !"/sharedfiles/filedetails/".equals(uri.getPath())) return 0;
            String query = uri.getRawQuery();
            if (query == null) return 0;
            for (String part : query.split("&")) {
                String[] pair = part.split("=", 2);
                if (pair.length == 2 && pair[0].equals("id") && pair[1].matches("[0-9]+"))
                    return positive(Long.parseLong(pair[1]));
            }
        }
        catch (RuntimeException ignored) {}
        return 0;
    }

    public static List<DD1WorkshopItem> items(List<PublishedFileDetails> details) {
        List<DD1WorkshopItem> result = new ArrayList<>();
        for (PublishedFileDetails detail : details) result.add(item(detail, false));
        return result;
    }

    public static List<DD1WorkshopItem> fullItems(List<PublishedFileDetails> details) {
        List<DD1WorkshopItem> result = new ArrayList<>();
        for (PublishedFileDetails detail : details) result.add(item(detail, true));
        return result;
    }

    public static List<ModSyncPlan.Subscribed> fromDetails(List<PublishedFileDetails> details) {
        List<ModSyncPlan.Subscribed> result = new ArrayList<>();
        for (PublishedFileDetails detail : details) {
            boolean downloadable = detail.getResult() == EResult.OK.code()
                && detail.getConsumerAppid() == DD1SteamEvents.APP_ID
                && detail.getHcontentFile() != 0;
            String title = detail.getTitle().isEmpty()
                ? Long.toString(detail.getPublishedfileid()) : detail.getTitle();
            result.add(new ModSyncPlan.Subscribed(detail.getPublishedfileid(), title,
                Integer.toUnsignedLong(detail.getTimeUpdated()), downloadable));
        }
        return result;
    }

    private static long positive(long value) {
        return value > 0 ? value : 0;
    }

    private static DD1WorkshopItem item(PublishedFileDetails detail, boolean full) {
        boolean downloadable = detail.getResult() == EResult.OK.code()
            && detail.getConsumerAppid() == DD1SteamEvents.APP_ID
            && detail.getHcontentFile() != 0;
        String title = detail.getTitle().isEmpty()
            ? Long.toString(detail.getPublishedfileid()) : detail.getTitle();
        List<String> previews = new ArrayList<>();
        if (!detail.getPreviewUrl().isEmpty()) previews.add(detail.getPreviewUrl());
        if (full) {
            for (PublishedFileDetails.Preview preview : detail.getPreviewsList())
                if (!preview.getUrl().isEmpty() && !previews.contains(preview.getUrl()))
                    previews.add(preview.getUrl());
        }
        return new DD1WorkshopItem(detail.getPublishedfileid(), title,
            full ? detail.getFileDescription() : detail.getShortDescription(),
            detail.getPreviewUrl(), detail.getFileSize(), detail.getSubscriptions(),
            detail.hasVoteData() ? detail.getVoteData().getScore() : 0,
            Integer.toUnsignedLong(detail.getTimeUpdated()), downloadable, previews);
    }
}
