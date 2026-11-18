package com.winlator.dd1;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import java.io.File;

// The one public folder a person keeps things for the game in: DD1/ by default,
// or another they choose on the mod manager. Inside it the launcher makes mods/
// (copied into the game at Play and when the mod screen opens) and dll/ (laid
// over the game at Play, DD1Overrides).
//
// Before this the choice was a mods folder, with replacement files fixed at
// DD1/overrides. A mods folder chosen then is still read as one until the person
// chooses again, and DD1/overrides becomes DD1/dll the first time it is asked for.
public final class DD1Folders {
    static final String BASE_KEY = "dd1_folder";
    static final String LEGACY_MODS_KEY = "mods_folder";
    static final String MODS = "mods";
    static final String DLL = "dll";
    static final String LEGACY_DLL = "overrides";

    private DD1Folders() {}

    public static File defaultBase() {
        return new File(Environment.getExternalStorageDirectory(), "DD1");
    }

    public static File base(Context context) {
        String chosen = preferences(context).getString(BASE_KEY, null);
        return chosen == null ? defaultBase() : new File(chosen);
    }

    public static File mods(Context context) {
        SharedPreferences preferences = preferences(context);
        return mods(preferences.getString(BASE_KEY, null),
            preferences.getString(LEGACY_MODS_KEY, null), defaultBase());
    }

    // Pure, for the tests: a chosen DD1 folder wins, then a mods folder chosen
    // before there was one, then the default.
    static File mods(String base, String legacyMods, File defaultBase) {
        if (base != null) return new File(base, MODS);
        if (legacyMods != null) return new File(legacyMods);
        return new File(defaultBase, MODS);
    }

    public static File dll(Context context) {
        return dll(base(context));
    }

    // DD1/overrides from before the rename is moved, not copied, so the files a
    // person put there are where the screen says they are; it is only done when
    // nothing named dll is there yet.
    static File dll(File base) {
        File dll = new File(base, DLL);
        File legacy = new File(base, LEGACY_DLL);
        if (!dll.exists() && legacy.isDirectory()) legacy.renameTo(dll);
        return dll;
    }

    // Anything chosen by hand, now or before: the game's own mods folder is then
    // not the place mods go, even with the game in phone storage.
    public static boolean chosenByHand(Context context) {
        SharedPreferences preferences = preferences(context);
        return preferences.getString(BASE_KEY, null) != null
            || preferences.getString(LEGACY_MODS_KEY, null) != null;
    }

    // The folder is made ready as it is chosen: mods/ and dll/ inside, so the
    // person sees where things go the first time they open it.
    public static void choose(Context context, File base) {
        new File(base, MODS).mkdirs();
        dll(base).mkdirs();
        DD1GameLocation.noMedia(new File(base, DLL));
        preferences(context).edit().putString(BASE_KEY, base.getPath())
            .remove(LEGACY_MODS_KEY).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("dd1", Context.MODE_PRIVATE);
    }
}
