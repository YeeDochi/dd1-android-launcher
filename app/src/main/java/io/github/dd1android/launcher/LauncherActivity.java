package io.github.dd1android.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import io.github.dd1android.launcher.payload.PayloadValidator;
import io.github.dd1android.launcher.storage.AppPaths;
import java.io.IOException;

public final class LauncherActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);
        TextView payloadState = findViewById(R.id.payload_state);
        try {
            AppPaths paths = AppPaths.create(getFilesDir());
            if (PayloadValidator.validate(paths.game().toPath()).valid()) {
                payloadState.setText(R.string.payload_ready);
            }
        } catch (IOException error) {
            payloadState.setText(R.string.storage_unavailable);
        }
    }
}
