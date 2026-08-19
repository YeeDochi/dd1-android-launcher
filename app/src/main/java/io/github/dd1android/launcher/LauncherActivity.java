package io.github.dd1android.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.widget.Button;
import android.widget.TextView;
import io.github.dd1android.launcher.game.GameActivity;
import io.github.dd1android.launcher.payload.PayloadValidator;
import io.github.dd1android.launcher.runtime.DeviceCaps;
import io.github.dd1android.launcher.runtime.LaunchConfigFactory;
import io.github.dd1android.launcher.storage.AppPaths;
import java.io.IOException;

public final class LauncherActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);
        TextView payloadState = findViewById(R.id.payload_state);
        Button launch = findViewById(R.id.launch_button);
        try {
            AppPaths paths = AppPaths.create(getFilesDir());
            boolean payloadReady = PayloadValidator.validate(paths.game().toPath()).valid();
            boolean runtimeReady = "arm64-v8a".equals(Build.SUPPORTED_ABIS[0])
                    && new java.io.File(paths.runtime(), "lib/libzfa.so").isFile();
            if (payloadReady) {
                payloadState.setText(R.string.payload_ready);
            }
            if (runtimeReady) {
                ((TextView) findViewById(R.id.runtime_state)).setText(R.string.runtime_ready);
            }
            if (payloadReady && runtimeReady) {
                launch.setEnabled(true);
                launch.setOnClickListener(view -> GameActivity.start(this,
                        LaunchConfigFactory.create(
                                new DeviceCaps(Build.SUPPORTED_ABIS[0], "",
                                        Build.MANUFACTURER.toLowerCase().contains("waydroid")),
                                paths,
                                null)));
            }
        } catch (IOException error) {
            payloadState.setText(R.string.storage_unavailable);
        }
    }
}
