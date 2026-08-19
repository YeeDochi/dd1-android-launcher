package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SteamTokenStoreTest {
    @Test
    public void encryptsLoadsAndClearsRefreshToken() {
        Context context = ApplicationProvider.getApplicationContext();
        SteamTokenStore store = new SteamTokenStore(context);
        store.clear();

        store.save("owner", "secret-token");

        assertEquals("owner", store.load().account);
        assertEquals("secret-token", store.load().token);
        assertFalse(context.getSharedPreferences("steam_session", 0)
            .getString("ciphertext", "").contains("secret-token"));
        store.clear();
        assertNull(store.load());
    }
}
