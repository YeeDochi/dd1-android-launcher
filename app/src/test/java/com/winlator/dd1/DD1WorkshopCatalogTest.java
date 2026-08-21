package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.PublishedFileDetails;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_GetUserFiles_Request;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_QueryFiles_Request;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_GetDetails_Request;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_Subscribe_Request;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_Unsubscribe_Request;
import in.dragonbra.javasteam.depotdownloader.data.PubFileItem;

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
        assertEquals("mysubscriptions", request.getType());
        assertEquals(3, request.getPage());
        assertEquals(100, request.getNumperpage());
    }

    @Test
    public void downloadRequestTargetsOneWorkshopItemWithoutVerifyMode() {
        PubFileItem item = DD1WorkshopCatalog.download(42, "/staging/42");

        assertEquals(262060, item.getAppId());
        assertEquals(42L, item.getPubFile());
        assertEquals("/staging/42", item.getInstallDirectory());
        assertFalse(item.getInstallToGameNameDirectory());
        assertFalse(item.getVerify());
        assertFalse(item.getDownloadManifestOnly());
    }

    @Test
    public void browseRequestIsScopedAndSearchable() {
        CPublishedFile_QueryFiles_Request request = DD1WorkshopCatalog.query("skin", 0, 2);

        assertEquals(262060, request.getAppid());
        assertEquals("skin", request.getSearchText());
        assertEquals(2, request.getPage());
        assertEquals(20, request.getNumperpage());
        assertTrue(request.getReturnDetails());
        assertTrue(request.getReturnVoteData());
        assertTrue(request.getReturnShortDescription());
    }

    @Test
    public void workshopUrlAndBareIdResolveWithoutTreatingWordsAsIds() {
        assertEquals(123L, DD1WorkshopCatalog.directId("123"));
        assertEquals(123L, DD1WorkshopCatalog.directId(
            "https://steamcommunity.com/sharedfiles/filedetails/?id=123&searchtext=x"));
        assertEquals(0L, DD1WorkshopCatalog.directId("musketeer"));
        assertEquals(0L, DD1WorkshopCatalog.directId("https://example.com/?id=123"));
    }

    @Test
    public void detailBecomesAVisualWorkshopItem() {
        PublishedFileDetails detail = PublishedFileDetails.newBuilder()
            .setPublishedfileid(42)
            .setTitle("Musketeer")
            .setShortDescription("A hero")
            .setPreviewUrl("https://cdn/42.jpg")
            .setFileSize(1234)
            .setSubscriptions(77)
            .setTimeUpdated(9)
            .setConsumerAppid(262060)
            .setHcontentFile(99)
            .setResult(1)
            .build();

        DD1WorkshopItem item = DD1WorkshopCatalog.items(Arrays.asList(detail)).get(0);

        assertEquals(42L, item.publishedFileId);
        assertEquals("Musketeer", item.title);
        assertEquals("A hero", item.description);
        assertEquals("https://cdn/42.jpg", item.previewUrl);
        assertEquals(1234L, item.fileSize);
        assertEquals(77, item.subscriptions);
        assertTrue(item.downloadable);
    }

    @Test
    public void directDetailsAndSubscriptionRequestsStayScopedToDd1() {
        CPublishedFile_GetDetails_Request details = DD1WorkshopCatalog.details(42);
        CPublishedFile_Subscribe_Request subscribe = DD1WorkshopCatalog.subscribe(42);
        CPublishedFile_Unsubscribe_Request unsubscribe = DD1WorkshopCatalog.unsubscribe(42);

        assertEquals(42L, details.getPublishedfileids(0));
        assertEquals(262060, details.getAppid());
        assertTrue(details.getIncludevotes());
        assertEquals(42L, subscribe.getPublishedfileid());
        assertEquals(262060, subscribe.getAppid());
        assertEquals(42L, unsubscribe.getPublishedfileid());
        assertEquals(262060, unsubscribe.getAppid());
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
