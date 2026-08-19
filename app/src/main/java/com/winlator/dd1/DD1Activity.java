package com.winlator.dd1;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.winlator.R;
import com.winlator.core.AppUtils;
import com.winlator.xenvironment.RootFSInstaller;

// The launcher entry point. Winlator's own MainActivity stays untouched so the
// runtime can be updated wholesale.
public class DD1Activity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dd1_activity);
        // The clock and battery earn no space on a landscape launcher; the
        // runtime's own screens hide them the same way.
        AppUtils.hideSystemUI(this);
        // Winlator unpacks its root filesystem from its own entry point, which the
        // launcher replaces, so the same call happens here.
        RootFSInstaller.installIfNeeded(this);
        findViewById(R.id.BTDrawerDlc).setOnClickListener(v -> {
            closeDrawer();
            openDlc();
        });
        findViewById(R.id.BTDrawerLanguage).setOnClickListener(v -> {
            closeDrawer();
            chooseLanguage();
        });
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.FLDD1Container, new DD1HomeFragment())
                .commit();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) AppUtils.hideSystemUI(this);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(DD1Locale.wrap(base));
    }

    private void chooseLanguage() {
        String[] tags = {DD1Locale.SYSTEM, "en", "ko"};
        String chosen = DD1Locale.chosen(this);
        int selected = 0;
        for (int i = 0; i < tags.length; i++) {
            if (tags[i].equals(chosen)) selected = i;
        }
        new AlertDialog.Builder(this, R.style.DD1Dialog)
            .setTitle(R.string.dd1_language)
            .setSingleChoiceItems(R.array.dd1_languages, selected, (dialog, which) -> {
                dialog.dismiss();
                if (tags[which].equals(DD1Locale.chosen(this))) return;
                DD1Locale.choose(this, tags[which]);
                recreate();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    public void toggleDrawer() {
        DrawerLayout drawer = findViewById(R.id.DLDD1Drawer);
        if (drawer.isDrawerOpen(Gravity.START)) drawer.closeDrawer(Gravity.START);
        else drawer.openDrawer(Gravity.START);
    }

    private void closeDrawer() {
        ((DrawerLayout)findViewById(R.id.DLDD1Drawer)).closeDrawer(Gravity.START);
    }

    private void openDlc() {
        DD1HomeFragment home = (DD1HomeFragment)getSupportFragmentManager()
            .findFragmentById(R.id.FLDD1Container);
        if (home != null) home.showDlcDialog();
    }
}
