package com.winlator.dd1;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;

// The launcher's own notices: a card that slides in at the bottom right, says
// one thing - how many mods are being synchronised, how many were removed - and
// goes. Android's toast is a grey pill in the middle of the screen with no room
// for what kind of news it is.
//
// Anything may post one, the install service included. They show while the
// launcher is on screen; one that arrives while it is not (the game running) is
// held briefly and shown when the launcher comes back, never drawn over the game.
public final class DD1Notices {
    public enum Kind { INFO, SUCCESS, WARNING }

    private static final long SHOW_MILLIS = 4000;
    private static final long KEEP_WAITING_MILLIS = 60_000;
    private static final int MAX_WAITING = 4;

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static WeakReference<Host> host = new WeakReference<>(null);
    private static final Deque<Pending> waiting = new ArrayDeque<>();

    private DD1Notices() {}

    // The screen that draws them: a container at the bottom right of the
    // launcher's window.
    public interface Host {
        LinearLayout noticeContainer();
        Activity activity();
    }

    private static final class Pending {
        final String text;
        final Kind kind;
        final long at;

        Pending(String text, Kind kind) {
            this.text = text;
            this.kind = kind;
            this.at = SystemClock.elapsedRealtime();
        }
    }

    public static void attach(Host screen) {
        main.post(() -> {
            host = new WeakReference<>(screen);
            long now = SystemClock.elapsedRealtime();
            while (!waiting.isEmpty()) {
                Pending next = waiting.pollFirst();
                if (now - next.at < KEEP_WAITING_MILLIS) draw(screen, next.text, next.kind);
            }
        });
    }

    public static void detach(Host screen) {
        main.post(() -> {
            if (host.get() == screen) host = new WeakReference<>(null);
        });
    }

    public static void show(Context context, int text, Kind kind) {
        show(context.getString(text), kind);
    }

    public static void show(String text, Kind kind) {
        main.post(() -> {
            Host screen = host.get();
            if (screen != null && !screen.activity().isFinishing()) {
                draw(screen, text, kind);
                return;
            }
            if (waiting.size() >= MAX_WAITING) waiting.pollFirst();
            waiting.addLast(new Pending(text, kind));
        });
    }

    private static void draw(Host screen, String text, Kind kind) {
        LinearLayout container = screen.noticeContainer();
        if (container == null) return;
        Context context = container.getContext();
        float density = context.getResources().getDisplayMetrics().density;

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(com.winlator.R.drawable.dd1_notice_background);
        card.setElevation(8 * density);
        int pad = Math.round(14 * density);
        card.setPadding(Math.round(10 * density), pad, Math.round(18 * density), pad);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Math.round(8 * density);
        params.gravity = Gravity.END;
        card.setLayoutParams(params);

        View bar = new View(context);
        android.graphics.drawable.GradientDrawable stripe = new android.graphics.drawable.GradientDrawable();
        stripe.setColor(colorOf(kind));
        stripe.setCornerRadius(2 * density);
        bar.setBackground(stripe);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
            Math.round(4 * density), Math.round(28 * density));
        barParams.setMarginEnd(Math.round(12 * density));
        card.addView(bar, barParams);

        TextView message = new TextView(context);
        message.setText(text);
        message.setTextColor(0xFFFAFAFA);
        message.setTextSize(14);
        message.setMaxWidth(Math.round(420 * density));
        card.addView(message);

        card.setAlpha(0f);
        card.setTranslationX(48 * density);
        container.addView(card);
        card.animate().alpha(1f).translationX(0f).setDuration(220).start();
        card.setOnClickListener(v -> dismiss(container, card, density));
        main.postDelayed(() -> dismiss(container, card, density), SHOW_MILLIS);
    }

    private static void dismiss(LinearLayout container, View card, float density) {
        if (card.getParent() == null || card.getTag() != null) return;
        card.setTag("leaving");
        card.animate().alpha(0f).translationX(48 * density).setDuration(220)
            .withEndAction(() -> container.removeView(card)).start();
    }

    private static int colorOf(Kind kind) {
        switch (kind) {
            case SUCCESS: return 0xFF43A047;
            case WARNING: return 0xFFFB8C00;
            default: return 0xFF4FB3E8;
        }
    }
}
