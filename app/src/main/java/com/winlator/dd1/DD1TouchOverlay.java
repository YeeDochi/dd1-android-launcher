package com.winlator.dd1;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

import com.winlator.core.AppUtils;
import com.winlator.math.XForm;
import com.winlator.renderer.ViewTransformation;
import com.winlator.xserver.Pointer;
import com.winlator.xserver.XServer;

// Puts the pointer where the finger is instead of pushing a relative cursor
// around. The rules live in TouchGesture; this carries them to the X server,
// runs the clock the hold needs, and turns screen pixels into the container's
// own resolution.
public class DD1TouchOverlay extends View implements TouchGesture.Listener {
    private final XServer xServer;
    private final View manyFingers;
    private final float[] xform = XForm.getInstance();
    private final TouchGesture gesture = new TouchGesture(this);
    private final Runnable hold = this::tick;

    private float lastX;
    private float lastY;
    private boolean hovering;

    // Sitting on top of the runtime's touchpad means it never sees a touch again,
    // and the four finger tap that opens the drawer is the only way to reach the
    // settings and the way out. Anything but one finger is handed straight back
    // to it.
    // ponytail: the touchpad never saw the first finger go down, so its own
    // multi-finger state starts mid-gesture. If the drawer tap turns out flaky in
    // game, forward the down event too and swallow its pointer effects instead.
    public DD1TouchOverlay(Context context, XServer xServer, View manyFingers) {
        super(context);
        this.xServer = xServer;
        this.manyFingers = manyFingers;
        // The finger is the cursor now, so the arrow it drags along is only in the
        // way. The pointer still moves; nothing is drawn for it.
        xServer.getRenderer().setCursorVisible(false);
        updateXform(AppUtils.getScreenWidth(), AppUtils.getScreenHeight());
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateXform(width, height);
    }

    // The container draws at its own resolution inside whatever the screen is, so
    // a fingertip in view pixels is not yet a place on the desktop.
    private void updateXform(int outerWidth, int outerHeight) {
        ViewTransformation transformation = new ViewTransformation();
        transformation.update(outerWidth, outerHeight,
            xServer.screenInfo.width, xServer.screenInfo.height);
        float invAspect = 1.0f / transformation.aspect;
        if (!xServer.getRenderer().isFullscreen()) {
            XForm.makeTranslation(xform, -transformation.viewOffsetX, -transformation.viewOffsetY);
            XForm.scale(xform, invAspect, invAspect);
        }
        else XForm.makeScale(xform, invAspect, invAspect);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        if (event.getPointerCount() > 1) {
            removeCallbacks(hold);
            return manyFingers != null && manyFingers.onTouchEvent(event);
        }

        lastX = event.getX();
        lastY = event.getY();
        long now = event.getEventTime();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                hovering = false;
                gesture.down(lastX, lastY, now);
                postDelayed(hold, TouchGesture.HOLD_MILLIS);
                return true;
            case MotionEvent.ACTION_MOVE:
                gesture.move(lastX, lastY, now);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(hold);
                gesture.up(lastX, lastY, now);
                hovering = false;
                return true;
            default:
                return false;
        }
    }

    private void tick() {
        gesture.tick(android.os.SystemClock.uptimeMillis());
    }

    @Override
    public void onMove(float x, float y) {
        // The runtime turns the cursor back on when the game's first window maps,
        // and it has no hook to ask it not to, so the answer is repeated rather
        // than argued about. Setting a boolean per move costs nothing.
        // ponytail: if this ever needs to hold against more of the runtime, listen
        // for window modifications instead of leaning on touches.
        xServer.getRenderer().setCursorVisible(false);
        float[] point = XForm.transformPoint(xform, x, y + TouchGesture.hoverOffset(hovering));
        xServer.injectPointerMove((int)point[0], (int)point[1]);
    }

    @Override
    public void onPress() {
        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT);
    }

    // A press and a release in the same instant is not a click: the game polls
    // the button and never sees it down. The runtime's own touchpad holds the
    // release back by the same amount for the same reason.
    private static final long CLICK_MILLIS = 30L;

    @Override
    public void onRelease() {
        postDelayed(() ->
            xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT), CLICK_MILLIS);
    }

    // The gesture says nothing about where the pointer goes, so lifting it clear
    // of the fingertip is this side's job, and it has to happen at once or the
    // tooltip opens under the hand and stays there.
    @Override
    public void onHoverStart() {
        hovering = true;
        onMove(lastX, lastY);
    }
}
