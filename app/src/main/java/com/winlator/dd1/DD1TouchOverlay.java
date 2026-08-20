package com.winlator.dd1;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import com.winlator.core.AppUtils;
import com.winlator.math.XForm;
import com.winlator.renderer.ViewTransformation;
import com.winlator.xserver.Pointer;
import com.winlator.xserver.XKeycode;
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

    private final RectF escape = new RectF();
    private final RectF keyboard = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float lastX;
    private float lastY;
    private boolean hovering;
    private boolean escaping;
    private boolean typing;

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
        // The keyboard needs something to type into. With no focused editor on
        // this side the IME opens over the game, sends its characters nowhere,
        // and the game loses the field it had selected.
        setFocusable(true);
        setFocusableInTouchMode(true);
        placeEscape(0, AppUtils.getScreenHeight());
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        placeEscape(transformation().viewOffsetX, height);
    }

    // The container draws at its own resolution inside whatever the screen is, so
    // a fingertip in view pixels is not yet a place on the desktop. Working out
    // that placement twice is how the pointer ended up somewhere else than the
    // finger when the keyboard resized the window: the renderer keeps the
    // transformation it actually drew with, so read that one and there is
    // nothing left to disagree with.
    private ViewTransformation transformation() {
        return xServer.getRenderer().viewTransformation;
    }

    private float[] xform() {
        ViewTransformation transformation = transformation();
        float invAspect = 1.0f / transformation.aspect;
        if (!xServer.getRenderer().isFullscreen()) {
            XForm.makeTranslation(xform, -transformation.viewOffsetX, -transformation.viewOffsetY);
            XForm.scale(xform, invAspect, invAspect);
        }
        else XForm.makeScale(xform, invAspect, invAspect);
        return xform;
    }

    // The game does not fill a phone this wide, and the bars either side of it
    // are the only part of the screen that costs nothing to cover. The menu is
    // otherwise a hunt for a small target in the corner of the game itself.
    private void placeEscape(int letterbox, int outerHeight) {
        float density = getResources().getDisplayMetrics().density;
        float size = 44f * density;
        float margin = 12f * density;
        float left = letterbox >= size + margin * 2 ? (letterbox - size) * 0.5f : margin;
        escape.set(left, outerHeight - size - margin, left + size, outerHeight - margin);
        // Naming an estate needs letters, and reaching them through the runtime's
        // own drawer is a detour. It sits above Esc in the same strip of screen
        // the game does not use.
        keyboard.set(left, escape.top - size - margin * 0.5f, left + size,
            escape.top - margin * 0.5f);
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
                if (escape.contains(lastX, lastY)) {
                    escaping = true;
                    invalidate();
                    return true;
                }
                if (keyboard.contains(lastX, lastY)) {
                    typing = true;
                    invalidate();
                    return true;
                }
                if (!insidePicture(lastX, lastY)) return true;
                hovering = false;
                gesture.down(lastX, lastY, now);
                postDelayed(hold, TouchGesture.HOLD_MILLIS);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (escaping) return true;
                gesture.move(lastX, lastY, now);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (escaping) {
                    escaping = false;
                    invalidate();
                    if (event.getActionMasked() == MotionEvent.ACTION_UP
                            && escape.contains(lastX, lastY)) sendEscape();
                    return true;
                }
                if (typing) {
                    typing = false;
                    invalidate();
                    if (event.getActionMasked() == MotionEvent.ACTION_UP
                            && keyboard.contains(lastX, lastY))
                        toggleKeyboard();
                    return true;
                }
                removeCallbacks(hold);
                gesture.up(lastX, lastY, now);
                hovering = false;
                return true;
            default:
                return false;
        }
    }

    // The IME talks to whatever view claims to be an editor, so the overlay
    // claims it. Nothing is edited here: every character committed is turned
    // straight into a key the game sees.
    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo out) {
        // A composing IME keeps its letters in a buffer this view cannot hold -
        // Samsung's Korean keypad piled up jamo and committed nothing. Asking
        // for the visible-password variant turns composition off, so every key
        // arrives finished.
        // ponytail: Latin only. Hangul needs syllables injected as Unicode
        // keysyms, which is a keysym table this does not have.
        out.inputType = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        out.imeOptions = EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI
            | EditorInfo.IME_FLAG_NO_FULLSCREEN;
        // No text is held, so there is nothing for the IME to compose against.
        return new BaseInputConnection(this, false) {
            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                for (int i = 0; i < text.length(); i++) sendChar(text.charAt(i));
                return true;
            }

            @Override
            public boolean sendKeyEvent(KeyEvent event) {
                // Backspace and Enter arrive as real key events, and the
                // runtime already maps those.
                return xServer.keyboard.onKeyEvent(event);
            }

            @Override
            public boolean deleteSurroundingText(int before, int after) {
                for (int i = 0; i < before; i++) {
                    xServer.injectKeyPress(XKeycode.KEY_BKSP);
                    postDelayed(() -> xServer.injectKeyRelease(XKeycode.KEY_BKSP), CLICK_MILLIS);
                }
                return true;
            }
        };
    }

    // The runtime's own key path already turns a single character into a keysym
    // on a spare keycode, and a multiple-action event is how it is asked to.
    private void sendChar(char c) {
        xServer.keyboard.onKeyEvent(new KeyEvent(0L, String.valueOf(c),
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0));
    }

    // The game has to move out of the keyboard's way, or the field being typed
    // into sits behind it. Shrinking the window did not do it: the game view is
    // not laid out again, so all that happens is the bottom gets clipped off.
    // Turning the screen upright does, and costs nothing to arrange - the game
    // is a landscape picture, so it letterboxes into the top half and the
    // keyboard has the bottom half to itself. Landscape comes back with the
    // keyboard.
    private void toggleKeyboard() {
        android.app.Activity activity = getContext() instanceof android.app.Activity
            ? (android.app.Activity)getContext() : null;
        if (activity == null) return;
        InputMethodManager imm =
            (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;

        if (keyboardShown()) {
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
            activity.setRequestedOrientation(
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            AppUtils.hideSystemUI(activity);
            return;
        }
        activity.getWindow().setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
            activity.getWindow().setDecorFitsSystemWindows(true);
        activity.setRequestedOrientation(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        // The IME opens against the focused view, so the focus has to be here
        // before it is asked to.
        requestFocus();
        imm.showSoftInput(this, 0);
    }

    // The game does not fill the window once the keyboard has taken half of it,
    // and a touch in the black around it lands on the edge of the desktop, where
    // the game scrolls the view - which is the screen shaking by itself. Outside
    // the picture there is nothing to press.
    private boolean keyboardShown() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return false;
        android.view.WindowInsets insets = getRootWindowInsets();
        return insets != null && insets.isVisible(android.view.WindowInsets.Type.ime());
    }

    private boolean insidePicture(float x, float y) {
        if (xServer.getRenderer().isFullscreen()) return true;
        ViewTransformation transformation = transformation();
        return x >= transformation.viewOffsetX
            && x <= transformation.viewOffsetX + transformation.viewWidth
            && y >= transformation.viewOffsetY
            && y <= transformation.viewOffsetY + transformation.viewHeight;
    }

    private void sendEscape() {
        xServer.injectKeyPress(XKeycode.KEY_ESC);
        postDelayed(() -> xServer.injectKeyRelease(XKeycode.KEY_ESC), CLICK_MILLIS);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (escape.isEmpty()) return;
        key(canvas, keyboard, "ABC", typing);
        key(canvas, escape, "ESC", escaping);
    }

    private void key(Canvas canvas, RectF where, String label, boolean pressed) {
        float radius = where.height() * 0.25f;
        paint.setColor(Color.argb(pressed ? 110 : 60, 255, 255, 255));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(where, radius, radius, paint);
        paint.setColor(Color.argb(pressed ? 220 : 140, 0, 0, 0));
        paint.setTextSize(where.height() * 0.32f);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(label, where.centerX(),
            where.centerY() + paint.getTextSize() * 0.35f, paint);
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
        float[] point = XForm.transformPoint(xform(), x, y + TouchGesture.hoverOffset(hovering));
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
