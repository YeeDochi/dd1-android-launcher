package io.github.dd1android.launcher.game;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import io.github.dd1android.launcher.runtime.LaunchConfig;
import io.github.dd1android.launcher.xserver.XServerRunner;
import java.io.File;

public final class GameActivity extends Activity {
    private static LaunchConfig pendingConfig;

    public static void start(Context context, LaunchConfig config) {
        // ponytail: one in-process game session; parcel the config if process restoration is needed.
        pendingConfig = config;
        context.startActivity(new Intent(context, GameActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(new GameSurface(this));
        LaunchConfig config = pendingConfig;
        int width = config == null ? 1280 : config.width();
        int height = config == null ? 720 : config.height();
        XServerRunner.start(new File(getFilesDir(), "runtime/x11").getAbsolutePath(), width, height);
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) {
            XServerRunner.stop();
            pendingConfig = null;
        }
        super.onDestroy();
    }

    public String getXDisplayName() {
        return ":0";
    }
}
