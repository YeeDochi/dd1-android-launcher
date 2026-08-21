package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import com.winlator.R;

import org.junit.Test;

public class DD1SteamEventsTest {
    private static final java.util.List<Integer> DLC =
        Arrays.asList(445700, 580100);

    @Test
    public void ownedPackagesEnableInstallOnlyAfterLoginAndPics() {
        DD1SteamEvents events = new DD1SteamEvents(id -> "message " + id);

        assertEquals(DD1InstallPhase.AUTHENTICATING, events.authStarted("url").phase);
        assertEquals(DD1InstallPhase.AUTHENTICATING, events.loggedOn().phase);
        assertEquals(DD1InstallPhase.READY_TO_INSTALL,
            events.packagesResolved(Collections.singletonMap(1, Arrays.asList(262060)), 0L, DLC).phase);
    }

    @Test
    public void unownedPackagesNeverEnableDownload() {
        DD1SteamEvents events = new DD1SteamEvents(id -> "message " + id);
        events.loggedOn();

        assertEquals(DD1InstallPhase.NOT_OWNED,
            events.packagesResolved(Collections.singletonMap(1, Arrays.asList(10)), 0L, DLC).phase);
    }

    @Test
    public void errorsDoNotExposeSecretText() {
        DD1SteamEvents events = new DD1SteamEvents(id -> "message " + id);

        DD1InstallSnapshot snapshot = events.failed("access_token=secret");

        assertEquals(DD1InstallPhase.ERROR, snapshot.phase);
        assertEquals("message " + R.string.dd1_state_failed, snapshot.message);
    }

    // The wait moves to the person; Steam has not failed and is not done, so the
    // phase must not move - the screen decides what to show from the prompt.
    @Test
    public void aCodeRequestKeepsAuthenticatingAndCarriesWhatToAsk() {
        DD1SteamEvents events = new DD1SteamEvents(id -> "message " + id);

        DD1InstallSnapshot snapshot =
            events.codeRequested(DD1SignInCode.email("a@b.com", false));

        assertEquals(DD1InstallPhase.AUTHENTICATING, snapshot.phase);
        assertEquals(DD1SignInCode.Source.EMAIL, snapshot.codePrompt.source);
        assertEquals("a@b.com", snapshot.codePrompt.emailHint);
        assertEquals(snapshot, events.snapshot());
    }

    @Test
    public void aRejectedCodeIsCarriedThroughSoTheScreenCanSaySo() {
        DD1SteamEvents events = new DD1SteamEvents(id -> "message " + id);

        DD1InstallSnapshot snapshot =
            events.codeRequested(DD1SignInCode.authenticator(true));

        assertEquals(DD1SignInCode.Source.AUTHENTICATOR, snapshot.codePrompt.source);
        assertTrue(snapshot.codePrompt.previousWasWrong);
    }
}
