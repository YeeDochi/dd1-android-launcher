package com.winlator.dd1;

import java.util.ArrayList;
import java.util.List;

import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.PublishedFileDetails;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_GetUserFiles_Request;

public final class DD1WorkshopCatalog {
    private DD1WorkshopCatalog() {}

    public static CPublishedFile_GetUserFiles_Request request(long steamId, int page) {
        return CPublishedFile_GetUserFiles_Request.newBuilder()
            .setSteamid(steamId)
            .setAppid(DD1SteamEvents.APP_ID)
            .setType("subscribed")
            .setPage(page)
            .setNumperpage(100)
            .build();
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
}
