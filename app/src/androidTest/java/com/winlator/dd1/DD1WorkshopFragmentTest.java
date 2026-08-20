package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.winlator.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class DD1WorkshopFragmentTest {
    @Test
    public void drawerOpensTheWorkshopManagerAndRendersALocalMod() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File local = new File(context.getFilesDir(), "game/mods/local-fixture");
        if (local.exists()) DD1Workshop.delete(context.getFilesDir(), "local-fixture");
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
                assertNotNull(activity.findViewById(R.id.BTWorkshopRefresh));

                workshop.renderSnapshot(DD1WorkshopSnapshot.ready(Collections.emptyList(),
                    DD1Workshop.scan(context.getFilesDir())));

                assertEquals("local-fixture", ((TextView)activity.findViewById(
                    R.id.TVWorkshopTitle)).getText().toString());
                assertEquals(View.VISIBLE, activity.findViewById(
                    R.id.BTWorkshopDelete).getVisibility());
            });
        }
        DD1Workshop.delete(context.getFilesDir(), "local-fixture");
    }
}
