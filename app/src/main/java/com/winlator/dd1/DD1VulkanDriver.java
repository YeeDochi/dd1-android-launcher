package com.winlator.dd1;

import android.content.Context;
import android.net.Uri;

import com.winlator.container.Container;
import com.winlator.container.GraphicsDrivers;
import com.winlator.core.FileUtils;
import com.winlator.core.GeneralComponents;
import com.winlator.core.KeyValueSet;
import com.winlator.core.ZipUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// A Vulkan driver the player brings, for Vortek to load in place of the device's.
// On an Adreno 840 the Turnip this build carries is not the one the community
// tunes for that generation (issue #5), and the runtime already knows how to load
// another: Vortek takes an AdrenoTools package - a zip holding a manifest and a
// libvulkan - and opens it instead of the system driver. Nothing is bundled; the
// file comes from the player, unpacked where the runtime looks for it, and named
// in the profile's Vulkan driver settings.
public final class DD1VulkanDriver {
    public static final String SYSTEM = "System";
    static final String KEY = "adrenotoolsDriver";

    private DD1VulkanDriver() {}

    public static final class NotADriver extends IOException {
        NotADriver(String why) {
            super(why);
        }
    }

    // Unpacks the package and returns the name it is chosen by.
    public static String install(Context context, Uri uri) throws IOException {
        File temp = new File(context.getCacheDir(), "dd1-vulkan-driver.zip");
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(temp)) {
            if (in == null) throw new IOException("Cannot open the file");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
        try {
            byte[] manifest = ZipUtils.read(temp, "*.json");
            if (manifest == null) throw new NotADriver("No manifest in the file");
            String name = nameOf(new String(manifest));
            if (name.isEmpty()) throw new NotADriver("The manifest names no driver");
            File destination = new File(GeneralComponents.getComponentDir(
                GeneralComponents.Type.ADRENOTOOLS_DRIVER, context), name);
            if (destination.exists()) FileUtils.delete(destination);
            destination.mkdirs();
            if (!ZipUtils.extract(temp, destination)) throw new IOException("Cannot unpack the file");
            if (GeneralComponents.getDefinitivePath(GeneralComponents.Type.ADRENOTOOLS_DRIVER,
                    context, name) == null) {
                FileUtils.delete(destination);
                throw new NotADriver("The library the manifest names is not in the file");
            }
            return name;
        }
        finally {
            temp.delete();
        }
    }

    // The runtime's own rule: the manifest's name, else its library's name. A
    // name that would climb out of the drivers directory is refused.
    static String nameOf(String manifestJson) throws NotADriver {
        try {
            JSONObject manifest = new JSONObject(manifestJson);
            return checkedName(manifest.optString("name", manifest.optString("libraryName", "")));
        }
        catch (JSONException e) {
            throw new NotADriver("The manifest is not readable");
        }
    }

    static String checkedName(String name) throws NotADriver {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.equals(".") || trimmed.equals(".."))
            throw new NotADriver("Unusable driver name");
        return trimmed;
    }

    public static String chosen(Container container) {
        String name = vulkanConfig(container).get(KEY);
        return name.isEmpty() ? SYSTEM : name;
    }

    public static void choose(Container container, String name) {
        container.setGraphicsDriverConfig(withDriver(container.getGraphicsDriver(),
            container.getGraphicsDriverConfig(), name));
        container.saveData();
    }

    // The profile keeps one settings string per driver pair, "vulkan|opengl";
    // only the Vulkan half changes, and the OpenGL half is kept as it was.
    static String withDriver(String graphicsDriver, String config, String name) {
        KeyValueSet[] parts = GraphicsDrivers.parseConfigs(graphicsDriver, config);
        parts[0].put(KEY, name == null || name.isEmpty() ? SYSTEM : name);
        return parts[0] + "|" + parts[1];
    }

    private static KeyValueSet vulkanConfig(Container container) {
        return GraphicsDrivers.parseConfigs(container.getGraphicsDriver(),
            container.getGraphicsDriverConfig())[0];
    }

    public static boolean appliesTo(String graphicsDriver) {
        return graphicsDriver != null && graphicsDriver.startsWith(GraphicsDrivers.VORTEK + ",");
    }
}
