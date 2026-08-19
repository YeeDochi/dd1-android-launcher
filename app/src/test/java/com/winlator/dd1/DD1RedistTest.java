package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

public class DD1RedistTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void installsTheOldestMissingRuntimeFirst() throws IOException {
        File game = folder.newFolder("game");
        File system32 = folder.newFolder("system32");
        File old = file(game, "_CommonRedist/vcredist/2013/vcredist_x64.exe");
        file(game, "_CommonRedist/vcredist/2022/VC_redist.x64.exe");

        assertEquals(old, DD1Game.pendingRedistributable(game, system32));
    }

    @Test
    public void skipsRuntimesAlreadyInThePrefix() throws IOException {
        File game = folder.newFolder("game");
        File system32 = folder.newFolder("system32");
        file(game, "_CommonRedist/vcredist/2013/vcredist_x64.exe");
        File newer = file(game, "_CommonRedist/vcredist/2022/VC_redist.x64.exe");
        file(system32, "msvcp120.dll");

        assertEquals(newer, DD1Game.pendingRedistributable(game, system32));

        file(system32, "msvcp140.dll");
        assertNull(DD1Game.pendingRedistributable(game, system32));
    }

    private static File file(File root, String path) throws IOException {
        File target = new File(root, path);
        target.getParentFile().mkdirs();
        target.createNewFile();
        return target;
    }
}
