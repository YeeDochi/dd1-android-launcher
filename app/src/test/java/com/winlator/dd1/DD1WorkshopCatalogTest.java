package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.PublishedFileDetails;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_GetUserFiles_Request;

public class DD1WorkshopCatalogTest {
    @Test
    public void usableDetailBecomesDownloadableSubscription() {
        ModSyncPlan.Subscribed item = convert(detail(42, 262060, 99, 1));

        assertEquals(42L, item.publishedFileId);
        assertEquals("Musketeer", item.title);
        assertEquals(7L, item.updatedAt);
        assertTrue(item.downloadable);
    }

    @Test
    public void missingContentRemainsVisibleButCannotDownload() {
        assertFalse(convert(detail(42, 262060, 0, 1)).downloadable);
    }

    @Test
    public void anotherGamesItemCannotDownloadIntoDd1() {
        assertFalse(convert(detail(42, 999, 99, 1)).downloadable);
    }

    @Test
    public void failedDetailRemainsVisibleButCannotDownload() {
        assertFalse(convert(detail(42, 262060, 99, 9)).downloadable);
    }

    @Test
    public void conversionKeepsSteamOrder() {
        List<ModSyncPlan.Subscribed> items = DD1WorkshopCatalog.fromDetails(Arrays.asList(
            detail(2, 262060, 99, 1), detail(1, 262060, 99, 1)));

        assertEquals(2L, items.get(0).publishedFileId);
        assertEquals(1L, items.get(1).publishedFileId);
    }

    @Test
    public void subscriptionRequestIsScopedToDd1AndTheRequestedPage() {
        CPublishedFile_GetUserFiles_Request request = DD1WorkshopCatalog.request(765L, 3);

        assertEquals(765L, request.getSteamid());
        assertEquals(262060, request.getAppid());
        assertEquals("subscribed", request.getType());
        assertEquals(3, request.getPage());
        assertEquals(100, request.getNumperpage());
    }

    private static ModSyncPlan.Subscribed convert(PublishedFileDetails detail) {
        return DD1WorkshopCatalog.fromDetails(Arrays.asList(detail)).get(0);
    }

    private static PublishedFileDetails detail(long id, int appId, long content, int result) {
        return PublishedFileDetails.newBuilder()
            .setPublishedfileid(id)
            .setTitle("Musketeer")
            .setTimeUpdated(7)
            .setConsumerAppid(appId)
            .setHcontentFile(content)
            .setResult(result)
            .build();
    }
}
