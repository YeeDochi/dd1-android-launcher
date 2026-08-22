package com.winlator.dd1;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import androidx.drawerlayout.widget.DrawerLayout;

// The runtime's own menu, the drawer that slides in from the left. It is off by
// default here, because a controller opens it by accident: a button the game does
// not use is turned into Back by Android, the activity answers Back by opening the
// drawer, and the first thing in the drawer is Keyboard. Pressing that button
// twice puts the soft keyboard over the game.
//
// Locking the drawer is not enough and looks as though it were - the runtime
// already locks it at startup. LOCK_MODE_LOCKED_CLOSED only refuses the finger
// that drags it open; openDrawer() from code goes through a lock untouched, and
// that call is what Back reaches. So the drawer is closed again the moment it
// starts to move, which is the one place every way of opening it passes through.
public final class DD1RuntimeMenu {
    private static final String KEY = "runtime_menu";

    private DD1RuntimeMenu() {}

    public static boolean enabled(Context context) {
        return preferences(context).getBoolean(KEY, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY, enabled).apply();
    }

    public static void apply(Activity activity) {
        if (enabled(activity)) return;
        View view = activity.findViewById(com.winlator.R.id.DrawerLayout);
        if (!(view instanceof DrawerLayout)) return;
        DrawerLayout drawer = (DrawerLayout)view;
        drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerSlide(View panel, float offset) {
                // Without animating: the panel is a few pixels out at this point
                // and closing it over the next frames would show it opening.
                if (offset > 0) drawer.closeDrawer(panel, false);
            }
        });
    }

    private static android.content.SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
            .getSharedPreferences("dd1", Context.MODE_PRIVATE);
    }
}
