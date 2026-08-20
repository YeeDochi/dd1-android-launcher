package com.winlator.dd1;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import in.dragonbra.javasteam.steam.handlers.steamcloud.AppFileChangeList;
import in.dragonbra.javasteam.steam.handlers.steamcloud.AppFileInfo;
import in.dragonbra.javasteam.steam.handlers.steamcloud.FileDownloadInfo;
import in.dragonbra.javasteam.steam.handlers.steamcloud.HttpHeaders;
import in.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud;

// Steam Cloud, as far as reading goes. Every failure comes back as "we do not
// know" rather than as an exception or an empty answer, because the caller's
// next move depends on telling those apart.
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

    // The listing was measured with no prefixes at all, but Steam may send them,
    // and then a name alone is not a path.
    private static String name(List<String> prefixes, AppFileInfo file) {
        int index = file.getPathPrefixIndex();
        if (index < 0 || index >= prefixes.size()) return file.getFilename();
        return prefixes.get(index) + file.getFilename();
    }

    public byte[] fetch(String path) {
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
