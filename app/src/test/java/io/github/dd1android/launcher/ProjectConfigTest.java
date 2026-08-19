package io.github.dd1android.launcher;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ProjectConfigTest {
    @Test
    public void appIdentityIsStable() {
        assertEquals("io.github.dd1android.launcher", BuildConfig.APPLICATION_ID);
    }
}
