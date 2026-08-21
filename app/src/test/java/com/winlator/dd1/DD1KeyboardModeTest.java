package com.winlator.dd1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DD1KeyboardModeTest {
    @Test
    public void hidingTheImeLeavesKeyboardMode() {
        DD1KeyboardMode mode = new DD1KeyboardMode();

        assertTrue(mode.toggle());
        assertTrue(mode.onImeHidden());
        assertTrue(mode.toggle());
        assertFalse(mode.toggle());
        assertFalse(mode.onImeHidden());
    }
}
