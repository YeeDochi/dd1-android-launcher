package com.winlator.dd1;

import android.app.Application;

import com.winlator.container.Container;

import org.json.JSONException;
import org.json.JSONObject;

public final class DD1Application extends Application {
    static {
        System.setProperty("kotlinx.coroutines.scheduler.core.pool.size", "2");
        System.setProperty("kotlinx.coroutines.scheduler.max.pool.size", "4");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Before any screen reads the profiles. Reading one that was never
        // finished is a crash out of onResume, so it cannot wait for a screen -
        // and more than one screen builds a ContainerManager.
        DD1ProfileRepair.repair(getFilesDir(), this::profileConfig);
    }

    // Asked for only when there is something to repair: reading the renderer
    // blocks until a GL context comes up.
    private String profileConfig(int id) {
        try {
            JSONObject data = new JSONObject(DD1ProfileConfig.create(
                DD1GraphicsChoice.resolve(this), Container.getFallbackCPUList()));
            data.put("id", id);
            return data.toString();
        }
        catch (JSONException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
