package com.winlator.dd1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import in.dragonbra.javasteam.enums.EResult;

public class DD1SteamSessionTest {
    // The three answers Steam gives a refresh token it will never accept again.
    // Anything else - a timeout, a busy server - deserves the ordinary error and
    // another try with the same token.
    @Test
    public void aTokenSteamHasThrownAwayIsRecognised() {
        assertTrue(DD1SteamSession.tokenRejected(EResult.InvalidPassword));
        assertTrue(DD1SteamSession.tokenRejected(EResult.AccessDenied));
        assertTrue(DD1SteamSession.tokenRejected(EResult.Expired));
    }

    @Test
    public void aPassingTroubleIsNotReadAsAThrownAwayToken() {
        assertFalse(DD1SteamSession.tokenRejected(EResult.OK));
        assertFalse(DD1SteamSession.tokenRejected(EResult.ServiceUnavailable));
        assertFalse(DD1SteamSession.tokenRejected(EResult.TryAnotherCM));
    }
}
