package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class DD1GameContentTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void anExecutableOfZeroesIsNotAnInstall() throws IOException {
        File game = game(new byte[] {0, 0, 0, 0});

        DD1Game.Validation validation = DD1Game.validate(game);

        assertFalse(validation.valid);
        assertTrue(validation.missingPath.contains("Darkest.exe"));
    }

    @Test
    public void aRealExecutableStartsWithMZ() throws IOException {
        assertTrue(DD1Game.validate(game(new byte[] {'M', 'Z', (byte)0x90, 0})).valid);
    }

    @Test
    public void aTruncatedExecutableIsRejected() throws IOException {
        File game = game(new byte[] {'M'});

        assertFalse(DD1Game.validate(game).valid);
    }

    @Test
    public void theReportedPathNamesWhatIsWrong() throws IOException {
        File game = game(new byte[] {0, 0, 0, 0});

        assertEquals("_windowsnosteam/win64/Darkest.exe", DD1Game.validate(game).missingPath);
    }

    private File game(byte[] header) throws IOException {
        File game = folder.newFolder(Integer.toString(header.length + header[0]));
        for (String directory : new String[] {"audio", "campaign", "dungeons", "heroes", "shared"})
            new File(game, directory).mkdirs();
        File executable = new File(game, "_windowsnosteam/win64/Darkest.exe");
        executable.getParentFile().mkdirs();
        try (RandomAccessFile file = new RandomAccessFile(executable, "rw")) {
            file.write(header);
        }
        return game;
    }
}
