package com.winlator.dd1;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DD1HomeStateTest {
    @Test
    public void reportsFirstBlockingSetupStep() {
        assertEquals(DD1HomeState.RUNTIME_MISSING, DD1HomeState.from(false, false, false));
        assertEquals(DD1HomeState.GAME_MISSING, DD1HomeState.from(true, false, false));
        assertEquals(DD1HomeState.PROFILE_MISSING, DD1HomeState.from(true, true, false));
        assertEquals(DD1HomeState.READY, DD1HomeState.from(true, true, true));
    }
}
