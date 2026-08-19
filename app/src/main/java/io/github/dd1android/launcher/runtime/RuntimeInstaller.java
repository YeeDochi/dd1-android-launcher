package io.github.dd1android.launcher.runtime;

import android.content.Context;
import com.github.luben.zstd.ZstdInputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

public final class RuntimeInstaller {
    private static final String ASSET = "runtime/dd1-runtime.tzst";
    private static final String VERSION = "winlator-11.1-dd1-1";

    private RuntimeInstaller() {}

    public static boolean isReady(File runtimeDir) {
        File root = new File(runtimeDir, "rootfs");
        return new File(root, ".dd1-runtime-version").isFile()
                && new File(root, "usr/local/bin/box64").canExecute()
                && new File(root, "usr/lib/libGL.so.1").isFile();
    }

    public static boolean install(Context context, File runtimeDir) {
        File root = new File(runtimeDir, "rootfs");
        root.mkdirs();
        try (TarArchiveInputStream tar = new TarArchiveInputStream(new ZstdInputStream(
                new BufferedInputStream(context.getAssets().open(ASSET))))) {
            TarArchiveEntry entry;
            byte[] buffer = new byte[64 * 1024];
            while ((entry = tar.getNextTarEntry()) != null) {
                File target = safeTarget(root.toPath(), entry.getName()).toFile();
                if (entry.isDirectory()) {
                    target.mkdirs();
                    continue;
                }
                File parent = target.getParentFile();
                if (parent != null) parent.mkdirs();
                try (BufferedOutputStream output = new BufferedOutputStream(
                        new FileOutputStream(target))) {
                    int count;
                    while ((count = tar.read(buffer)) != -1) output.write(buffer, 0, count);
                }
                if (!target.setExecutable(true, false)) {
                    throw new IOException("Could not mark runtime executable: " + target);
                }
            }
            try (FileOutputStream marker = new FileOutputStream(
                    new File(root, ".dd1-runtime-version"))) {
                marker.write(VERSION.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return isReady(runtimeDir);
        } catch (IOException | RuntimeException error) {
            return false;
        }
    }

    static Path safeTarget(Path root, String entry) throws IOException {
        Path target = root.resolve(entry).normalize();
        if (!target.startsWith(root)) throw new IOException("Unsafe runtime entry: " + entry);
        return target;
    }
}
