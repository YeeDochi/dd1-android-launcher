package com.winlator.dd1;

import android.os.Bundle;
import android.view.Gravity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.winlator.R;
import com.winlator.xenvironment.RootFSInstaller;

// The launcher entry point. Winlator's own MainActivity stays untouched so the
// runtime can be updated wholesale.
public class DD1Activity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dd1_activity);
        // Winlator unpacks its root filesystem from its own entry point, which the
        // launcher replaces, so the same call happens here.
        RootFSInstaller.installIfNeeded(this);
        findViewById(R.id.BTDrawerDlc).setOnClickListener(v -> {
            closeDrawer();
            openDlc();
        });
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.FLDD1Container, new DD1HomeFragment())
                .commit();
        }
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
