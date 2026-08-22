package com.winlator.dd1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DD1HeldKeysTest {
    @Test
    public void pressingTheButtonHoldsTheKeysAndPressingItAgainLetsGo() {
        DD1HeldKeys keys = new DD1HeldKeys();

        assertFalse("nothing is held to begin with", keys.held());
        assertTrue("pressed", keys.toggle());
        assertTrue(keys.held());
        assertFalse("let go", keys.toggle());
        assertFalse(keys.held());
    }

    // The keys are held inside the X server, not in this object. Leaving the game
    // with them down leaves them down for whatever runs next, so the caller has to
    // be told whether there is anything to let go of.
    @Test
    public void lettingGoReportsWhetherAnythingWasBeingHeld() {
        DD1HeldKeys keys = new DD1HeldKeys();

        assertFalse("nothing was held, so nothing to send", keys.releaseAll());

        keys.toggle();
        assertTrue("something was held", keys.releaseAll());
        assertFalse(keys.held());
    }

    @Test
    public void lettingGoTwiceSendsNothingTheSecondTime() {
        DD1HeldKeys keys = new DD1HeldKeys();
        keys.toggle();

        assertTrue(keys.releaseAll());
        assertFalse(keys.releaseAll());
    }
}
