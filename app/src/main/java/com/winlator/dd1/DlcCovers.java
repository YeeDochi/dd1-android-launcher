package com.winlator.dd1;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Store artwork for the DLC list. It is fetched from Steam and kept in the cache
// directory only, never shipped with the launcher.
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
            Bitmap bitmap = read(cacheFile(context, appId));
            if (bitmap == null) bitmap = download(context, appId);
            if (bitmap == null) return;
            Bitmap loaded = bitmap;
            main().post(() -> {
                if (Integer.valueOf(appId).equals(view.getTag())) view.setImageBitmap(loaded);
            });
        });
    }

    private static File cacheFile(Context context, int appId) {
        return new File(context.getCacheDir(), "dlc-" + appId + ".jpg");
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setInstanceFollowRedirects(true);
        return connection;
    }

    static String headerUrl(int appId) {
        HttpURLConnection connection = null;
        try {
            connection = open(String.format(java.util.Locale.US, DETAILS_URL, appId));
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            StringBuilder body = new StringBuilder();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = input.read(buffer)) > 0) body.append(new String(buffer, 0, read));
            }
            return parseHeaderUrl(body.toString());
        }
        catch (Exception offline) {
            return null;
        }
        finally {
            if (connection != null) connection.disconnect();
        }
    }

    // The response is a large JSON document and only one field is wanted.
    static String parseHeaderUrl(String json) {
        String key = "\"header_image\":\"";
        int start = json.indexOf(key);
        if (start < 0) return null;
        start += key.length();
        int end = json.indexOf('"', start);
        if (end < 0) return null;
        return json.substring(start, end).replace("\\/", "/");
    }

    private static Bitmap read(File file) {
        return file.isFile() ? BitmapFactory.decodeFile(file.getPath()) : null;
    }

    private static Bitmap download(Context context, int appId) {
        File target = cacheFile(context, appId);
        HttpURLConnection connection = null;
        try {
            connection = open(String.format(java.util.Locale.US, LEGACY_URL, appId));
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                connection.disconnect();
                String resolved = headerUrl(appId);
                if (resolved == null) return null;
                connection = open(resolved);
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            }
            try (InputStream input = connection.getInputStream();
                 OutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = input.read(buffer)) > 0) output.write(buffer, 0, read);
            }
            return read(target);
        }
        catch (Exception offline) {
            target.delete();
            return null;
        }
        finally {
            if (connection != null) connection.disconnect();
        }
    }
}
