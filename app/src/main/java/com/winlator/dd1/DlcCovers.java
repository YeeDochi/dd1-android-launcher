package com.winlator.dd1;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import com.winlator.core.StreamUtils;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Store artwork for the DLC list. It is fetched from Steam and kept in the cache
// directory only, never shipped with the launcher. The fetching, caching and
// downscaling is the Workshop image cache's job - the only thing particular to
// DLC is working out the address.
public final class DlcCovers {
    private static final String LEGACY_URL =
        "https://cdn.cloudflare.steamstatic.com/steam/apps/%d/header.jpg";
    // Newer store entries keep their artwork behind a hashed path, so the address
    // has to be asked for.
    private static final String DETAILS_URL =
        "https://store.steampowered.com/api/appdetails?appids=%d";
    private static ExecutorService worker;

    private static synchronized ExecutorService worker() {
        if (worker == null) worker = Executors.newFixedThreadPool(2);
        return worker;
    }
    private static Handler main;

    private static synchronized Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    private DlcCovers() {}

    public static void load(ImageView view, int appId) {
        Context context = view.getContext().getApplicationContext();
        view.setTag(appId);
        worker().execute(() -> {
            Bitmap bitmap = cover(context.getCacheDir(), appId);
            if (bitmap == null) return;
            main().post(() -> {
                if (Integer.valueOf(appId).equals(view.getTag())) view.setImageBitmap(bitmap);
            });
        });
    }

    private static Bitmap cover(File cacheDir, int appId) {
        Bitmap legacy = DD1WorkshopImages.fetch(cacheDir,
            String.format(Locale.US, LEGACY_URL, appId));
        if (legacy != null) return legacy;
        String resolved = headerUrl(appId);
        return resolved == null ? null : DD1WorkshopImages.fetch(cacheDir, resolved);
    }

    private static String headerUrl(int appId) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)new URL(
                String.format(Locale.US, DETAILS_URL, appId)).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(true);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (InputStream input = connection.getInputStream()) {
                return parseHeaderUrl(new String(StreamUtils.copyToByteArray(input),
                    StandardCharsets.UTF_8));
            }
        }
        catch (Exception offline) {
            return null;
        }
        finally {
            if (connection != null) connection.disconnect();
        }
    }

    // The response is a large JSON document and only one field is wanted. Read by
    // hand rather than through JSONObject so it stays testable off a device.
    static String parseHeaderUrl(String json) {
        String key = "\"header_image\":\"";
        int start = json.indexOf(key);
        if (start < 0) return null;
        start += key.length();
        int end = json.indexOf('"', start);
        if (end < 0) return null;
        return json.substring(start, end).replace("\\/", "/");
    }
}
