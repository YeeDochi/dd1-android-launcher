package io.github.dd1android.launcher.game;

import android.content.Context;
import android.view.SurfaceView;

public final class GameSurface extends SurfaceView {
    public GameSurface(Context context) {
        super(context);
        getHolder().setFixedSize(1280, 720);
    }
}
