package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import android.widget.GridLayout;
import android.widget.LinearLayout;

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
        context.getSharedPreferences("dd1", Context.MODE_PRIVATE).edit()
            .putInt("workshop_columns", 2).commit();
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
                assertNotNull(activity.findViewById(R.id.BTWorkshopImport));
                assertNotNull(activity.findViewById(R.id.BTWorkshopColumns));
                assertTrue(activity.findViewById(R.id.BTWorkshopSearch).getLayoutParams().width
                    >= 80 * activity.getResources().getDisplayMetrics().density);
                assertEquals(0, activity.getResources().getIdentifier(
                    "TVWorkshopLog", "id", activity.getPackageName()));

                DD1WorkshopSnapshot snapshot = DD1WorkshopSnapshot.ready(
                    Collections.emptyList(), DD1Workshop.scan(context.getFilesDir()))
                    .withBrowse(Arrays.asList(
                        new DD1WorkshopItem(1, "Crimson Court", "[b]DLC info[/b]",
                            "https://cdn/hero.jpg", 10, 4, .9f, 1, true,
                            Arrays.asList("https://cdn/hero.jpg", "https://cdn/one.jpg",
                                "https://cdn/two.jpg")),
                        new DD1WorkshopItem(2, "Musketeer", "", "", 20, 8,
                            .8f, 1, true)), "", 0, 1, 2, false, null);
                workshop.renderSnapshot(snapshot);

                assertEquals(2, ((GridLayout)activity.findViewById(
                    R.id.GLWorkshopCards)).getChildCount());
                assertEquals("Crimson Court", ((TextView)activity.findViewById(
                    R.id.TVWorkshopCardTitle)).getText().toString());
                GridLayout cards = activity.findViewById(R.id.GLWorkshopCards);
                assertTrue(((GridLayout.LayoutParams)cards.getChildAt(0)
                    .getLayoutParams()).topMargin > 0);
                cards.getChildAt(0).performClick();
                assertNotNull(workshop.detailDialog);
                assertEquals("Crimson Court", ((TextView)workshop.detailDialog.findViewById(
                    R.id.TVWorkshopDetailTitle)).getText().toString());
                assertEquals("DLC info", ((TextView)workshop.detailDialog.findViewById(
                    R.id.TVWorkshopDetailDescription)).getText().toString());
                assertEquals(2, ((LinearLayout)workshop.detailDialog.findViewById(
                    R.id.LLWorkshopDetailPictures)).getChildCount());
                workshop.detailDialog.dismiss();

                activity.findViewById(R.id.BTWorkshopTabInstalled).performClick();
                workshop.renderSnapshot(snapshot);
                assertEquals("local-fixture", ((TextView)activity.findViewById(
                    R.id.TVWorkshopTitle)).getText().toString());
                assertEquals(View.VISIBLE, activity.findViewById(
                    R.id.BTWorkshopEnable).getVisibility());
            });
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.BTWorkshopColumns).performClick();
                activity.findViewById(R.id.BTWorkshopColumns).performClick();
            });
            scenario.onActivity(activity -> {
                int saved = activity.getSharedPreferences("dd1", Context.MODE_PRIVATE)
                    .getInt("workshop_columns", -1);
                assertEquals(4, saved);
                assertEquals(4,
                    ((GridLayout)activity.findViewById(R.id.GLWorkshopCards)).getColumnCount());
            });
        }
        DD1Workshop.delete(context.getFilesDir(), "local-fixture", true);
    }
}
