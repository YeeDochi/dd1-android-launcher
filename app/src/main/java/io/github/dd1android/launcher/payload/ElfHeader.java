package io.github.dd1android.launcher.payload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

public record ElfHeader(boolean elf64LittleEndian, int machine) {
    public static ElfHeader read(Path path) throws IOException {
        byte[] bytes = new byte[20];
        int length = 0;
        try (InputStream input = Files.newInputStream(path)) {
            while (length < bytes.length) {
                int count = input.read(bytes, length, bytes.length - length);
                if (count < 0) {
                    break;
                }
                length += count;
            }
        }
        boolean format = length == bytes.length
                && bytes[0] == 0x7f
                && bytes[1] == 'E'
                && bytes[2] == 'L'
                && bytes[3] == 'F'
                && bytes[4] == 2
                && bytes[5] == 1;
        int machine = format
                ? Short.toUnsignedInt(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getShort(18))
                : -1;
        return new ElfHeader(format, machine);
    }
}
