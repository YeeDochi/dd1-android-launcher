package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class DD1CodeRequestTest {
    @Test
    public void whatSteamAskedForIsWhatTheScreenIsToldToAsk() {
        DD1CodeRequest request = new DD1CodeRequest();
        request.open(DD1SignInCode.email("a@b.com", false));

        assertEquals(DD1SignInCode.Source.EMAIL, request.prompt().source);
        assertEquals("a@b.com", request.prompt().emailHint);
        assertFalse(request.prompt().previousWasWrong);
    }

    @Test
    public void theTypedCodeIsWhatSteamGets() throws Exception {
        DD1CodeRequest request = new DD1CodeRequest();
        CompletableFuture<String> waiting = request.open(DD1SignInCode.authenticator(false));

        assertTrue(request.submit("  K7B2Q "));

        assertEquals("K7B2Q", waiting.get(1, TimeUnit.SECONDS));
        assertNull("nothing is being asked any more", request.prompt());
    }

    // Steam is on the other end of this waiting; an empty box must not be sent as
    // an answer, and the request has to stay open for a real one.
    @Test
    public void anEmptyBoxIsNotAnAnswer() {
        DD1CodeRequest request = new DD1CodeRequest();
        CompletableFuture<String> waiting = request.open(DD1SignInCode.email("a@b.com", false));

        assertFalse(request.submit("   "));
        assertFalse(request.submit(null));

        assertFalse(waiting.isDone());
        assertEquals(DD1SignInCode.Source.EMAIL, request.prompt().source);
    }

    @Test
    public void aCodeNobodyAskedForIsIgnored() {
        DD1CodeRequest request = new DD1CodeRequest();

        assertFalse(request.submit("K7B2Q"));
        assertNull(request.prompt());
    }

    // Cancelling has to reach Steam's side, or the thread waiting on the code
    // waits for good and the sign-in can never be started again.
    @Test
    public void cancellingReleasesWhatWasWaiting() {
        DD1CodeRequest request = new DD1CodeRequest();
        CompletableFuture<String> waiting = request.open(DD1SignInCode.email("a@b.com", false));

        request.cancel("sign-in cancelled");

        assertTrue(waiting.isCompletedExceptionally());
        assertNull(request.prompt());
        try {
            waiting.join();
            fail("expected the wait to end in a failure");
        }
        catch (Exception expected) {
        }
    }

    @Test
    public void cancellingTwiceIsHarmless() {
        DD1CodeRequest request = new DD1CodeRequest();
        request.cancel("nothing pending");
        request.cancel("still nothing");
    }

    // A wrong code makes Steam ask again. The first wait must not be left behind.
    @Test
    public void askingAgainAfterAWrongCodeDoesNotLeaveTheFirstWaiting() {
        DD1CodeRequest request = new DD1CodeRequest();
        CompletableFuture<String> first = request.open(DD1SignInCode.email("a@b.com", false));

        CompletableFuture<String> second = request.open(DD1SignInCode.email("a@b.com", true));

        assertTrue("the abandoned wait is released", first.isDone());
        assertFalse(second.isDone());
        assertTrue(request.prompt().previousWasWrong);
    }
}
