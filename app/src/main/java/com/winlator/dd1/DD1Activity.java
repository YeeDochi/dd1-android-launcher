package com.winlator.dd1;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

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
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.FLDD1Container, new DD1HomeFragment())
                .commit();
        }
    }
}
