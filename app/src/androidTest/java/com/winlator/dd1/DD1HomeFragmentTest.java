package com.winlator.dd1;

import com.winlator.R;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.winlator.dd1.DD1InstallPhase;
import com.winlator.dd1.DD1InstallSnapshot;

import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DD1HomeFragmentTest {
    @Test
    public void rendersSteamInstallControlsFromServiceState() {
        try (ActivityScenario<DD1Activity> scenario = ActivityScenario.launch(DD1Activity.class)) {
            scenario.onActivity(activity -> {
                activity.getSupportFragmentManager().executePendingTransactions();
                Fragment fragment = activity.getSupportFragmentManager()
                    .findFragmentById(R.id.FLDD1Container);
                DD1HomeFragment home = (DD1HomeFragment)fragment;

                home.renderInstallSnapshot(snapshot(DD1InstallPhase.SIGNED_OUT));
                assertEquals(View.VISIBLE, activity.findViewById(R.id.BTSteamLogin).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.ETSteamAccount).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.ETSteamPassword).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.BTSteamCredentials).getVisibility());
                assertTrue(activity.findViewById(R.id.ETSteamAccount).getTop() <
                    activity.findViewById(R.id.BTSteamLogin).getTop());
                assertEquals(View.GONE, activity.findViewById(R.id.BTPrimaryAction).getVisibility());

                home.renderInstallSnapshot(snapshot(DD1InstallPhase.READY_TO_INSTALL));
                assertEquals(View.VISIBLE, activity.findViewById(R.id.BTDownload).getVisibility());

                home.renderInstallSnapshot(snapshot(DD1InstallPhase.DOWNLOADING));
                assertEquals(View.VISIBLE, activity.findViewById(R.id.PBDownload).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.TVDownloadPercent).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.TVInstallLog).getVisibility());
            });
        }
    }

    private static DD1InstallSnapshot snapshot(DD1InstallPhase phase) {
        return new DD1InstallSnapshot(phase, 1, 2, 1, phase.name(), "file",
            null, Collections.singletonList("log"));
    }
}
