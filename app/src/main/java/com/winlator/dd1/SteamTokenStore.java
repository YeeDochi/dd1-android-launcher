package com.winlator.dd1;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SteamTokenStore {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "dd1-steam-refresh";
    private static final String PREFS = "steam_session";
    private static final String ACCOUNT = "account";
    private static final String IV = "iv";
    private static final String CIPHERTEXT = "ciphertext";

    private final SharedPreferences preferences;

    public SteamTokenStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void save(String account, String token) {
        if (account == null || account.isEmpty() || token == null || token.isEmpty())
            throw new IllegalArgumentException("account and token are required");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] ciphertext = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            preferences.edit()
                .putString(ACCOUNT, account)
                .putString(IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString(CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .apply();
        }
        catch (Exception error) {
            throw new IllegalStateException("Unable to protect Steam session", error);
        }
    }

    public synchronized Session load() {
        String account = preferences.getString(ACCOUNT, null);
        String iv = preferences.getString(IV, null);
        String ciphertext = preferences.getString(CIPHERTEXT, null);
        if (account == null || iv == null || ciphertext == null) return null;
        try {
            KeyStore keyStore = loadKeyStore();
            SecretKey key = (SecretKey)keyStore.getKey(KEY_ALIAS, null);
            if (key == null) throw new IllegalStateException("missing key");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            String token = new String(cipher.doFinal(
                Base64.decode(ciphertext, Base64.NO_WRAP)), StandardCharsets.UTF_8);
            return new Session(account, token);
        }
        catch (Exception error) {
            clear();
            return null;
        }
    }

    public synchronized void clear() {
        preferences.edit().clear().apply();
        try {
            KeyStore keyStore = loadKeyStore();
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS);
        }
        catch (Exception ignored) {}
    }

    private static KeyStore loadKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        return keyStore;
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = loadKeyStore();
        SecretKey existing = (SecretKey)keyStore.getKey(KEY_ALIAS, null);
        if (existing != null) return existing;

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build());
        return generator.generateKey();
    }

    public static final class Session {
        public final String account;
        public final String token;

        private Session(String account, String token) {
            this.account = account;
            this.token = token;
        }
    }
}
