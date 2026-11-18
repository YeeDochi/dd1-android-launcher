package com.winlator.dd1;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.winlator.R;
import com.winlator.contentdialog.AboutDialog;
import com.winlator.core.AppUtils;
import com.winlator.xenvironment.RootFSInstaller;

// The launcher entry point. Winlator's own MainActivity stays untouched so the
// runtime can be updated wholesale.
public class DD1Activity extends AppCompatActivity implements DD1Notices.Host {
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
            showScreen(new DD1DlcFragment());
        });
        findViewById(R.id.BTDrawerSaves).setOnClickListener(v -> {
            closeDrawer();
            showScreen(new DD1SavesFragment());
        });
        findViewById(R.id.BTDrawerWorkshop).setOnClickListener(v -> {
            closeDrawer();
            showScreen(new DD1WorkshopFragment());
        });
        findViewById(R.id.BTDrawerSettings).setOnClickListener(v -> {
            closeDrawer();
            showScreen(new DD1SettingsFragment());
        });
        // The open-source notice is the runtime's own text; it stays a dialog
        // rather than being rewritten as a screen.
        findViewById(R.id.BTDrawerAbout).setOnClickListener(v -> {
            closeDrawer();
            new AboutDialog(this).show();
        });
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.FLDD1Container, new DD1HomeFragment())
                .commit();
        }
    }

    // Each screen binds the install service while it is shown, and a screen
    // change stops the old one before the new one starts. In between nothing
    // was bound, an idle service stopped, the Steam session closed with it, and
    // the next screen signed in again from the stored token - on every visit to
    // the settings. Bound here as well, the service lives as long as the
    // launcher is on screen.
    private final ServiceConnection keepService = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {}

        @Override
        public void onServiceDisconnected(ComponentName name) {}
    };
    private boolean serviceBound;

    @Override
    protected void onStart() {
        super.onStart();
        serviceBound = bindService(new Intent(this, DD1InstallService.class), keepService, BIND_AUTO_CREATE);
        DD1Notices.attach(this);
    }

    @Override
    public android.widget.LinearLayout noticeContainer() {
        return findViewById(R.id.LLDD1Notices);
    }

    @Override
    public android.app.Activity activity() {
        return this;
    }

    @Override
    protected void onStop() {
        DD1Notices.detach(this);
        if (serviceBound) {
            unbindService(keepService);
            serviceBound = false;
        }
        super.onStop();
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

    public void toggleDrawer() {
        DrawerLayout drawer = findViewById(R.id.DLDD1Drawer);
        if (drawer.isDrawerOpen(Gravity.START)) drawer.closeDrawer(Gravity.START);
        else drawer.openDrawer(Gravity.START);
    }

    private void closeDrawer() {
        ((DrawerLayout)findViewById(R.id.DLDD1Drawer)).closeDrawer(Gravity.START);
    }

    // Home is the root, so it keeps no entry of its own and the system back
    // button returns to it.
    private void showScreen(androidx.fragment.app.Fragment screen) {
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.FLDD1Container, screen)
            .addToBackStack(null)
            .commit();
    }
}
