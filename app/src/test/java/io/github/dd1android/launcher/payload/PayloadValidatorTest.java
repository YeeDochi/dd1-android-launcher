package io.github.dd1android.launcher.payload;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public final class PayloadValidatorTest {
    @Test
    public void acceptsDd1LinuxPayload() throws Exception {
        Path root = Files.createTempDirectory("dd1-game");
        writeElf(root.resolve("_linuxnosteam/darkest.bin.x86_64"), 62);
        write(root.resolve("_linuxnosteam/lib64/libSDL2-2.0.so.0"));
        write(root.resolve("_linuxnosteam/lib64/libfmod.so.14"));
        write(root.resolve("_linuxnosteam/lib64/libfmodstudio.so.14"));
        Files.createDirectories(root.resolve("campaign"));

        assertTrue(PayloadValidator.validate(root).valid());
    }

    @Test
    public void rejectsWrongElfMachineAndMissingFiles() throws Exception {
        Path root = Files.createTempDirectory("dd1-bad-game");
        writeElf(root.resolve("_linuxnosteam/darkest.bin.x86_64"), 183);

        ValidationResult result = PayloadValidator.validate(root);

        assertFalse(result.valid());
        assertTrue(result.errors().contains("darkest.bin.x86_64 is not an x86_64 ELF"));
        assertTrue(result.errors().contains("missing campaign/"));
        assertTrue(result.errors().contains("missing libfmod.so.14"));
    }

    private static void write(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, new byte[] {1});
    }

    private static void writeElf(Path path, int machine) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        header.put(new byte[] {0x7f, 'E', 'L', 'F'});
        header.put((byte) 2);
        header.put((byte) 1);
        header.position(18);
        header.putShort((short) machine);
        Files.createDirectories(path.getParent());
        Files.write(path, header.array());
    }
}
