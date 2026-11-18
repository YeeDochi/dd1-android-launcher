package com.winlator.dd1;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.preference.PreferenceManager;

import com.winlator.container.Container;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

// A log a player can attach to an issue. Some faults only show on hardware nobody
// here owns - a Galaxy S26 Ultra stalled on every new animation under Turnip and
// under Qualcomm's own driver alike (issue #5) - and without what Wine, box64 and
// the GL layer printed at the moment it stalled there is nothing to go on.
//
// The runtime already knows how to do this: with Wine's debug output or box64's
// log on, it attaches a log window to every process it starts, and with "save
// logs to file" that window writes each line, stamped with the time, to a file.
// All of it is read from the runtime's own preferences, so turning it on here is
// writing those preferences and nothing in the runtime changes. The file goes to
// DD1/logs, where a file manager reaches it, beside a short description of the
// device and the profile, which is the first thing anybody asks for.
public final class DD1Diagnostics {
    static final String KEY = "diagnostic_logs";
    static final String LOG = "wine-log.txt";
    static final String PREVIOUS = "wine-log-previous.txt";
    static final String DEVICE = "device.txt";
    static final String LOGCAT = "logcat.txt";
    static final String LOGCAT_PREVIOUS = "logcat-previous.txt";
    private static final String LOGCAT_PID = "diagnostic_logcat_pid";

    private DD1Diagnostics() {}

    public static boolean enabled(Context context) {
        return context.getApplicationContext().getSharedPreferences("dd1", Context.MODE_PRIVATE)
            .getBoolean(KEY, false);
    }

    public static void setEnabled(Context context, boolean on) {
        context.getApplicationContext().getSharedPreferences("dd1", Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, on).apply();
        apply(PreferenceManager.getDefaultSharedPreferences(context),
            runtimePreferences(on, new File(dir(), LOG)));
        if (!on) stopLogcat(context);
    }

    public static File dir() {
        return new File(DD1GameLocation.storageRoot(), "logs");
    }

    // What the runtime reads. Box64 at level 1 only: it names opcodes the dynarec
    // could not translate and prints its own start-up, where level 2 traces every
    // call and would itself be the stall. Wine keeps the runtime's default
    // channels, which add nothing to its errors and fixmes on their own.
    static Map<String, Object> runtimePreferences(boolean on, File log) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("enable_wine_debug", on);
        values.put("box64_logs", on ? 1 : 0);
        values.put("save_logs_to_file", on);
        values.put("log_file", on ? log.getPath() : null);
        return values;
    }

    private static void apply(SharedPreferences preferences, Map<String, Object> values) {
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, Object> value : values.entrySet()) {
            Object v = value.getValue();
            if (v == null) editor.remove(value.getKey());
            else if (v instanceof Boolean) editor.putBoolean(value.getKey(), (Boolean)v);
            else if (v instanceof Integer) editor.putInt(value.getKey(), (Integer)v);
            else editor.putString(value.getKey(), v.toString());
        }
        editor.commit();
    }

    // At Play. The runtime empties the log file when it opens, so the last run's
    // log is kept one step back: a player who reopens the game to find the file
    // has not lost the run that went wrong.
    public static void beforePlay(Context context, Container container) {
        if (!enabled(context)) return;
        File dir = dir();
        if (!dir.isDirectory() && !dir.mkdirs()) return;
        keepPrevious(dir, LOG, PREVIOUS);
        startLogcat(context, dir);
        try (Writer out = new OutputStreamWriter(
                new FileOutputStream(new File(dir, DEVICE)), StandardCharsets.UTF_8)) {
            out.write(describe(facts(context, container)));
        }
        catch (IOException ignored) {}
    }

    static void keepPrevious(File dir, String name, String previousName) {
        File log = new File(dir, name);
        if (!log.isFile() || log.length() == 0) return;
        File previous = new File(dir, previousName);
        previous.delete();
        log.renameTo(previous);
    }

    // What Wine prints is only the top of the stack. Vortek, the Vulkan driver it
    // loads, the X server's renderer and Android itself write to logcat, in this
    // app's own process - and on the S26 Ultra the Wine log stayed silent through
    // eight minutes of stalls, which says the answer is down there. An app reads
    // its own log lines without any permission, so a logcat writing to a file is
    // started at Play and runs until the next Play or until the switch goes off.
    // Its pid is remembered so a launcher restarted in between can still stop it,
    // or two of them would write into the same file.
    private static void startLogcat(Context context, File dir) {
        stopLogcat(context);
        keepPrevious(dir, LOGCAT, LOGCAT_PREVIOUS);
        try {
            Process process = new ProcessBuilder("logcat", "-v", "threadtime",
                "-f", new File(dir, LOGCAT).getPath(), "-r", "16384", "-n", "1")
                .redirectErrorStream(true).start();
            int pid = pidOf(process);
            if (pid > 0) preferences(context).edit().putInt(LOGCAT_PID, pid).apply();
        }
        catch (IOException ignored) {}
    }

    private static void stopLogcat(Context context) {
        int pid = preferences(context).getInt(LOGCAT_PID, -1);
        if (pid <= 0) return;
        // Only if that pid is still a logcat: after a reboot the number may
        // belong to something else of this app's.
        if (isLogcat(pid)) android.os.Process.killProcess(pid);
        preferences(context).edit().remove(LOGCAT_PID).apply();
    }

    private static boolean isLogcat(int pid) {
        try {
            byte[] cmdline = java.nio.file.Files.readAllBytes(
                new File("/proc/" + pid + "/cmdline").toPath());
            return new String(cmdline, StandardCharsets.UTF_8).startsWith("logcat");
        }
        catch (Exception e) {
            return false;
        }
    }

    private static int pidOf(Process process) {
        try {
            java.lang.reflect.Field field = process.getClass().getDeclaredField("pid");
            field.setAccessible(true);
            return field.getInt(process);
        }
        catch (Exception e) {
            return -1;
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences("dd1", Context.MODE_PRIVATE);
    }

    private static Map<String, String> facts(Context context, Container container) {
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("launcher", versionName(context));
        facts.put("device", Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")");
        facts.put("android", Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT);
        facts.put("soc", Build.VERSION.SDK_INT >= 31 ? Build.SOC_MODEL : "?");
        facts.put("gpu", String.valueOf(com.winlator.core.GPUHelper.glGetRenderer(context)));
        facts.put("cores", String.valueOf(DD1CpuBudget.cores()));
        facts.put("graphics choice", DD1GraphicsChoice.stored(context).isEmpty()
            ? "automatic" : DD1GraphicsChoice.stored(context));
        facts.put("graphics driver", container.getGraphicsDriver());
        facts.put("screen", container.getScreenSize());
        facts.put("cpu list", String.valueOf(container.getCPUList()));
        facts.put("box64 preset", container.getBox64Preset());
        facts.put("env", container.getEnvVars());
        facts.put("vulkan driver file", DD1VulkanDriver.chosen(container));
        facts.put("fps display", container.getHUDMode() != 0 ? "on" : "off");
        facts.put("game location", DD1GameLocation.chosen(context));
        return facts;
    }

    static String describe(Map<String, String> facts) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> fact : facts.entrySet())
            text.append(fact.getKey()).append(": ").append(fact.getValue()).append('\n');
        return text.toString();
    }

    private static String versionName(Context context) {
        try {
            return context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0).versionName;
        }
        catch (Exception e) {
            return "?";
        }
    }
}
