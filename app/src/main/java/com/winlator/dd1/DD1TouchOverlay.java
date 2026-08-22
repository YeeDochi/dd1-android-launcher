package com.winlator.dd1;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import com.winlator.R;

import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Rect;
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
    private final DD1KeyboardMode keyboardMode = new DD1KeyboardMode();
    private final Runnable hold = this::tick;

    private final RectF escape = new RectF();
    private final RectF keyboard = new RectF();
    // The torch is toggled with Shift+Ctrl and a left click, which a touch screen
    // cannot do at once. This holds the two keys until it is pressed again, on the
    // opposite bar so the hand that clicks is not the hand that holds.
    private final RectF sticky = new RectF();
    private final DD1HeldKeys heldKeys = new DD1HeldKeys();
    private final Path border = new Path();
    private final Path light = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float lastX;
    private float lastY;
    private boolean escaping;
    private boolean typing;
    private boolean sticking;

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
        AppUtils.observeSoftKeyboardVisibility(this, this::keyboardVisibilityChanged);
        placeEscape(0, AppUtils.getScreenHeight());
        applyRefreshRate(context);
    }

    // The panel draws at its own rate whatever the game is doing, and on a 120 Hz
    // phone half of that is spent on frames nobody asked for. The runtime's
    // activity owns the window, so the rate is asked for from here rather than by
    // editing it: this view is already attached to that window.
    private static final String HALF_REFRESH_RATE = "half_refresh_rate";

    public static boolean prefersHalfRefreshRate(Context context) {
        return context.getSharedPreferences("dd1", Context.MODE_PRIVATE)
            .getBoolean(HALF_REFRESH_RATE, false);
    }

    public static void chooseHalfRefreshRate(Context context, boolean half) {
        context.getSharedPreferences("dd1", Context.MODE_PRIVATE).edit()
            .putBoolean(HALF_REFRESH_RATE, half).apply();
    }

    private void applyRefreshRate(Context context) {
        if (!prefersHalfRefreshRate(context)) return;
        android.app.Activity activity = activityOf(context);
        if (activity == null) return;
        android.view.Window window = activity.getWindow();
        float rate = window.getWindowManager().getDefaultDisplay().getRefreshRate();
        if (rate <= 61f) return;
        android.view.WindowManager.LayoutParams params = window.getAttributes();
        params.preferredRefreshRate = rate / 2f;
        window.setAttributes(params);
    }

    private static android.app.Activity activityOf(Context context) {
        while (context instanceof android.content.ContextWrapper) {
            if (context instanceof android.app.Activity) return (android.app.Activity)context;
            context = ((android.content.ContextWrapper)context).getBaseContext();
        }
        return null;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        placeEscape(transformation().viewOffsetX,
            keyboardMode.active() ? visibleBottom() : height);
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
        // The other bar, so the hand holding the keys is not the hand clicking.
        // Wider than the others because it says what it does rather than a word.
        float wide = size * 1.6f;
        float right = getWidth() - (letterbox >= wide + margin * 2
            ? (letterbox - wide) * 0.5f + wide : wide + margin);
        sticky.set(right, escape.top, right + wide, escape.bottom);
    }

    // Winlator normally pans its desktop towards the pointer when an IME opens.
    // That helps a desktop text field but makes DD1's whole screen move under a
    // dragging finger. This callback runs before Winlator's observer, so the
    // reset is posted to run after it.
    private void keyboardVisibilityChanged(boolean visible) {
        if (visible && keyboardMode.active()) {
            post(() -> {
                xServer.getRenderer().setScreenOffsetYRelativeToCursor(false);
                placeEscape(transformation().viewOffsetX, visibleBottom());
                invalidate();
            });
        }
        else if (!visible && keyboardMode.onImeHidden()) leaveKeyboardMode();
    }

    // Works whether Android resized the view or left it behind the IME: the
    // buttons belong at the bottom of the part of the window that is visible.
    private int visibleBottom() {
        Rect visible = new Rect();
        int[] location = new int[2];
        getWindowVisibleDisplayFrame(visible);
        getLocationOnScreen(location);
        return Math.max(0, Math.min(getHeight(), visible.bottom - location[1]));
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
                if (sticky.contains(lastX, lastY)) {
                    sticking = true;
                    invalidate();
                    return true;
                }
                if (!insidePicture(lastX, lastY)) return true;
                gesture.down(lastX, lastY, now);
                postDelayed(hold, TouchGesture.HOLD_MILLIS);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (escaping || sticking) return true;
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
                if (sticking) {
                    sticking = false;
                    if (event.getActionMasked() == MotionEvent.ACTION_UP
                            && sticky.contains(lastX, lastY)) {
                        if (heldKeys.toggle()) holdKeys(); else letKeysGo();
                    }
                    invalidate();
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
    // into sits behind it. Turning the screen upright does it, and costs nothing to
    // arrange - the game is a landscape picture, so it letterboxes into the top
    // half and the keyboard has the bottom half to itself. Landscape comes back
    // with the keyboard.
    //
    // Turning it is not the only thing that fixes: it is the only thing that fixes
    // all three. Keeping landscape and making room above the keyboard was measured
    // on both devices and abandoned, for three separate reasons:
    //
    //   - Telling the renderer the surface is shorter puts the picture *under* the
    //     keyboard, not above it. It draws with glViewport(0, 0, w, h) and OpenGL
    //     counts from the bottom left; the call is upstream, so no y offset can be
    //     passed. Measured: the screen went blank.
    //   - Giving the game's own view a shorter height does work - a Tab S8 letter-
    //     boxed 1497x842 above the keyboard and was comfortable - but on a Galaxy
    //     S25 the window height would not settle, reporting 990, then 486, then
    //     990 with 396 visible, so what the picture should fit changed under it.
    //   - With the keyboard over the game rather than beside it, the runtime pans
    //     its desktop towards the pointer, and the whole picture slides up and down
    //     under a dragging finger. Portrait avoids that by never having the two
    //     overlap.
    //
    // The typing itself is what needs the space: the field must be clicked before
    // it can be typed into, so the game has to be visible *and* touchable while the
    // keyboard is up. That is what upright gives and what none of the three gave.
    private void toggleKeyboard() {
        android.app.Activity activity = getContext() instanceof android.app.Activity
            ? (android.app.Activity)getContext() : null;
        if (activity == null) return;
        InputMethodManager imm =
            (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;

        if (!keyboardMode.toggle()) {
            leaveKeyboardMode();
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

    private void leaveKeyboardMode() {
        android.app.Activity activity = getContext() instanceof android.app.Activity
            ? (android.app.Activity)getContext() : null;
        if (activity == null) return;
        InputMethodManager imm =
            (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(getWindowToken(), 0);
        activity.setRequestedOrientation(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        AppUtils.hideSystemUI(activity);
    }

    // The game does not fill the window once the keyboard has taken half of it,
    // and a touch in the black around it lands on the edge of the desktop. There
    // is nothing to press outside the picture.
    private boolean insidePicture(float x, float y) {
        if (xServer.getRenderer().isFullscreen()) return true;
        ViewTransformation transformation = transformation();
        return x >= transformation.viewOffsetX
            && x <= transformation.viewOffsetX + transformation.viewWidth
            && y >= transformation.viewOffsetY
            && y <= transformation.viewOffsetY + transformation.viewHeight;
    }

    private void holdKeys() {
        xServer.injectKeyPress(XKeycode.KEY_SHIFT_L);
        xServer.injectKeyPress(XKeycode.KEY_CTRL_L);
    }

    private void letKeysGo() {
        xServer.injectKeyRelease(XKeycode.KEY_CTRL_L);
        xServer.injectKeyRelease(XKeycode.KEY_SHIFT_L);
    }

    // Leaving the game while the keys are held would leave them held for whatever
    // runs next, and nothing else would ever let them go.
    @Override
    protected void onDetachedFromWindow() {
        if (heldKeys.releaseAll()) letKeysGo();
        super.onDetachedFromWindow();
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
        key(canvas, sticky, getContext().getString(R.string.dd1_sticky_keys),
            sticking, heldKeys.held());
    }

    private void key(Canvas canvas, RectF where, String label, boolean pressed) {
        key(canvas, where, label, pressed, false);
    }

    // Pressed is a moment and latched is a state, so they cannot look the same.
    // The game is a dark screen with one warm light in it, and a bright blue slab
    // in the bar beside it would be the loudest thing on screen. So the button
    // stays dark and grey like the rest of it, and being on is a red light going
    // round its edge - which is a torch, which is what it is for. Colour is missed
    // in the corner of an eye; movement is not.
    private void key(Canvas canvas, RectF where, String label, boolean pressed,
            boolean latched) {
        float radius = where.height() * 0.25f;
        paint.setStyle(Paint.Style.FILL);
        // Not a neutral grey: the game is lit by one red light and everything in
        // it is warmed by that, so the button carries the same cast.
        paint.setColor(pressed ? Color.argb(210, 54, 32, 27)
            : Color.argb(180, 32, 19, 16));
        canvas.drawRoundRect(where, radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, where.height() * 0.045f));
        paint.setColor(latched ? Color.argb(90, 150, 116, 106)
            : Color.argb(160, 158, 118, 106));
        canvas.drawRoundRect(where, radius, radius, paint);
        if (latched) travellingLight(canvas, where, radius);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(latched ? Color.argb(255, 240, 196, 164)
            : Color.argb(220, 198, 168, 156));
        if (latched) {
            paint.setTextSize(where.height() * 0.40f);
            canvas.drawText(getContext().getString(R.string.dd1_sticky_keys_on),
                where.centerX(), where.centerY() + paint.getTextSize() * 0.35f, paint);
            return;
        }
        // Two keys do not fit across a button this size on one line.
        String[] lines = label.split("\n");
        paint.setTextSize(where.height() * (lines.length > 1 ? 0.26f : 0.32f));
        float step = paint.getTextSize() * 1.08f;
        float first = where.centerY() + paint.getTextSize() * 0.35f
            - step * (lines.length - 1) * 0.5f;
        for (int i = 0; i < lines.length; i++)
            canvas.drawText(lines[i], where.centerX(), first + step * i, paint);
    }

    // A bright arc walking the button's outline, once round every LAP_MILLIS. Only
    // this button's own rectangle is invalidated for it, so the game keeps the rest
    // of the frame.
    private static final long LAP_MILLIS = 1600L;

    private void travellingLight(Canvas canvas, RectF where, float radius) {
        border.reset();
        border.addRoundRect(where, radius, radius, Path.Direction.CW);
        measure.setPath(border, false);
        float length = measure.getLength();
        if (length <= 0) return;
        float head = (android.os.SystemClock.uptimeMillis() % LAP_MILLIS)
            / (float)LAP_MILLIS * length;
        float tail = length * 0.28f;
        light.reset();
        // Wrapped round the end rather than stopping at it.
        if (head + tail <= length) measure.getSegment(head, head + tail, light, true);
        else {
            measure.getSegment(head, length, light, true);
            measure.getSegment(0, head + tail - length, light, true);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(3f, where.height() * 0.10f));
        paint.setColor(Color.argb(255, 219, 82, 48));
        canvas.drawPath(light, paint);
        postInvalidateOnAnimation((int)where.left - 4, (int)where.top - 4,
            (int)where.right + 4, (int)where.bottom + 4);
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
        float[] point = XForm.transformPoint(xform(), x, y);
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

    // Using an item on a hero is a right click, and holding still is what asks
    // for one. The cursor stays exactly where the finger rested: there is no
    // tooltip to keep clear of, because a tap already leaves the cursor on what
    // it touched and the game shows it there.
    @Override
    public void onSecondaryClick() {
        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_RIGHT);
        postDelayed(() ->
            xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT), CLICK_MILLIS);
    }
}
