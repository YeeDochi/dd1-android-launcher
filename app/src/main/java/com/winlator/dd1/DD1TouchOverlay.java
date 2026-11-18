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
    // Walking a corridor by dragging is a stream of pointer moves, and the game
    // redraws its hover state for every one of them: a dragging finger dropped
    // frames where a held key does not. The game walks the party on A and D, so
    // these hold those for as long as the finger stays on them.
    private final RectF back = new RectF();
    private final RectF forward = new RectF();
    private XKeycode walking;
    // The magnifier (DD1Zoom), and the map under the game that says which part
    // of the picture is on screen and moves it when dragged.
    private final RectF magnify = new RectF();
    private final RectF minimap = new RectF();
    private final DD1Zoom zoom = new DD1Zoom();
    private final RectF seen = new RectF();
    private final Runnable refreshThumbnail = this::copyThumbnail;
    private android.graphics.Bitmap thumbnail;
    private android.graphics.Bitmap thumbnailSpare;
    private boolean magnifying;
    private boolean panning;
    private float focusX = Float.NaN;
    private float focusY = Float.NaN;
    private final Path border = new Path();
    private final Path light = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float lastX;
    private float lastY;
    private boolean escaping;
    private boolean typing;
    private boolean sticking;
    private int measurements;
    private int heightBeforeKeyboard;

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
        // A controller button that the game does not use arrives as Back, and the
        // runtime answers Back by opening its drawer over the game.
        if (context instanceof android.app.Activity)
            DD1RuntimeMenu.apply((android.app.Activity)context);
        buttonsHidden = hidesButtons(context);
        placeEscape(0, AppUtils.getScreenHeight());
        applyRefreshRate(context);
    }

    // The panel draws at its own rate whatever the game is doing, and on a 120 Hz
    // phone half of that is spent on frames nobody asked for. The runtime's
    // activity owns the window, so the rate is asked for from here rather than by
    // editing it: this view is already attached to that window.
    private static final String HALF_REFRESH_RATE = "half_refresh_rate";

    // Someone playing with a controller has Esc and the rest on the pad, and the
    // keys drawn in the bars are only in the way. Hidden, they are neither drawn
    // nor touchable; touch on the picture still clicks.
    private static final String HIDE_BUTTONS = "hide_screen_buttons";
    private boolean buttonsHidden;

    public static boolean hidesButtons(Context context) {
        return context.getSharedPreferences("dd1", Context.MODE_PRIVATE)
            .getBoolean(HIDE_BUTTONS, false);
    }

    public static void chooseHiddenButtons(Context context, boolean hidden) {
        context.getSharedPreferences("dd1", Context.MODE_PRIVATE).edit()
            .putBoolean(HIDE_BUTTONS, hidden).apply();
    }

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
        refreshButtons();
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
        if (buttonsHidden) {
            // An empty rectangle is drawn as nothing and contains no touch.
            escape.setEmpty();
            keyboard.setEmpty();
            magnify.setEmpty();
            sticky.setEmpty();
            back.setEmpty();
            forward.setEmpty();
            return;
        }
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
        magnify.set(left, keyboard.top - size - margin * 0.5f, left + size,
            keyboard.top - margin * 0.5f);
        // The other bar, so the hand holding the keys is not the hand clicking.
        // Wider than the others because it says what it does rather than a word.
        float wide = size * 1.6f;
        float right = getWidth() - (letterbox >= wide + margin * 2
            ? (letterbox - wide) * 0.5f + wide : wide + margin);
        sticky.set(right, escape.top, right + wide, escape.bottom);
        // Above the held keys, as a pair, and taller than they are wide so the
        // pair still fits a narrow bar.
        float gap = margin * 0.5f;
        float half = (wide - gap) * 0.5f;
        float top = sticky.top - gap - size * 1.25f;
        back.set(right, top, right + half, sticky.top - gap);
        forward.set(right + half + gap, top, right + wide, sticky.top - gap);
    }

    // Every path that moves the buttons goes through here, and it reads the state
    // rather than being handed it. A placement that was posted while the keyboard
    // was up used to land after it had gone, leaving the buttons at the height the
    // keyboard had left and against the edges the shrunken picture had - and
    // nothing placed them again.
    private void refreshButtons() {
        placeEscape(transformation().viewOffsetX,
            keyboardMode.active() ? visibleBottom() : getHeight());
        placeZoom();
        invalidate();
    }

    // The part of this view the game is drawn in, unscaled.
    private RectF picture() {
        if (xServer.getRenderer().isFullscreen()) return new RectF(0, 0, getWidth(), getHeight());
        ViewTransformation t = transformation();
        return new RectF(t.viewOffsetX, t.viewOffsetY, t.viewOffsetX + t.viewWidth,
            t.viewOffsetY + t.viewHeight);
    }

    // The map goes at the top of the right-hand bar, which nothing else uses, so
    // it covers none of the game. At the bottom of the picture it sat on the hero
    // panel. A screen with no bar wide enough - a 16:10 tablet - gets it small in
    // the picture's top right corner instead.
    private void placeZoom() {
        RectF picture = picture();
        if (picture.width() <= 0 || picture.height() <= 0) return;
        zoom.setPicture(picture.left, picture.top, picture.width(), picture.height());
        float density = getResources().getDisplayMetrics().density;
        float margin = 6f * density;
        float aspect = picture.height() / picture.width();
        float bar = getWidth() - picture.right;
        float width;
        float left;
        // 40 dp is a Galaxy S22's bar less its margins: small, but a map only has
        // to show where the frame is.
        if (bar - margin * 2 >= 40f * density) {
            width = bar - margin * 2;
            left = picture.right + margin;
        }
        else {
            width = Math.min(120f * density, picture.width() * 0.18f);
            left = picture.right - margin - width;
        }
        float top = picture.top + margin;
        minimap.set(left, top, left + width, top + width * aspect);
        applyZoom();
    }

    private void toggleZoom() {
        // The overlay is laid out before the renderer has its surface, so the
        // picture it knew then may have been empty. Ask again now.
        placeZoom();
        RectF picture = picture();
        if (Float.isNaN(focusX)) {
            focusX = picture.centerX();
            focusY = picture.centerY();
        }
        zoom.toggle(focusX, focusY);
        applyZoom();
        removeCallbacks(refreshThumbnail);
        if (zoom.on()) copyThumbnail();
        else thumbnail = null;
    }

    private void applyZoom() {
        View view = xServer.getRenderer().xServerView;
        view.setPivotX(0);
        view.setPivotY(0);
        view.setScaleX(zoom.scale());
        view.setScaleY(zoom.scale());
        view.setTranslationX(zoom.translationX());
        view.setTranslationY(zoom.translationY());
        invalidate();
    }

    private void panFromMinimap(float x, float y) {
        zoom.panToFraction((x - minimap.left) / minimap.width(),
            (y - minimap.top) / minimap.height());
        applyZoom();
    }

    // The map shows the game itself, copied small out of its surface. The copy
    // is of the surface's buffer, which the view's scale does not touch, so it is
    // always the whole picture. Twice a second is enough to recognise a room.
    private static final long THUMBNAIL_MILLIS = 500L;

    private void copyThumbnail() {
        if (!zoom.on() || minimap.isEmpty()) return;
        View view = xServer.getRenderer().xServerView;
        if (!(view instanceof android.view.SurfaceView) || getHandler() == null) return;
        int width = Math.max(1, Math.round(minimap.width()));
        int height = Math.max(1, Math.round(minimap.height()));
        android.graphics.Bitmap target = thumbnailSpare;
        if (target == null || target.getWidth() != width || target.getHeight() != height)
            target = android.graphics.Bitmap.createBitmap(width, height,
                android.graphics.Bitmap.Config.ARGB_8888);
        RectF picture = picture();
        Rect source = new Rect(Math.round(picture.left), Math.round(picture.top),
            Math.round(picture.right), Math.round(picture.bottom));
        final android.graphics.Bitmap into = target;
        try {
            android.view.PixelCopy.request((android.view.SurfaceView)view, source, into, result -> {
                if (result == android.view.PixelCopy.SUCCESS && zoom.on()) {
                    thumbnailSpare = thumbnail;
                    thumbnail = into;
                    invalidate();
                }
                else thumbnailSpare = into;
                if (zoom.on()) postDelayed(refreshThumbnail, THUMBNAIL_MILLIS);
            }, getHandler());
        }
        catch (IllegalArgumentException e) {
            // The surface is not there yet; try again later.
            postDelayed(refreshThumbnail, THUMBNAIL_MILLIS);
        }
    }

    // Winlator normally pans its desktop towards the pointer when an IME opens.
    // That helps a desktop text field but makes DD1's whole screen move under a
    // dragging finger. This callback runs before Winlator's observer, so the
    // reset is posted to run after it.
    private void keyboardVisibilityChanged(boolean visible) {
        if (visible && keyboardMode.active()) {
            post(() -> {
                xServer.getRenderer().setScreenOffsetYRelativeToCursor(false);
                refreshButtons();
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
            if (walking != null) stopWalking();
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
                if (back.contains(lastX, lastY)) {
                    startWalking(XKeycode.KEY_A);
                    return true;
                }
                if (forward.contains(lastX, lastY)) {
                    startWalking(XKeycode.KEY_D);
                    return true;
                }
                if (magnify.contains(lastX, lastY)) {
                    magnifying = true;
                    invalidate();
                    return true;
                }
                if (zoom.on() && minimap.contains(lastX, lastY)) {
                    panning = true;
                    panFromMinimap(lastX, lastY);
                    return true;
                }
                float[] local = zoom.toLocal(lastX, lastY);
                if (!insidePicture(local[0], local[1])) return true;
                gesture.down(lastX, lastY, now);
                postDelayed(hold, TouchGesture.HOLD_MILLIS);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (panning) {
                    panFromMinimap(lastX, lastY);
                    return true;
                }
                if (escaping || sticking || magnifying || walking != null) return true;
                gesture.move(lastX, lastY, now);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (walking != null) {
                    stopWalking();
                    return true;
                }
                if (panning) {
                    panning = false;
                    return true;
                }
                if (magnifying) {
                    magnifying = false;
                    if (event.getActionMasked() == MotionEvent.ACTION_UP
                            && magnify.contains(lastX, lastY)) toggleZoom();
                    invalidate();
                    return true;
                }
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
        // The picture moves and shrinks for the keyboard; a magnified one would
        // put the field being typed into somewhere off screen.
        if (zoom.on()) toggleZoom();
        activity.getWindow().setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
            activity.getWindow().setDecorFitsSystemWindows(true);
        heightBeforeKeyboard = getHeight();
        measurements = 0;
        if (shrinksForKeyboard()) squeezePictureAboveKeyboard();
        else activity.setRequestedOrientation(
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
        if (shrinksForKeyboard()) restorePicture();
        else activity.setRequestedOrientation(
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

    private void startWalking(XKeycode key) {
        walking = key;
        xServer.injectKeyPress(key);
        invalidate();
    }

    private void stopWalking() {
        XKeycode key = walking;
        walking = null;
        if (key != null) xServer.injectKeyRelease(key);
        invalidate();
    }

    // The loading cover goes over everything, this overlay's buttons included,
    // and only for a launch the launcher asked it for.
    private boolean coverPlaced;

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (coverPlaced || !(getContext() instanceof android.app.Activity)) return;
        coverPlaced = true;
        if (!DD1LaunchCover.coversStartup((android.app.Activity)getContext())) return;
        android.view.ViewParent parent = getParent();
        if (parent instanceof android.view.ViewGroup)
            ((android.view.ViewGroup)parent).addView(new DD1LaunchCover(getContext(), xServer));
    }

    // Leaving the game while the keys are held would leave them held for whatever
    // runs next, and nothing else would ever let them go.
    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(refreshThumbnail);
        if (walking != null) stopWalking();
        if (heldKeys.releaseAll()) letKeysGo();
        super.onDetachedFromWindow();
    }

    private boolean shrinksForKeyboard() {
        return getResources().getBoolean(R.bool.dd1_shrink_for_keyboard);
    }

    // Lying to the renderer about the surface size does not work - see the note on
    // entering keyboard mode. The surface has to actually be shorter, and giving the
    // game's own view a height does that: the GLSurfaceView gets a real
    // onSurfaceChanged, the viewport fills it, and the picture letterboxes into what
    // is left. The overlay's touch mapping reads the same transformation, so a
    // finger still lands where it is aimed.
    private void fitPictureTo(int height) {
        android.view.View picture = xServer.getRenderer().xServerView;
        android.view.ViewGroup.LayoutParams params = picture.getLayoutParams();
        if (params == null) return;
        params.height = height;
        picture.setLayoutParams(params);
        post(this::refreshButtons);
    }

    // The window is told about the keyboard a frame or several after it is asked
    // for, and how many is the device's business. Until then the whole height still
    // looks free, which is not the same as there being no room - read as no room
    // once, and a device turned itself upright on a measurement that meant nothing.
    private void squeezePictureAboveKeyboard() {
        // A measurement queued while the keyboard was up can wake while it is on
        // its way down, when the window is still short. Shrinking then happens
        // after the picture has been given its height back, and it stays small
        // with a black band under it.
        if (!keyboardMode.active()) return;
        int visible = visibleBottom();
        if (visible <= 0 || visible >= heightBeforeKeyboard) {
            if (keyboardMode.active() && ++measurements <= 12)
                postDelayed(this::squeezePictureAboveKeyboard, 120);
            return;
        }
        fitPictureTo(visible);
    }

    private void restorePicture() {
        fitPictureTo(android.view.ViewGroup.LayoutParams.MATCH_PARENT);
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
        key(canvas, back, "◀", walking == XKeycode.KEY_A);
        key(canvas, forward, "▶", walking == XKeycode.KEY_D);
        key(canvas, magnify, "", magnifying || zoom.on());
        magnifierIcon(canvas, magnify, zoom.on());
        if (zoom.on()) drawMinimap(canvas);
    }

    // A lens and its handle, lit like the torch's light while it is on.
    private void magnifierIcon(Canvas canvas, RectF where, boolean on) {
        float h = where.height();
        float r = h * 0.19f;
        float cx = where.centerX() - h * 0.05f;
        float cy = where.centerY() - h * 0.05f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(2f, h * 0.07f));
        paint.setColor(on ? Color.argb(255, 240, 196, 164) : Color.argb(220, 198, 168, 156));
        canvas.drawCircle(cx, cy, r, paint);
        float d = r * 0.7071f;
        canvas.drawLine(cx + d, cy + d, cx + d + h * 0.15f, cy + d + h * 0.15f, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        if (on) {
            float radius = h * 0.25f;
            paint.setStrokeWidth(Math.max(2f, h * 0.06f));
            paint.setColor(Color.argb(255, 219, 82, 48));
            canvas.drawRoundRect(where, radius, radius, paint);
        }
    }

    private void drawMinimap(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        float radius = 4f * density;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(190, 20, 12, 10));
        canvas.drawRoundRect(minimap, radius, radius, paint);
        if (thumbnail != null) {
            paint.setAlpha(210);
            canvas.drawBitmap(thumbnail, null, minimap, paint);
            paint.setAlpha(255);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, density));
        paint.setColor(Color.argb(160, 158, 118, 106));
        canvas.drawRoundRect(minimap, radius, radius, paint);
        float[] f = zoom.visibleFraction();
        seen.set(minimap.left + f[0] * minimap.width(), minimap.top + f[1] * minimap.height(),
            minimap.left + f[2] * minimap.width(), minimap.top + f[3] * minimap.height());
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(40, 240, 196, 164));
        canvas.drawRect(seen, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, 2f * density));
        paint.setColor(Color.argb(255, 219, 82, 48));
        canvas.drawRect(seen, paint);
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
        // Where the finger is in the unscaled view; DD1Zoom is the identity when
        // the magnifier is off.
        float[] local = zoom.toLocal(x, y);
        focusX = local[0];
        focusY = local[1];
        float[] point = XForm.transformPoint(xform(), local[0], local[1]);
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
