package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.PatternMatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Button;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.winlator.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Collections;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class DD1WorkshopFragmentTest {
    // Details were reachable from the store only. An installed mod is the one you
    // actually want to read about - it is on the device and you are deciding
    // whether to keep it.
    @Test
    public void anInstalledWorkshopModOpensItsDetails() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File mod = new File(context.getFilesDir(), "game/mods/909090");
        mod.mkdirs();
        java.nio.file.Files.write(new File(mod, ".dd1-workshop").toPath(),
            "909090\n1700000000\nDetail Fixture\n".getBytes("UTF-8"));
        try (ActivityScenario<DD1Activity> scenario = ActivityScenario.launch(DD1Activity.class)) {
            scenario.onActivity(activity -> {
                DD1WorkshopFragment workshop = new DD1WorkshopFragment();
                activity.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.FLDD1Container, workshop).commitNow();
                activity.getSupportFragmentManager().executePendingTransactions();

                workshop.renderSnapshot(DD1WorkshopSnapshot.ready(
                    Collections.emptyList(), DD1Workshop.scan(context.getFilesDir())));
                activity.findViewById(R.id.BTWorkshopTabInstalled).performClick();
                workshop.renderSnapshot(DD1WorkshopSnapshot.ready(
                    Collections.emptyList(), DD1Workshop.scan(context.getFilesDir())));

                LinearLayout rows = activity.findViewById(R.id.LLWorkshopList);
                View fixture = null;
                for (int i = 0; i < rows.getChildCount(); i++) {
                    View row = rows.getChildAt(i);
                    if ("Detail Fixture".equals(((TextView)row.findViewById(
                            R.id.TVWorkshopTitle)).getText().toString())) fixture = row;
                }
                assertNotNull("the fixture is listed", fixture);
                assertTrue("the row is what opens it", fixture.isClickable());

                fixture.performClick();
                assertNotNull("a detail sheet opened", workshop.detailDialog);
                assertEquals("Detail Fixture", ((TextView)workshop.detailDialog
                    .findViewById(R.id.TVWorkshopDetailTitle)).getText().toString());
                // Steam has not answered yet, and zeroes would read as facts.
                assertEquals(View.GONE, workshop.detailDialog
                    .findViewById(R.id.TVWorkshopDetailMeta).getVisibility());
                workshop.detailDialog.dismiss();
            });
        }
        finally {
            deleteTree(mod);
        }
    }

    // The card grid is rebuilt from scratch on every snapshot, and a sync used to
    // publish one per chunk. Re-reading each picture off disk on a background
    // thread is what made them blink, so one already decoded has to be drawn in
    // the same pass that builds the view.
    @Test
    public void aPictureAlreadyDecodedIsDrawnWithoutABlankFrame() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        String url = "https://cdn.example/blink-test.png";
        File cached = DD1WorkshopImages.file(context.getCacheDir(), url);
        cached.getParentFile().mkdirs();
        try (java.io.OutputStream out = new java.io.FileOutputStream(cached)) {
            android.graphics.Bitmap.createBitmap(4, 4,
                android.graphics.Bitmap.Config.ARGB_8888)
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        }
        assertNotNull("warmed from disk once",
            DD1WorkshopImages.load(context.getCacheDir(), url));
        assertNotNull("and kept in memory", DD1WorkshopImages.inMemory(url));

        try (ActivityScenario<DD1Activity> scenario = ActivityScenario.launch(DD1Activity.class)) {
            scenario.onActivity(activity -> {
                DD1WorkshopFragment workshop = new DD1WorkshopFragment();
                activity.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.FLDD1Container, workshop).commitNow();
                activity.getSupportFragmentManager().executePendingTransactions();

                workshop.renderSnapshot(DD1WorkshopSnapshot.ready(
                    Collections.emptyList(), Collections.emptyList())
                    .withBrowse(Collections.singletonList(new DD1WorkshopItem(
                        7, "Blink", "", url, 10, 4, .9f, 1, true)),
                        "", 0, 1, 1, false, null));

                // Read straight after the render, with nothing posted in between.
                assertNotNull("drawn in the same pass",
                    ((ImageView)activity.findViewById(R.id.IVWorkshopCard)).getDrawable());
            });
        }
        finally {
            cached.delete();
        }
    }

    // A sync reports itself while the list is on screen. Rebuilding the grid for
    // that drops the scroll position and swaps the card out from under a finger
    // that was reaching for Subscribe, so a progress report has to leave the views
    // where they are.
    @Test
    public void aProgressReportDoesNotRebuildTheList() {
        try (ActivityScenario<DD1Activity> scenario = ActivityScenario.launch(DD1Activity.class)) {
            scenario.onActivity(activity -> {
                DD1WorkshopFragment workshop = new DD1WorkshopFragment();
                activity.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.FLDD1Container, workshop).commitNow();
                activity.getSupportFragmentManager().executePendingTransactions();

                java.util.List<DD1WorkshopItem> browse = Arrays.asList(
                    new DD1WorkshopItem(11, "First", "", "", 10, 4, .9f, 1, true),
                    new DD1WorkshopItem(12, "Second", "", "", 20, 8, .8f, 1, true));
                workshop.renderSnapshot(DD1WorkshopSnapshot.ready(
                    Collections.emptyList(), Collections.emptyList())
                    .withBrowse(browse, "", 0, 1, 2, false, null));

                GridLayout grid = activity.findViewById(R.id.GLWorkshopCards);
                assertEquals(2, grid.getChildCount());
                View firstCard = grid.getChildAt(0);
                View secondCard = grid.getChildAt(1);

                // The same list, one mod now being fetched at 40%.
                workshop.renderSnapshot(DD1WorkshopSnapshot.ready(
                    Collections.emptyList(), Collections.emptyList())
                    .withBrowse(browse, "", 0, 1, 2, false, null)
                    .syncing("First", 40, Collections.emptyList()));

                assertEquals("the cards are the same views", firstCard, grid.getChildAt(0));
                assertEquals(secondCard, grid.getChildAt(1));
                // A download's progress is the installed tab's business. Nothing
                // on a store card reports it, which is why nothing here had to be
                // redrawn.
                assertEquals(0, activity.getResources().getIdentifier(
                    "PBWorkshopCard", "id", activity.getPackageName()));
                // Both remain a plain subscribe toggle while the sync runs.
                assertTrue(firstCard.findViewById(R.id.BTWorkshopCardAction).isEnabled());
                assertTrue(secondCard.findViewById(R.id.BTWorkshopCardAction).isEnabled());

                // Subscribing during a sync has to show on the card, or it reads
                // as a button that did nothing - which is exactly how it read.
                DD1WorkshopSnapshot syncing = DD1WorkshopSnapshot.ready(
                    Collections.emptyList(), Collections.emptyList())
                    .withBrowse(browse, "", 0, 1, 2, false, null)
                    .syncing("First", 40, Collections.emptyList());
                workshop.renderSnapshot(syncing.withSubscribed(12));
                View flipped = null;
                GridLayout after = activity.findViewById(R.id.GLWorkshopCards);
                for (int i = 0; i < after.getChildCount(); i++) {
                    View c = after.getChildAt(i);
                    if ("Second".equals(((TextView)c.findViewById(
                            R.id.TVWorkshopCardTitle)).getText().toString())) flipped = c;
                }
                assertNotNull(flipped);
                assertEquals(activity.getString(R.string.dd1_workshop_unsubscribe),
                    ((Button)flipped.findViewById(
                        R.id.BTWorkshopCardAction)).getText().toString());

                // A list that actually changed is drawn again.
                workshop.renderSnapshot(DD1WorkshopSnapshot.ready(
                    Collections.emptyList(), Collections.emptyList())
                    .withBrowse(Collections.singletonList(browse.get(0)), "", 0, 1, 1,
                        false, null));
                assertEquals(1, grid.getChildCount());
            });
        }
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

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
                assertEquals(ImageView.ScaleType.FIT_CENTER,
                    ((ImageView)activity.findViewById(R.id.IVWorkshopCard)).getScaleType());
                cards.getChildAt(0).performClick();
                assertNotNull(workshop.detailDialog);
                assertEquals("Crimson Court", ((TextView)workshop.detailDialog.findViewById(
                    R.id.TVWorkshopDetailTitle)).getText().toString());
                assertEquals("DLC info", ((TextView)workshop.detailDialog.findViewById(
                    R.id.TVWorkshopDetailDescription)).getText().toString());
                assertEquals(2, ((LinearLayout)workshop.detailDialog.findViewById(
                    R.id.LLWorkshopDetailPictures)).getChildCount());
                assertEquals(ImageView.ScaleType.FIT_CENTER,
                    ((ImageView)workshop.detailDialog.findViewById(
                        R.id.IVWorkshopDetailHero)).getScaleType());
                assertEquals(ImageView.ScaleType.FIT_CENTER,
                    ((ImageView)((LinearLayout)workshop.detailDialog.findViewById(
                        R.id.LLWorkshopDetailPictures)).getChildAt(0)).getScaleType());
                Button subscribe = workshop.detailDialog.findViewById(
                    R.id.BTWorkshopDetailSubscribe);
                Button close = workshop.detailDialog.findViewById(R.id.BTWorkshopDetailClose);
                Button web = workshop.detailDialog.findViewById(R.id.BTWorkshopDetailWeb);
                assertEquals(activity.getString(R.string.dd1_workshop_subscribe),
                    subscribe.getText().toString());
                assertTrue(subscribe.getBackground() != null);
                assertTrue(close.getBackground() != null);
                assertTrue(subscribe.getMinimumHeight() >= 48
                    * activity.getResources().getDisplayMetrics().density);
                assertEquals(web, ((LinearLayout)subscribe.getParent()).getChildAt(0));
                assertEquals(subscribe, ((LinearLayout)subscribe.getParent()).getChildAt(1));
                assertEquals(close, ((LinearLayout)close.getParent()).getChildAt(2));
                IntentFilter webFilter = new IntentFilter(Intent.ACTION_VIEW);
                webFilter.addDataScheme("https");
                webFilter.addDataAuthority("steamcommunity.com", null);
                webFilter.addDataPath("/sharedfiles/filedetails/", PatternMatcher.PATTERN_LITERAL);
                Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
                Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor(
                    webFilter, null, true);
                instrumentation.addMonitor(monitor);
                web.performClick();
                assertEquals(1, monitor.getHits());
                instrumentation.removeMonitor(monitor);
                workshop.detailDialog.dismiss();

                context.getSharedPreferences("dd1", Context.MODE_PRIVATE).edit()
                    .putInt("workshop_columns", 4).commit();
                AttachedChildStrictGrid strict = new AttachedChildStrictGrid(activity);
                strict.setId(R.id.GLWorkshopCards);
                strict.setLayoutParams(cards.getLayoutParams());
                while (cards.getChildCount() > 0) {
                    View child = cards.getChildAt(0);
                    cards.removeViewAt(0);
                    strict.addView(child);
                }
                ViewGroup gridParent = (ViewGroup)cards.getParent();
                int gridIndex = gridParent.indexOfChild(cards);
                gridParent.removeViewAt(gridIndex);
                gridParent.addView(strict, gridIndex);
                ((GridLayout.LayoutParams)strict.getChildAt(0).getLayoutParams()).columnSpec =
                    GridLayout.spec(3, 1f);
                activity.findViewById(R.id.BTWorkshopColumns).performClick();
                assertEquals(2, strict.getColumnCount());
                assertEquals(2, strict.getChildCount());

                activity.findViewById(R.id.BTWorkshopTabInstalled).performClick();
                workshop.renderSnapshot(snapshot);
                // The list is whatever the device actually has installed, so a
                // real mod left there by hand sorts wherever it sorts. Find the
                // fixture rather than assuming it came first.
                LinearLayout rows = activity.findViewById(R.id.LLWorkshopList);
                View fixture = null;
                for (int i = 0; i < rows.getChildCount(); i++) {
                    View row = rows.getChildAt(i);
                    if ("local-fixture".equals(((TextView)row.findViewById(
                            R.id.TVWorkshopTitle)).getText().toString())) fixture = row;
                }
                assertNotNull(fixture);
                assertEquals(View.VISIBLE,
                    fixture.findViewById(R.id.BTWorkshopEnable).getVisibility());
            });
            scenario.onActivity(activity -> {
                int saved = activity.getSharedPreferences("dd1", Context.MODE_PRIVATE)
                    .getInt("workshop_columns", -1);
                assertEquals(2, saved);
                assertEquals(2,
                    ((GridLayout)activity.findViewById(R.id.GLWorkshopCards)).getColumnCount());
                View rotate = activity.findViewById(R.id.BTWorkshopRotate);
                LinearLayout controls = (LinearLayout)rotate.getParent();
                assertEquals(rotate, controls.getChildAt(controls.getChildCount() - 1));
                int expected = activity.getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE
                    ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
                rotate.performClick();
                assertEquals(expected, activity.getRequestedOrientation());
            });
        }
        DD1Workshop.delete(context.getFilesDir(), "local-fixture", true);
    }

    private static final class AttachedChildStrictGrid extends GridLayout {
        AttachedChildStrictGrid(Context context) {
            super(context);
            super.setColumnCount(4);
        }

        @Override
        public void setColumnCount(int count) {
            if (getChildCount() > 0 && count < getColumnCount())
                throw new IllegalArgumentException("attached children must be detached first");
            super.setColumnCount(count);
        }
    }

    // The rotate button asks the activity for portrait, and the activity outlives
    // the screen that asked. Leaving the mod manager still turned sideways left
    // every other screen sideways with no control anywhere to undo it.
    @Test
    public void leavingTheModManagerGivesTheScreenBackItsOrientation() {
        try (ActivityScenario<DD1Activity> scenario = ActivityScenario.launch(DD1Activity.class)) {
            scenario.onActivity(activity -> {
                activity.toggleDrawer();
                activity.findViewById(R.id.BTDrawerWorkshop).performClick();
                activity.getSupportFragmentManager().executePendingTransactions();

                activity.findViewById(R.id.BTWorkshopRotate).performClick();
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
                    activity.getRequestedOrientation());

                activity.toggleDrawer();
                activity.findViewById(R.id.BTDrawerSettings).performClick();
                activity.getSupportFragmentManager().executePendingTransactions();
            });
            scenario.onActivity(activity -> assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
                activity.getRequestedOrientation()));
        }
    }
}
