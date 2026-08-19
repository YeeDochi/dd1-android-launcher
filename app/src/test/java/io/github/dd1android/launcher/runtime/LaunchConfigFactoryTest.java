package io.github.dd1android.launcher.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import io.github.dd1android.launcher.storage.AppPaths;
import java.nio.file.Files;
import java.util.Map;
import org.junit.Test;

public final class LaunchConfigFactoryTest {
    @Test
    public void adrenoUsesZinkThenMobileGlues() throws Exception {
        AppPaths paths = AppPaths.create(Files.createTempDirectory("dd1").toFile());

        LaunchConfig config = LaunchConfigFactory.create(
                new DeviceCaps("arm64-v8a", "Adreno (TM) 650", false), paths, null);

        assertEquals(Renderer.ZINK, config.renderer());
        assertEquals(Renderer.MOBILE_GLUES, config.fallbackRenderer());
        assertEquals(1280, config.width());
        assertEquals(720, config.height());
        assertEquals(":0", config.environment().get("DISPLAY"));
        assertEquals("1", config.environment().get("BOX64_DYNACACHE"));
    }

    @Test
    public void waydroidUsesHostMesaWithoutFallbackLoop() throws Exception {
        AppPaths paths = AppPaths.create(Files.createTempDirectory("dd1").toFile());

        LaunchConfig config = LaunchConfigFactory.create(
                new DeviceCaps("x86_64", "AMD Radeon 860M", true), paths, null);

        assertEquals(Renderer.WAYDROID_MESA, config.renderer());
        assertNull(config.fallbackRenderer());
        assertEquals(Map.of(), config.rendererEnvironment());
    }

    @Test
    public void explicitRendererDisablesAutomaticFallback() throws Exception {
        AppPaths paths = AppPaths.create(Files.createTempDirectory("dd1").toFile());

        LaunchConfig config = LaunchConfigFactory.create(
                new DeviceCaps("arm64-v8a", "Mali", false), paths, Renderer.MOBILE_GLUES);

        assertEquals(Renderer.MOBILE_GLUES, config.renderer());
        assertNull(config.fallbackRenderer());
    }
}
