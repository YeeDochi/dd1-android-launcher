package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import android.widget.GridLayout;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.winlator.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Collections;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class DD1WorkshopFragmentTest {
    @Test
    public void drawerOpensAStorefrontAndInstalledModManager() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File local = new File(context.getFilesDir(), "game/mods-disabled/local-fixture");
        if (local.exists()) DD1Workshop.delete(context.getFilesDir(), "local-fixture", true);
        assertTrue(local.mkdirs());

        try (ActivityScenario<DD1Activity> scenario = ActivityScenario.launch(DD1Activity.class)) {
            scenario.onActivity(activity -> {
                activity.toggleDrawer();
                activity.findViewById(R.id.BTDrawerWorkshop).performClick();
                activity.getSupportFragmentManager().executePendingTransactions();

                Fragment fragment = activity.getSupportFragmentManager()
                    .findFragmentById(R.id.FLDD1Container);
                assertTrue(fragment instanceof DD1WorkshopFragment);
                DD1WorkshopFragment workshop = (DD1WorkshopFragment)fragment;
                assertEquals(activity.getString(R.string.dd1_workshop),
                    ((TextView)activity.findViewById(R.id.TVScreenTitle)).getText().toString());
                assertNotNull(activity.findViewById(R.id.TIWorkshopSearch));
                assertNotNull(activity.findViewById(R.id.BTWorkshopTabStore));
                assertNotNull(activity.findViewById(R.id.BTWorkshopTabInstalled));
                assertTrue(activity.findViewById(R.id.BTWorkshopSearch).getLayoutParams().width
                    >= 80 * activity.getResources().getDisplayMetrics().density);
                assertEquals(0, activity.getResources().getIdentifier(
                    "TVWorkshopLog", "id", activity.getPackageName()));

                DD1WorkshopSnapshot snapshot = DD1WorkshopSnapshot.ready(
                    Collections.emptyList(), DD1Workshop.scan(context.getFilesDir()))
                    .withBrowse(Arrays.asList(
                        new DD1WorkshopItem(1, "Crimson Court", "", "", 10, 4,
                            .9f, 1, true),
                        new DD1WorkshopItem(2, "Musketeer", "", "", 20, 8,
                            .8f, 1, true)), "", 0, 1, 2, false, null);
                workshop.renderSnapshot(snapshot);

                assertEquals(2, ((GridLayout)activity.findViewById(
                    R.id.GLWorkshopCards)).getChildCount());
                assertEquals("Crimson Court", ((TextView)activity.findViewById(
                    R.id.TVWorkshopCardTitle)).getText().toString());

                activity.findViewById(R.id.BTWorkshopTabInstalled).performClick();
                workshop.renderSnapshot(snapshot);
                assertEquals("local-fixture", ((TextView)activity.findViewById(
                    R.id.TVWorkshopTitle)).getText().toString());
                assertEquals(View.VISIBLE, activity.findViewById(
                    R.id.BTWorkshopEnable).getVisibility());
            });
        }
        DD1Workshop.delete(context.getFilesDir(), "local-fixture", true);
    }
}
