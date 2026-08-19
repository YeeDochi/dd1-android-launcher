package io.github.dd1android.launcher.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.Test;

public final class RuntimeInstallerTest {
    @Test
    public void archiveEntriesStayInsideRuntimeRoot() throws Exception {
        Path root = Path.of("/runtime/rootfs");
        assertEquals(root.resolve("usr/bin/box64"),
                RuntimeInstaller.safeTarget(root, "./usr/bin/box64"));
        assertThrows(IOException.class,
                () -> RuntimeInstaller.safeTarget(root, "../../payload"));
    }
}
