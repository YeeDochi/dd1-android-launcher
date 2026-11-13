package com.winlator.dd1;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.winlator.R;
import com.winlator.xserver.Window;
import com.winlator.xserver.WindowManager;
import com.winlator.xserver.XServer;

// What the player sees between pressing Play and the game's first picture. The
// runtime's own "Starting up" box closes as soon as any window maps, a second
// or two in, and what followed was half a minute of black with the touch
// buttons floating over it. This stays until the game has been drawing for a
// moment, says which stage it is at, and fades out.
//
// The game's store header is fetched at runtime into the cache, the way the DLC
// covers are; nothing of the game ships with the launcher.
public final class DD1LaunchCover extends FrameLayout {
    static final String EXTRA = "dd1_launch_cover";
    private static final String TAG = "DD1Launch";
    private static final int GAME_APP_ID = 262060;
    // The game clears its window a few times before it shows anything, so one
    // update is not a picture yet.
    private static final long SETTLE_MILLIS = 1500;
    // A game that never draws still has to hand the screen back.
    private static final long GIVE_UP_MILLIS = 120_000;

    private final XServer xServer;
    private final long started = SystemClock.elapsedRealtime();
    private final TextView stage;
    private volatile boolean done;
    private long gameDrewAt;
    private final WindowManager.OnWindowModificationListener watcher =
        new WindowManager.OnWindowModificationListener() {
            @Override
            public void onMapWindow(Window window) {
                String name = window.getClassName();
                Log.i(TAG, elapsed() + " ms: map \"" + name + "\" " + window.getWidth() + " wide");
                if (isGame(window)) post(() -> stage.setText(R.string.dd1_launch_loading_game));
            }

            // Runs on the X server's thread for every frame of every window, so
            // it does as little as it can and takes itself off once it is done.
            @Override
            public void onUpdateWindowContent(Window window) {
                if (done) {
                    xServer.windowManager.removeOnWindowModificationListener(this);
                    return;
                }
                if (!insideGame(window)) return;
                long now = SystemClock.elapsedRealtime();
                if (gameDrewAt == 0) {
                    gameDrewAt = now;
                    Log.i(TAG, elapsed() + " ms: the game drew its first frame");
                    return;
                }
                if (now - gameDrewAt < SETTLE_MILLIS) return;
                Log.i(TAG, elapsed() + " ms: the game is drawing; cover removed");
                done = true;
                xServer.windowManager.removeOnWindowModificationListener(this);
                post(DD1LaunchCover.this::fadeOut);
            }
        };

    // The launcher asks for the cover when it starts the game; the runtime's
    // file manager and anything else started without it keep the runtime's box.
    public static boolean coversStartup(Activity activity) {
        return activity.getIntent() != null
            && activity.getIntent().getBooleanExtra(EXTRA, false);
    }

    DD1LaunchCover(Context context, XServer xServer) {
        super(context);
        this.xServer = xServer;
        setBackground(new ColorDrawable(Color.rgb(14, 12, 11)));
        // Touches during loading reach nothing useful, and the buttons under the
        // cover are hidden; the cover takes them so nothing is pressed blind.
        setClickable(true);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        addView(column, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            Gravity.CENTER));

        ImageView header = new ImageView(context);
        header.setScaleType(ImageView.ScaleType.CENTER_CROP);
        header.setBackground(new ColorDrawable(Color.rgb(28, 24, 22)));
        header.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(10));
            }
        });
        header.setClipToOutline(true);
        header.setElevation(dp(8));
        column.addView(header, new LinearLayout.LayoutParams(dp(344), dp(160)));
        DlcCovers.load(header, GAME_APP_ID);

        ProgressBar bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        bar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(
            Color.rgb(196, 152, 98)));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(dp(240), dp(4));
        barParams.topMargin = dp(32);
        column.addView(bar, barParams);

        stage = text(context, R.string.dd1_launch_starting, 16, Color.rgb(232, 226, 220));
        LinearLayout.LayoutParams stageParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stageParams.topMargin = dp(18);
        column.addView(stage, stageParams);

        TextView hint = text(context, R.string.dd1_launch_hint, 12, Color.rgb(150, 142, 135));
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(6);
        column.addView(hint, hintParams);

        xServer.windowManager.addOnWindowModificationListener(watcher);
        postDelayed(() -> {
            if (done) return;
            Log.i(TAG, elapsed() + " ms: gave up waiting for the game to draw");
            done = true;
            fadeOut();
        }, GIVE_UP_MILLIS);
        Log.i(TAG, "cover shown");
    }

    private void fadeOut() {
        animate().alpha(0f).setDuration(350).withEndAction(() -> {
            ViewGroup parent = (ViewGroup)getParent();
            if (parent != null) parent.removeView(this);
        }).start();
    }

    private static boolean isGame(Window window) {
        return window.getClassName().toLowerCase(java.util.Locale.ROOT).contains("darkest");
    }

    // The game names its top window, but it draws into a nameless child of it.
    private static boolean insideGame(Window window) {
        for (Window at = window; at != null; at = at.getParent())
            if (isGame(at)) return true;
        return false;
    }

    private long elapsed() {
        return SystemClock.elapsedRealtime() - started;
    }

    private TextView text(Context context, int text, float sp, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
