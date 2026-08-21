package com.winlator.dd1;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class DD1WorkshopImages {
    private static final long MAX_BYTES = 8L * 1024 * 1024;

    private DD1WorkshopImages() {}

    public static File file(File cacheDir, String url) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (byte value : hash) name.append(String.format("%02x", value & 0xff));
            return new File(new File(cacheDir, "dd1-workshop-images"), name + ".img");
        }
        catch (Exception impossible) {
            throw new AssertionError(impossible);
        }
    }

    public static Bitmap load(File cacheDir, String url) {
        File cached = file(cacheDir, url);
        return cached.isFile() ? decode(cached) : null;
    }

    // The staging name carries the thread, so two images can be fetched at once.
    // Two threads after the same image both download it; the rename is atomic and
    // the loser only wasted bytes.
    public static Bitmap fetch(File cacheDir, String url) {
        if (url == null || url.isEmpty()) return null;
        Bitmap cached = load(cacheDir, url);
        if (cached != null) return cached;
        File target = file(cacheDir, url);
        File partial = new File(target.getPath() + "." + Thread.currentThread().getId() + ".part");
        HttpURLConnection connection = null;
        try {
            URL current = checked(url);
            for (int redirects = 0; redirects < 5; redirects++) {
                connection = (HttpURLConnection)current.openConnection();
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(10_000);
                connection.setInstanceFollowRedirects(false);
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    connection.disconnect();
                    connection = null;
                    current = checked(new URL(current, location).toString());
                    continue;
                }
                if (status != HttpURLConnection.HTTP_OK
                        || connection.getContentLengthLong() > MAX_BYTES) return null;
                if (!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs())
                    return null;
                try (InputStream input = connection.getInputStream();
                        OutputStream output = new FileOutputStream(partial)) {
                    copy(input, output, MAX_BYTES);
                }
                Bitmap bitmap = decode(partial);
                if (bitmap == null || !partial.renameTo(target)) return null;
                return bitmap;
            }
        }
        catch (Exception ignored) {}
        finally {
            if (connection != null) connection.disconnect();
            if (partial.exists()) partial.delete();
        }
        return null;
    }

    static void copy(InputStream input, OutputStream output, long maxBytes) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) throw new IOException("Workshop image is too large");
            output.write(buffer, 0, read);
        }
    }

    private static URL checked(String value) throws Exception {
        URL url = new URL(value);
        if (!"http".equals(url.getProtocol()) && !"https".equals(url.getProtocol()))
            throw new IOException("Unsupported Workshop image URL");
        return url;
    }

    private static Bitmap decode(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (bounds.outWidth / options.inSampleSize > 1024
                || bounds.outHeight / options.inSampleSize > 1024)
            options.inSampleSize *= 2;
        return BitmapFactory.decodeFile(file.getPath(), options);
    }
}
