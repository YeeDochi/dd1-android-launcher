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

    public DD1CloudSaves(SteamCloud cloud, in.dragonbra.javasteam.rpc.service.Cloud cloudService) {
        this.cloud = cloud;
        this.cloudService = cloudService;
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
        byte[] content = fetchExactly(path);
        if (content != null) return content;
        int slash = path.lastIndexOf('/');
        return slash < 0 ? null : fetchExactly(path.substring(slash + 1));
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

    public boolean upload(File root, List<DD1SaveSummary.Entry> files) {
        if (!uploadable(files)) return false;
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
                        .build()).runBlock();
            if (begun.getResult() != EResult.OK) {
                Log.e("DD1Cloud", "Upload batch refused: " + begun.getResult());
                return false;
            }
            batch = begun.getBody().getBatchId();
            Log.i("DD1Cloud", "Batch " + batch + " for " + names.size() + " files, change="
                + begun.getBody().getAppChangeNumber());
        }
        catch (Exception refused) {
            Log.e("DD1Cloud", "Upload batch refused", refused);
            return false;
        }

        boolean allDone = true;
        for (DD1SaveSummary.Entry file : files) {
            if (!send(root, file, batch)) {
                allDone = false;
                break;
            }
        }
        try {
            // Telling Steam the batch failed is what stops a half-sent save from
            // becoming the version it hands back.
            cloud.completeAppUploadBatch(DD1SteamEvents.APP_ID, batch,
                allDone ? in.dragonbra.javasteam.enums.EResult.OK
                    : in.dragonbra.javasteam.enums.EResult.Fail,
                kotlinx.coroutines.GlobalScope.INSTANCE).get();
        }
        catch (Exception ignored) {
            Log.e("DD1Cloud", "Upload batch completion failed", ignored);
            return false;
        }
        return allDone;
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
                return false;
            }
            opened = true;
            FileUploadInfo info = new FileUploadInfo(begun.getBody());
            // An encrypted upload needs a key exchange this launcher does not do.
            // Guessing at it would write rubbish into the player's cloud.
            if (info.getEncryptFile()) return false;

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
                && response.getBody().getFileCommitted()))
            Log.e("DD1Cloud", "Commit rejected: " + path
                + " result=" + response.getResult()
                + " committed=" + response.getBody().getFileCommitted());
        return response.getResult();
    }

    private static boolean put(FileUploadBlockDetails block, byte[] content) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)new URL(DD1CloudTransfer.url(
                block.getUrlHost(), block.getUrlPath(), block.getUseHttps())).openConnection();
            connection.setRequestMethod("PUT");
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
