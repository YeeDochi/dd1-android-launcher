package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.security.MessageDigest;

import javax.crypto.Cipher;

import in.dragonbra.javasteam.util.crypto.CryptoHelper;
import kotlinx.coroutines.scheduling.TasksKt;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SteamCryptoCompatibilityTest {
    @Test
    public void javaSteamProviderSupportsDepotManifestCrypto() throws Exception {
        assertEquals("SC", CryptoHelper.SEC_PROV);
        assertNotNull(MessageDigest.getInstance("SHA-1", CryptoHelper.SEC_PROV));
        assertNotNull(Cipher.getInstance("AES/CBC/PKCS7Padding", CryptoHelper.SEC_PROV));
        assertEquals(2, TasksKt.CORE_POOL_SIZE);
        assertEquals(4, TasksKt.MAX_POOL_SIZE);
    }
}
