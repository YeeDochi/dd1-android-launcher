package com.winlator.dd1;

import com.winlator.core.FileUtils;
import com.winlator.core.StreamUtils;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.util.Log;

import in.dragonbra.javasteam.steam.handlers.steamcloud.AppFileChangeList;
import in.dragonbra.javasteam.steam.handlers.steamcloud.AppFileInfo;
import in.dragonbra.javasteam.steam.handlers.steamcloud.FileDownloadInfo;
import in.dragonbra.javasteam.steam.handlers.steamcloud.FileUploadBlockDetails;
import in.dragonbra.javasteam.steam.handlers.steamcloud.FileUploadInfo;
import in.dragonbra.javasteam.steam.handlers.steamcloud.HttpHeaders;
import in.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud;
import in.dragonbra.javasteam.steam.handlers.steamunifiedmessages.callback.ServiceMethodResponse;
import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesCloudSteamclient.CCloud_BeginAppUploadBatch_Request;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesCloudSteamclient.CCloud_BeginAppUploadBatch_Response;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesCloudSteamclient.CCloud_ClientBeginFileUpload_Request;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesCloudSteamclient.CCloud_ClientBeginFileUpload_Response;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesCloudSteamclient.CCloud_ClientCommitFileUpload_Request;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesCloudSteamclient.CCloud_CompleteAppUploadBatch_Request;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesCloudSteamclient.CCloud_CompleteAppUploadBatch_Response;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesCloudSteamclient.CCloud_ClientCommitFileUpload_Response;

// Steam Cloud: what it holds, a file out of it, and a batch of saves into it.
// Reading answers "we do not know" rather than throwing or coming back empty,
// because those two mean opposite things to the caller. Writing goes through one
// guarded method, so no path can skip the guard.
public final class DD1CloudSaves {
    // Steam's protocol defines 0 as "download on no platform". All is -1.
    static final int PLATFORMS_TO_SYNC = -1;

    private final SteamCloud cloud;
    private final in.dragonbra.javasteam.rpc.service.Cloud cloudService;

    // Where the batch being sent is written down until Steam has been told it
    // is over. A launcher killed mid-upload never told it, Steam kept every such
    // batch open, and after enough of them refused all uploads with
    // TooManyPending - with nothing on the phone that still knew which batches
    // they were. Null where nothing is kept (tests).
    private final File openBatch;

    public DD1CloudSaves(SteamCloud cloud, in.dragonbra.javasteam.rpc.service.Cloud cloudService) {
        this(cloud, cloudService, null);
    }

    public DD1CloudSaves(SteamCloud cloud, in.dragonbra.javasteam.rpc.service.Cloud cloudService,
            File openBatch) {
        this.cloud = cloud;
        this.cloudService = cloudService;
        this.openBatch = openBatch;
    }

    // At sign-in: a batch a previous run opened and never closed is closed now,
    // as failed, so it stops counting against the account.
    public void closeAbandonedBatch() {
        if (openBatch == null || !openBatch.isFile()) return;
        long batch;
        try {
            batch = Long.parseLong(new String(FileUtils.read(openBatch), "UTF-8").trim());
        }
        catch (Exception unreadable) {
            openBatch.delete();
            return;
        }
        EResult result = completeBatch(batch, false);
        Log.i("DD1Cloud", "Closed batch " + batch + " left open by an earlier run: " + result);
        if (result != null) openBatch.delete();
    }

    private void rememberBatch(long batch) {
        if (openBatch == null) return;
        try {
            File parent = openBatch.getParentFile();
            if (parent != null) parent.mkdirs();
            FileUtils.writeString(openBatch, Long.toString(batch));
        }
        catch (Exception ignored) {
            Log.e("DD1Cloud", "Could not note batch " + batch, ignored);
        }
    }

    // Called on the service directly: SteamCloud's own completeAppUploadBatch
    // throws Steam's answer away, which is how a refused completion went unseen.
    private EResult completeBatch(long batch, boolean succeeded) {
        try {
            ServiceMethodResponse<CCloud_CompleteAppUploadBatch_Response.Builder> done =
                cloudService.completeAppUploadBatchBlocking(
                    CCloud_CompleteAppUploadBatch_Request.newBuilder()
                        .setAppid(DD1SteamEvents.APP_ID)
                        .setBatchId(batch)
                        .setBatchEresult((succeeded ? EResult.OK : EResult.Fail).code())
                        .build()).runBlock();
            if (done.getResult() != EResult.OK)
                Log.e("DD1Cloud", "Completing batch " + batch + ": " + done.getResult());
            return done.getResult();
        }
        catch (Exception failed) {
            Log.e("DD1Cloud", "Completing batch " + batch + " failed", failed);
            return null;
        }
    }

    public DD1CloudListing list() {
        try {
            AppFileChangeList changes = cloud.getAppFileListChange(
                DD1SteamEvents.APP_ID, 0L,
                kotlinx.coroutines.GlobalScope.INSTANCE).get();
            List<String> prefixes = changes.getPathPrefixes();
            List<DD1SaveSummary.Entry> files = new ArrayList<>();
            for (AppFileInfo file : changes.getFiles()) {
                files.add(DD1CloudListing.entry(name(prefixes, file),
                    file.getRawFileSize(), file.getShaFile(),
                    file.getTimestamp() == null ? 0L : file.getTimestamp().getTime()));
            }
            return DD1CloudListing.of(changes.getCurrentChangeNumber(), files);
        }
        catch (Throwable unreadable) {
            return DD1CloudListing.unknown();
        }
    }

    // Steam reports a bare filename and the index of the prefix it belongs under,
    // and that index is 0-based and correct: with two slots up there the listing
    // came back 16 files at index 0 and 8 at index 1, matching profile_0 and
    // profile_1 exactly.
    //
    // Measured the hard way. With only one slot in the cloud every file read
    // index 0, which looked like the index being unset; taking the first prefix
    // for everything then worked by luck and mislabelled every file of the second
    // slot as the first's the moment there were two. The two files Steam keeps in
    // the tree's root also report index 0, so they are named as though they were
    // inside profile_0 - DD1SaveSlots decides that from the local tree instead.
    private static String name(List<String> prefixes, AppFileInfo file) {
        int index = file.getPathPrefixIndex();
        if (index < 0 || index >= prefixes.size()) return file.getFilename();
        return prefixes.get(index) + file.getFilename();
    }

    // A name the listing got wrong costs one failed request, and the digest check
    // means a wrong file can never be mistaken for a right one.
    public byte[] fetch(String path) {
        noContent = false;
        byte[] content = fetchExactly(path);
        if (content != null) return content;
        boolean listedEmpty = noContent;
        int slash = path.lastIndexOf('/');
        content = slash < 0 ? null : fetchExactly(path.substring(slash + 1));
        noContent = content == null && listedEmpty;
        return content;
    }

    // Steam lists a file whose upload was begun and never committed - the
    // launcher killed mid-batch - and answers a download of it with no address
    // at all. That is not a failed transfer to retry: there is nothing there.
    private volatile boolean noContent;

    public boolean lastHadNoContent() {
        return noContent;
    }

    private byte[] fetchExactly(String path) {
        try {
            FileDownloadInfo info = cloud.clientFileDownload(
                DD1SteamEvents.APP_ID, path,
                in.dragonbra.javasteam.enums.ESteamRealm.SteamGlobal, false,
                kotlinx.coroutines.GlobalScope.INSTANCE).get();
            if (info.isExplicitDelete()) {
                Log.e("DD1Cloud", "Fetch " + path + ": explicit delete");
                return null;
            }

            if (info.getUrlHost() == null || info.getUrlHost().isEmpty()) {
                Log.e("DD1Cloud", "Fetch " + path + ": listed, but Steam holds no content");
                noContent = true;
                return null;
            }
            byte[] body = get(DD1CloudTransfer.url(info.getUrlHost(), info.getUrlPath(),
                info.getUseHttps()), info.getRequestHeaders());
            if (body == null) {
                Log.e("DD1Cloud", "Fetch " + path + ": no body");
                return null;
            }
            byte[] content = DD1CloudTransfer.inflate(body, info.getRawFileSize());
            // The digest Steam gave is the only reason to trust these bytes.
            if (!DD1CloudTransfer.digestMatches(content, info.getShaFile())) {
                Log.e("DD1Cloud", "Fetch " + path + ": digest mismatch, body "
                    + body.length + " raw " + info.getRawFileSize()
                    + " inflated " + content.length);
                return null;
            }
            return content;
        }
        catch (Throwable failed) {
            Log.e("DD1Cloud", "Fetch " + path + " failed", failed);
            return null;
        }
    }

    // The single funnel every cloud write goes through. An empty set empties the
    // cloud, and a zero-length file is what an interrupted write leaves behind;
    // both have already cost somebody their progress, here and elsewhere. One bad
    // file stops the set, because half a save is a save nobody can load.
    public static boolean uploadable(List<DD1SaveSummary.Entry> files) {
        if (files.isEmpty()) return false;
        for (DD1SaveSummary.Entry file : files) {
            if (file.length <= 0) return false;
            if (!DD1SaveSummary.acceptable(file)) return false;
        }
        return true;
    }

    // Why the last upload stopped, in Steam's words where Steam gave one, for the
    // saves screen: "sync failed" alone sent people turning things off and on.
    private volatile String lastFailure;

    public String lastFailure() {
        return lastFailure;
    }

    public boolean upload(File root, List<DD1SaveSummary.Entry> files) {
        return upload(root, files, java.util.Collections.emptyList());
    }

    // stale: the slot's files that the cloud has and this phone does not
    // (DD1SaveSlots.staleInCloud). They are named to Steam with the batch, as its
    // own client does, and deleted only once every file has gone up - a batch
    // that stops halfway must not leave the cloud with less than it had.
    public boolean upload(File root, List<DD1SaveSummary.Entry> files, List<String> stale) {
        return upload(root, files, stale, null);
    }

    // sent: told the count of files that have gone up, after each one.
    private volatile boolean tooManyPending;

    // Steam is holding uploads that were never finished and takes no more until
    // it lets them go - not something sending again will fix.
    public boolean refusedForPending() {
        return tooManyPending;
    }

    public boolean upload(File root, List<DD1SaveSummary.Entry> files, List<String> stale,
            java.util.function.IntConsumer sent) {
        lastFailure = null;
        tooManyPending = false;
        if (!uploadable(files)) {
            lastFailure = "nothing safe to send";
            return false;
        }
        List<String> names = new ArrayList<>();
        for (DD1SaveSummary.Entry file : files) names.add(file.path);

        long batch;
        try {
            ServiceMethodResponse<CCloud_BeginAppUploadBatch_Response.Builder> begun =
                cloudService.beginAppUploadBatch(
                    CCloud_BeginAppUploadBatch_Request.newBuilder()
                        .setAppid(DD1SteamEvents.APP_ID)
                        .setMachineName("DD1 Android")
                        .addAllFilesToUpload(names)
                        .addAllFilesToDelete(stale)
                        .build()).runBlock();
            if (begun.getResult() != EResult.OK) {
                Log.e("DD1Cloud", "Upload batch refused: " + begun.getResult());
                lastFailure = "Steam refused the upload: " + begun.getResult();
                if (begun.getResult() == EResult.TooManyPending) tooManyPending = true;
                return false;
            }
            batch = begun.getBody().getBatchId();
            rememberBatch(batch);
            Log.i("DD1Cloud", "Batch " + batch + " for " + names.size() + " files, change="
                + begun.getBody().getAppChangeNumber());
        }
        catch (Exception refused) {
            Log.e("DD1Cloud", "Upload batch refused", refused);
            lastFailure = "Steam did not answer: " + refused.getMessage();
            return false;
        }

        boolean allDone = true;
        int count = 0;
        for (DD1SaveSummary.Entry file : files) {
            if (!send(root, file, batch)) {
                allDone = false;
                if (lastFailure == null) lastFailure = "could not send " + file.path;
                break;
            }
            if (sent != null) sent.accept(++count);
        }
        if (allDone) {
            for (String path : stale) {
                if (!delete(path, batch)) {
                    allDone = false;
                    lastFailure = "could not remove " + path + " from the cloud";
                    break;
                }
            }
        }
        // Telling Steam the batch failed is what stops a half-sent save from
        // becoming the version it hands back.
        EResult closed = completeBatch(batch, allDone);
        if (closed != null && openBatch != null) openBatch.delete();
        if (closed != EResult.OK) {
            if (lastFailure == null) lastFailure = "Steam did not close the upload: " + closed;
            return false;
        }
        return allDone;
    }

    private boolean delete(String path, long batch) {
        try {
            EResult result = cloudService.clientDeleteFile(
                in.dragonbra.javasteam.protobufs.steamclient.SteammessagesCloudSteamclient
                    .CCloud_ClientDeleteFile_Request.newBuilder()
                    .setAppid(DD1SteamEvents.APP_ID)
                    .setFilename(path)
                    .setIsExplicitDelete(true)
                    .setUploadBatchId(batch)
                    .build()).runBlock().getResult();
            Log.i("DD1Cloud", "Delete " + path + " -> " + result);
            // Already gone is what was asked for.
            return result == EResult.OK || result == EResult.FileNotFound;
        }
        catch (Exception failed) {
            Log.e("DD1Cloud", "Delete failed: " + path, failed);
            return false;
        }
    }

    private boolean send(File root, DD1SaveSummary.Entry file, long batch) {
        byte[] sha1 = null;
        boolean opened = false;
        try {
            byte[] content = FileUtils.read(new File(root, file.path));
            if (content == null || content.length == 0) return false;
            sha1 = MessageDigest.getInstance("SHA-1").digest(content);

            ServiceMethodResponse<CCloud_ClientBeginFileUpload_Response.Builder> begun =
                begin(content, sha1, file, batch);
            // Steam answers a file it already holds at this digest with
            // DuplicateRequest: there is nothing to send, which is the same thing
            // as having sent it. Read as a failure it stopped the batch at the
            // first file that had not changed - and the file that sorts first in a
            // slot is one that almost never changes, so no save ever went up.
            if (begun.getResult() == EResult.DuplicateRequest) {
                Log.i("DD1Cloud", "Already in the cloud: " + file.path);
                return true;
            }
            if (begun.getResult() != EResult.OK) {
                Log.e("DD1Cloud", "Begin refused: " + file.path
                    + " result=" + begun.getResult());
                lastFailure = "Steam refused " + file.path + ": " + begun.getResult();
                return false;
            }
            opened = true;
            FileUploadInfo info = new FileUploadInfo(begun.getBody());
            // An encrypted upload needs a key exchange this launcher does not do.
            // Guessing at it would write rubbish into the player's cloud.
            if (info.getEncryptFile()) {
                lastFailure = "Steam asked for an encrypted upload, which the launcher does not do";
                return false;
            }

            Log.i("DD1Cloud", "Begin " + file.path + " size=" + content.length
                + " blocks=" + info.getBlockRequests().size());

            for (FileUploadBlockDetails block : info.getBlockRequests()) {
                if (!put(block, content)) return false;
            }
            boolean committed = commit(true, sha1, file.path);
            opened = !committed;
            return committed;
        }
        catch (Exception failed) {
            Log.e("DD1Cloud", "Upload failed: " + file.path, failed);
            return false;
        }
        finally {
            // Whatever went wrong, the upload this method opened does not stay
            // open: the next attempt has to be able to start one.
            if (opened) abort(sha1, file.path);
        }
    }

    private ServiceMethodResponse<CCloud_ClientBeginFileUpload_Response.Builder> begin(
            byte[] content, byte[] sha1, DD1SaveSummary.Entry file, long batch) {
        return cloudService.clientBeginFileUpload(
            CCloud_ClientBeginFileUpload_Request.newBuilder()
                .setAppid(DD1SteamEvents.APP_ID)
                .setFileSize(content.length)
                .setRawFileSize(content.length)
                .setFileSha(com.google.protobuf.ByteString.copyFrom(sha1))
                .setTimeStamp(file.modifiedMillis / 1000L)
                .setFilename(file.path)
                .setPlatformsToSync(PLATFORMS_TO_SYNC)
                .setCellId(0)
                .setCanEncrypt(false)
                .setIsSharedFile(false)
                .setUploadBatchId(batch)
                .build()).runBlock();
    }

    // Telling Steam the transfer failed is how an upload is given back. It is
    // not news that anything went wrong - the caller already knows - so its own
    // failure is not worth stopping for.
    private void abort(byte[] sha1, String path) {
        try {
            Log.i("DD1Cloud", "Abort " + path + " -> " + commitResult(false, sha1, path));
        }
        catch (Exception ignored) {
            Log.e("DD1Cloud", "Could not close the upload for " + path, ignored);
        }
    }

    // A rejected commit and a failed request both come back as "not committed"
    // from SteamCloud, which drops the result Steam sent with it. Which of the two
    // it is decides whether the fix is here or in what we asked for.
    private boolean commit(boolean transferSucceeded, byte[] sha1, String path)
            throws Exception {
        return commitResult(transferSucceeded, sha1, path) == EResult.OK;
    }

    private EResult commitResult(boolean transferSucceeded, byte[] sha1, String path)
            throws Exception {
        ServiceMethodResponse<CCloud_ClientCommitFileUpload_Response.Builder> response =
            cloudService.clientCommitFileUpload(
                CCloud_ClientCommitFileUpload_Request.newBuilder()
                    .setTransferSucceeded(transferSucceeded)
                    .setAppid(DD1SteamEvents.APP_ID)
                    .setFileSha(com.google.protobuf.ByteString.copyFrom(sha1))
                    .setFilename(path)
                    .build()).runBlock();
        if (transferSucceeded && !(response.getResult() == EResult.OK
                && response.getBody().getFileCommitted())) {
            lastFailure = "Steam did not keep " + path + ": " + response.getResult();
            Log.e("DD1Cloud", "Commit rejected: " + path
                + " result=" + response.getResult()
                + " committed=" + response.getBody().getFileCommitted());
        }
        return response.getResult();
    }

    // Without them a stalled connection held a cloud batch open for as long as
    // the app lived. A save file is a few hundred kilobytes at most.
    private static final int CONNECT_MILLIS = 20_000;
    private static final int READ_MILLIS = 60_000;

    private static boolean put(FileUploadBlockDetails block, byte[] content) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)new URL(DD1CloudTransfer.url(
                block.getUrlHost(), block.getUrlPath(), block.getUseHttps())).openConnection();
            connection.setRequestMethod("PUT");
            connection.setConnectTimeout(CONNECT_MILLIS);
            connection.setReadTimeout(READ_MILLIS);
            connection.setDoOutput(true);
            for (HttpHeaders header : block.getRequestHeaders())
                connection.setRequestProperty(header.getName(), header.getValue());
            byte[] body = block.getExplicitBodyData();
            if (body == null || body.length == 0) {
                int offset = (int)block.getBlockOffset();
                int length = Math.min(block.getBlockLength(), content.length - offset);
                body = Arrays.copyOfRange(content, offset, offset + length);
            }
            connection.getOutputStream().write(body);
            connection.getOutputStream().flush();
            return connection.getResponseCode() / 100 == 2;
        }
        catch (Exception failed) {
            return false;
        }
        finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static byte[] get(String url, List<HttpHeaders> headers) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_MILLIS);
            connection.setReadTimeout(READ_MILLIS);
            for (HttpHeaders header : headers)
                connection.setRequestProperty(header.getName(), header.getValue());
            int status = connection.getResponseCode();
            if (status / 100 != 2) {
                Log.e("DD1Cloud", "GET " + url + " -> " + status);
                return null;
            }
            try (InputStream in = connection.getInputStream()) {
                return StreamUtils.copyToByteArray(in);
            }
        }
        catch (Exception failed) {
            Log.e("DD1Cloud", "GET " + url + " failed", failed);
            return null;
        }
        finally {
            if (connection != null) connection.disconnect();
        }
    }
}
