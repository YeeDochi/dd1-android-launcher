package com.winlator.dd1;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import in.dragonbra.javasteam.steam.handlers.steamcloud.AppFileChangeList;
import in.dragonbra.javasteam.steam.handlers.steamcloud.AppFileInfo;
import in.dragonbra.javasteam.steam.handlers.steamcloud.FileDownloadInfo;
import in.dragonbra.javasteam.steam.handlers.steamcloud.FileUploadBlockDetails;
import in.dragonbra.javasteam.steam.handlers.steamcloud.FileUploadInfo;
import in.dragonbra.javasteam.steam.handlers.steamcloud.HttpHeaders;
import in.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud;

// Steam Cloud: what it holds, a file out of it, and a batch of saves into it.
// Reading answers "we do not know" rather than throwing or coming back empty,
// because those two mean opposite things to the caller. Writing goes through one
// guarded method, so no path can skip the guard.
public final class DD1CloudSaves {
    private final SteamCloud cloud;

    public DD1CloudSaves(SteamCloud cloud) {
        this.cloud = cloud;
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
            if (info.isExplicitDelete()) return null;

            byte[] body = get(DD1CloudTransfer.url(info.getUrlHost(), info.getUrlPath(),
                info.getUseHttps()), info.getRequestHeaders());
            if (body == null) return null;
            byte[] content = DD1CloudTransfer.inflate(body, info.getRawFileSize());
            // The digest Steam gave is the only reason to trust these bytes.
            if (!DD1CloudTransfer.digestMatches(content, info.getShaFile())) return null;
            return content;
        }
        catch (Throwable failed) {
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
            batch = cloud.beginAppUploadBatch(DD1SteamEvents.APP_ID, "DD1 Android",
                names, Collections.<String>emptyList(), 0L, 0L,
                kotlinx.coroutines.GlobalScope.INSTANCE).get().getBatchID();
        }
        catch (Exception refused) {
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
            return false;
        }
        return allDone;
    }

    private boolean send(File root, DD1SaveSummary.Entry file, long batch) {
        try {
            byte[] content = read(new File(root, file.path));
            if (content == null || content.length == 0) return false;
            byte[] sha1 = MessageDigest.getInstance("SHA-1").digest(content);

            FileUploadInfo info = cloud.beginFileUpload(DD1SteamEvents.APP_ID,
                content.length, content.length, sha1, new Date(file.modifiedMillis),
                file.path, 0, 0, false, false, null, batch,
                kotlinx.coroutines.GlobalScope.INSTANCE).get();
            // An encrypted upload needs a key exchange this launcher does not do.
            // Guessing at it would write rubbish into the player's cloud.
            if (info.getEncryptFile()) return false;

            for (FileUploadBlockDetails block : info.getBlockRequests()) {
                if (!put(block, content)) {
                    cloud.commitFileUpload(false, DD1SteamEvents.APP_ID, sha1, file.path,
                        kotlinx.coroutines.GlobalScope.INSTANCE).get();
                    return false;
                }
            }
            return cloud.commitFileUpload(true, DD1SteamEvents.APP_ID, sha1, file.path,
                kotlinx.coroutines.GlobalScope.INSTANCE).get();
        }
        catch (Exception failed) {
            return false;
        }
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

    private static byte[] read(File file) {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int got;
            while ((got = in.read(buffer)) > 0) out.write(buffer, 0, got);
            return out.toByteArray();
        }
        catch (Exception unreadable) {
            return null;
        }
    }

    private static byte[] get(String url, List<HttpHeaders> headers) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)new URL(url).openConnection();
            connection.setRequestMethod("GET");
            for (HttpHeaders header : headers)
                connection.setRequestProperty(header.getName(), header.getValue());
            if (connection.getResponseCode() / 100 != 2) return null;
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
                return out.toByteArray();
            }
        }
        catch (Exception failed) {
            return null;
        }
        finally {
            if (connection != null) connection.disconnect();
        }
    }
}
