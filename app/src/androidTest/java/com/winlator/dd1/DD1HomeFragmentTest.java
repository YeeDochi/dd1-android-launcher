package com.winlator.dd1;

import com.winlator.R;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
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
                // Measured coordinates are all zero until a layout pass runs, and
                // there is none between making these visible and reading them.
                // Both sit in the same column, so their order in it is the claim.
                View account = activity.findViewById(R.id.ETSteamAccount);
                ViewGroup column = (ViewGroup)account.getParent();
                assertTrue(column.indexOfChild(account)
                    < column.indexOfChild(activity.findViewById(R.id.BTSteamLogin)));
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

    // The accounts this was written for cannot sign in any other way: no mobile
    // authenticator means no QR either, so if this box does not appear there is no
    // way in at all.
    @Test
    public void aSteamGuardCodeRequestPutsABoxOnScreen() {
        try (ActivityScenario<DD1Activity> scenario = ActivityScenario.launch(DD1Activity.class)) {
            scenario.onActivity(activity -> {
                activity.getSupportFragmentManager().executePendingTransactions();
                DD1HomeFragment home = (DD1HomeFragment)activity.getSupportFragmentManager()
                    .findFragmentById(R.id.FLDD1Container);

                home.renderInstallSnapshot(snapshot(DD1InstallPhase.AUTHENTICATING)
                    .asking(DD1SignInCode.email("player@example.com", false)));

                assertEquals(View.VISIBLE, activity.findViewById(R.id.ETSteamCode).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.BTSteamCode).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.IVSteamQr).getVisibility());
                CharSequence hint = ((android.widget.TextView)activity
                    .findViewById(R.id.TVSteamCodeHint)).getText();
                assertTrue("the address Steam mailed is named: " + hint,
                    hint.toString().contains("player@example.com"));

                // A rejected code has to say so, or the same box comes back looking
                // like nothing happened.
                home.renderInstallSnapshot(snapshot(DD1InstallPhase.AUTHENTICATING)
                    .asking(DD1SignInCode.authenticator(true)));
                CharSequence again = ((android.widget.TextView)activity
                    .findViewById(R.id.TVSteamCodeHint)).getText();
                assertTrue("a rejected code is reported: " + again,
                    again.toString().contains(activity.getString(R.string.dd1_steam_code_wrong)));

                // And a QR sign-in still shows the QR rather than a code box.
                home.renderInstallSnapshot(new DD1InstallSnapshot(
                    DD1InstallPhase.AUTHENTICATING, 0, 0, 0, "qr", null,
                    "https://s.team/q/1/2", Collections.singletonList("log")));
                assertEquals(View.GONE, activity.findViewById(R.id.ETSteamCode).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.IVSteamQr).getVisibility());
            });
        }
    }

    // A profile is made by creating the directory, unpacking a tar into it and
    // writing the config last. A download publishes a snapshot several times a
    // second and every one reaches refresh(), so a first run that downloads the
    // game reads that directory again and again while it has no config yet.
    @Test
    public void aProfileCaughtHalfMadeDoesNotTakeTheScreenDown() throws Exception {
        java.io.File halfMade = new java.io.File(
            ApplicationProvider.getApplicationContext().getFilesDir(),
            "rootfs/home/xuser-9");
        assertTrue(halfMade.mkdirs() || halfMade.isDirectory());
        try (ActivityScenario<DD1Activity> scenario = ActivityScenario.launch(DD1Activity.class)) {
            scenario.onActivity(activity -> {
                activity.getSupportFragmentManager().executePendingTransactions();
                DD1HomeFragment home = (DD1HomeFragment)activity.getSupportFragmentManager()
                    .findFragmentById(R.id.FLDD1Container);

                // This is the call the crash came out of, three times a second.
                home.renderInstallSnapshot(snapshot(DD1InstallPhase.DOWNLOADING));
                home.renderInstallSnapshot(snapshot(DD1InstallPhase.READY_TO_INSTALL));
                home.renderInstallSnapshot(snapshot(DD1InstallPhase.SIGNED_OUT));

                assertEquals(View.VISIBLE,
                    activity.findViewById(R.id.BTSteamLogin).getVisibility());
            });
        }
        finally {
            halfMade.delete();
        }
    }

    private static DD1InstallSnapshot snapshot(DD1InstallPhase phase) {
        return new DD1InstallSnapshot(phase, 1, 2, 1, phase.name(), "file",
            null, Collections.singletonList("log"));
    }
}
