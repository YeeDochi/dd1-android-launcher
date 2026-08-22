package com.winlator.dd1;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.winlator.core.StreamUtils;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// Tells the owner that a newer launcher has been released. Nothing is downloaded
// and nothing is sent: the one question asked is what the newest release is
// tagged, and the answer is a version number. Installing is the owner's own
// business, so the offer is a link to the release page.
public final class DD1Update {
    private static final String LATEST_RELEASE =
        "https://api.github.com/repos/YeeDochi/dd1-android-launcher/releases/latest";
    public static final String RELEASES_PAGE =
        "https://github.com/YeeDochi/dd1-android-launcher/releases/latest";
    private static final String HIDDEN_ON = "update_hidden_on";
    // Asking once when the app starts is the whole feature; a screen that is
    // built again on a rotation must not ask a second time.
    private static boolean asked;

    public interface Listener {
        void found(String version);
    }

    private DD1Update() {}

    public static void checkOnce(Context context, Listener listener) {
        if (asked) return;
        asked = true;
        Context app = context.getApplicationContext();
        if (hiddenToday(preferences(app), today())) return;
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String latest = parseTag(fetch());
            if (latest == null || !newerThan(installedVersion(app), latest)) return;
            main.post(() -> listener.found(latest));
        }, "dd1-update").start();
    }

    public static void hideForToday(Context context) {
        preferences(context.getApplicationContext()).edit()
            .putString(HIDDEN_ON, today()).apply();
    }

    static String installedVersion(Context context) {
        try {
            return context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0).versionName;
        }
        catch (Exception impossible) {
            return "";
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("dd1", Context.MODE_PRIVATE);
    }

    static boolean hiddenToday(SharedPreferences preferences, String today) {
        return today.equals(preferences.getString(HIDDEN_ON, ""));
    }

    // The day in the phone's own timezone, so "today" ends when the owner's day
    // ends rather than at some hour in the morning.
    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static String fetch() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection)new URL(LATEST_RELEASE).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(true);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (InputStream input = connection.getInputStream()) {
                return new String(StreamUtils.copyToByteArray(input), StandardCharsets.UTF_8);
            }
        }
        // Being offline is the ordinary case, not a fault worth reporting.
        catch (Exception offline) {
            return null;
        }
        finally {
            if (connection != null) connection.disconnect();
        }
    }

    // Read by hand rather than through JSONObject, for the same reason the DLC
    // artwork is: this way it can be tested without a device.
    static String parseTag(String json) {
        if (json == null) return null;
        String key = "\"tag_name\":";
        int start = json.indexOf(key);
        if (start < 0) return null;
        start = json.indexOf('"', start + key.length());
        if (start < 0) return null;
        int end = json.indexOf('"', ++start);
        if (end < 0) return null;
        String tag = json.substring(start, end).trim();
        // Releases are tagged v0.1.8; the version the app knows itself by is not.
        if (tag.startsWith("v") || tag.startsWith("V")) tag = tag.substring(1);
        return tag.isEmpty() ? null : tag;
    }

    // 0.1.10 is newer than 0.1.9, which is why the parts are compared as numbers
    // and not as text. Anything that is not a number reads as zero, so a tag
    // nobody expected cannot announce an update that does not exist.
    static boolean newerThan(String installed, String candidate) {
        String[] mine = installed.split("[^0-9]+");
        String[] theirs = candidate.split("[^0-9]+");
        for (int i = 0; i < Math.max(mine.length, theirs.length); i++) {
            int a = number(mine, i);
            int b = number(theirs, i);
            if (a != b) return b > a;
        }
        return false;
    }

    private static int number(String[] parts, int index) {
        if (index >= parts.length) return 0;
        try {
            return Integer.parseInt(parts[index]);
        }
        catch (NumberFormatException notANumber) {
            return 0;
        }
    }
}
