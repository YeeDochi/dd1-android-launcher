package io.github.dd1android.launcher.game;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.Surface;
import io.github.dd1android.launcher.xserver.XServer;
import java.util.function.Consumer;

public final class GameSurface extends GLSurfaceView {
    public GameSurface(Context context, XServer xServer, Consumer<Surface> onReady) {
        super(context);
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setPreserveEGLContextOnPause(true);
        setRenderer(new GameRenderer(this, xServer,
                () -> onReady.accept(getHolder().getSurface())));
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }
}
