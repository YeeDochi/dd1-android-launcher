package com.winlator.dd1;

import android.content.Context;

import com.winlator.container.Container;
import com.winlator.core.EnvVars;
import com.winlator.core.TarCompressorUtils;
import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.io.IOException;

// Gladio's client - the libGL the game loads - waits for its renderer on
// every glGetUniformLocation, every buffer update and every flush, hundreds of
// times a frame, and on a Galaxy S26 Ultra that wait was 95% of the game's main
// thread (issue #5). This is the same client (brunodev85/gladio, LGPL-2.1, the
// commit our renderer speaks to) with locations cached, buffer acknowledgements
// read later instead of at once, waits that sleep on a futex instead of
// yielding, and a frame limit. Source and changes: docs/gladio-client.
//
// The rootfs's own libGL is left alone. The patched one is unpacked beside it,
// through the runtime's unpacker so the side-by-side build's paths are
// rewritten as for every other runtime file, and put first on the library path
// whenever the chosen pair draws OpenGL through Gladio.
public final class DD1GladioClient {
    static final String ASSET = "dd1/gladio-client.tzst";
    static final String DIR = "opt/dd1-gladio";
    // Bumped with every rebuild of the asset, so an old copy is replaced.
    static final String MARKER = ".dd1-gladio-client-2";
    static final String KEY = "LD_LIBRARY_PATH";

    private DD1GladioClient() {}

    public static void apply(Context context, Container container) {
        RootFS rootFS = RootFS.find(context);
        File dir = new File(rootFS.getRootDir(), DIR);
        boolean wanted = usesGladio(container.getGraphicsDriver());
        boolean ready = wanted && install(context, rootFS.getRootDir(), dir);
        String updated = withLibraryPath(container.getEnvVars(), ready ? dir.getPath() : null,
            rootFS.getLibDir().getPath());
        updated = withFrameLimit(updated, ready ? frameLimit(context) : null);
        if (!updated.equals(container.getEnvVars())) {
            container.setEnvVars(updated);
            container.saveData();
        }
    }

    static boolean usesGladio(String graphicsDriver) {
        return graphicsDriver != null && graphicsDriver.endsWith(",gladio");
    }

    // The runtime sets LD_LIBRARY_PATH to the rootfs's lib directory itself;
    // the profile's value replaces it, so it has to carry that directory too.
    // Only this launcher's own entry is written or removed.
    static String withLibraryPath(String envVars, String clientDir, String rootfsLib) {
        EnvVars vars = new EnvVars(envVars == null ? "" : envVars);
        String current = vars.has(KEY) ? vars.get(KEY) : "";
        boolean ours = current.contains("/" + DIR);
        if (clientDir != null) vars.put(KEY, clientDir + ":" + rootfsLib);
        else if (ours) vars.remove(KEY);
        return vars.toString();
    }

    // The client paces the game's frames at its swap: without a limit it drew
    // 400+ FPS on screens that do not move, and a turn-based game gives little
    // back for more than 60. Read by the client as DD1_FPS_LIMIT; 0 is no limit.
    static final String FRAME_LIMIT_KEY = "DD1_FPS_LIMIT";
    static final int[] FRAME_LIMITS = {30, 60, 0};
    static final int DEFAULT_FRAME_LIMIT = 60;
    private static final String FRAME_LIMIT_PREF = "frame_limit";

    public static int frameLimit(Context context) {
        int fps = context.getSharedPreferences("dd1", Context.MODE_PRIVATE)
            .getInt(FRAME_LIMIT_PREF, DEFAULT_FRAME_LIMIT);
        for (int known : FRAME_LIMITS) if (known == fps) return fps;
        return DEFAULT_FRAME_LIMIT;
    }

    public static void chooseFrameLimit(Context context, int fps) {
        context.getSharedPreferences("dd1", Context.MODE_PRIVATE).edit()
            .putInt(FRAME_LIMIT_PREF, fps).apply();
    }

    // Null when the patched client is not in use: the stock one ignores it.
    static String withFrameLimit(String envVars, Integer fps) {
        EnvVars vars = new EnvVars(envVars == null ? "" : envVars);
        if (fps != null) vars.put(FRAME_LIMIT_KEY, String.valueOf(fps));
        else vars.remove(FRAME_LIMIT_KEY);
        return vars.toString();
    }

    private static boolean install(Context context, File root, File dir) {
        File marker = new File(dir, MARKER);
        if (marker.isFile() && new File(dir, "libGL.so.1").isFile()) return true;
        if (!TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, ASSET, root)) return false;
        try {
            return marker.createNewFile() || marker.isFile();
        }
        catch (IOException e) {
            return false;
        }
    }
}
