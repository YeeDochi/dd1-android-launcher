package io.github.dd1android.launcher.game;

import android.content.Context;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import io.github.dd1android.launcher.runtime.NativeRuntime;
import java.util.function.Consumer;

public final class GameSurface extends SurfaceView implements SurfaceHolder.Callback {
    private final Consumer<android.view.Surface> onReady;

    public GameSurface(Context context, int width, int height,
                       Consumer<android.view.Surface> onReady) {
        super(context);
        this.onReady = onReady;
        getHolder().setFixedSize(width, height);
        getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (NativeRuntime.attachSurface(holder.getSurface())) onReady.accept(holder.getSurface());
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        NativeRuntime.detachSurface();
    }
}
