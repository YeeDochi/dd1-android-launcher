package com.winlator.dd1;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

// The launcher's own language. Android follows the system locale by default,
// which is right until someone wants a Korean phone to show an English menu or
// the other way round.
public final class DD1Locale {
    public static final String SYSTEM = "";

    private static final String PREFERENCES = "dd1";
    private static final String KEY = "language";

    private DD1Locale() {}

    // An empty or unknown tag means the system decides.
    public static Locale localeFor(String tag) {
        if (tag == null || tag.isEmpty()) return null;
        Locale locale = Locale.forLanguageTag(tag);
        return locale.getLanguage().isEmpty() ? null : locale;
    }

    public static String chosen(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY, SYSTEM);
    }

    public static void choose(Context context, String tag) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(KEY, tag == null ? SYSTEM : tag).apply();
    }

    public static Context wrap(Context base) {
        Locale locale = localeFor(chosen(base));
        if (locale == null) return base;
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(locale);
        return base.createConfigurationContext(configuration);
    }
}
