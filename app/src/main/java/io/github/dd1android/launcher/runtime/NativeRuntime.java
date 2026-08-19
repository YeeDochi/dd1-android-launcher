package io.github.dd1android.launcher.runtime;

import android.view.Surface;

public final class NativeRuntime {
    static {
        System.loadLibrary("dd1runtime");
    }

    private NativeRuntime() {}

    public static native boolean attachSurface(Surface surface);
    public static native void detachSurface();
}
