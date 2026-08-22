package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.winlator.R;
import com.winlator.box64.Box64Preset;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DD1SettingsFragmentTest {
    // The faster translation preset trades safety for battery, so the screen has
    // to say so beside the switch rather than leaving the reader to find out.
    @Test
    public void offersTheFasterTranslationWithItsWarning() {
        try (ActivityScenario<DD1Activity> scenario = ActivityScenario.launch(DD1Activity.class)) {
            scenario.onActivity(activity -> {
                activity.toggleDrawer();
                activity.findViewById(R.id.BTDrawerSettings).performClick();
                activity.getSupportFragmentManager().executePendingTransactions();

                Fragment fragment = activity.getSupportFragmentManager()
                    .findFragmentById(R.id.FLDD1Container);
                assertTrue(fragment instanceof DD1SettingsFragment);

                CheckBox toggle = activity.findViewById(R.id.CBBox64Performance);
                assertNotNull(toggle);
                String warning = ((TextView)activity.findViewById(
                    R.id.TVBox64Hint)).getText().toString();
                assertTrue(warning, warning.length() > 20);
                assertEquals(View.VISIBLE, toggle.getVisibility());

                // Without a runtime profile there is nothing to write to, and an
                // enabled switch that silently does nothing is worse than none.
                ContainerManager manager = new ContainerManager(activity);
                if (manager.getContainers().isEmpty()) {
                    assertTrue(!toggle.isEnabled());
                    return;
                }

                Container container = manager.getContainers().get(0);
                String before = container.getBox64Preset();
                toggle.performClick();
                assertEquals(toggle.isChecked() ? Box64Preset.PERFORMANCE : Box64Preset.DEFAULT,
                    container.getBox64Preset());
                toggle.performClick();
                assertEquals(before == null || before.equals(Box64Preset.PERFORMANCE)
                    ? container.getBox64Preset() : Box64Preset.DEFAULT,
                    container.getBox64Preset());
            });
        }
    }

    // Both knobs exist because translating x86 is where the battery goes and
    // there is no frame limiter to spend the headroom on. They have to write
    // where the runtime reads, or the screen is a decoration.
    @Test
    public void writesTheCoreBudgetAndTheRefreshRateChoice() {
        try (ActivityScenario<DD1Activity> scenario = ActivityScenario.launch(DD1Activity.class)) {
            scenario.onActivity(activity -> {
                activity.toggleDrawer();
                activity.findViewById(R.id.BTDrawerSettings).performClick();
                activity.getSupportFragmentManager().executePendingTransactions();

                RadioGroup rates = activity.findViewById(R.id.RGRefreshRate);
                assertEquals(2, rates.getChildCount());
                ((RadioButton)rates.getChildAt(1)).performClick();
                assertTrue(DD1TouchOverlay.prefersHalfRefreshRate(activity));
                ((RadioButton)rates.getChildAt(0)).performClick();
                assertTrue(!DD1TouchOverlay.prefersHalfRefreshRate(activity));

                ContainerManager manager = new ContainerManager(activity);
                RadioGroup cores = activity.findViewById(R.id.RGCpuBudget);
                if (manager.getContainers().isEmpty()) {
                    assertEquals(0, cores.getChildCount());
                    return;
                }
                assertEquals(3, cores.getChildCount());
                Container container = manager.getContainers().get(0);
                int count = DD1CpuBudget.cores();
                ((RadioButton)cores.getChildAt(DD1CpuBudget.EFFICIENCY)).performClick();
                assertEquals(DD1CpuBudget.list(DD1CpuBudget.EFFICIENCY, count),
                    container.getCPUList());
                ((RadioButton)cores.getChildAt(DD1CpuBudget.ALL)).performClick();
                assertEquals(DD1CpuBudget.list(DD1CpuBudget.ALL, count),
                    container.getCPUList());
            });
        }
    }

    // Somebody here is here because the game will not draw. Every option has to
    // name who it is for, or the list is a guessing game - and picking one has to
    // stick, which the profile correction used to undo on the next launch.
    @Test
    public void theGraphicsDriverCanBeChosenAndSaysWhoEachIsFor() {
        Context context = ApplicationProvider.getApplicationContext();
        DD1GraphicsChoice.store(context, DD1GraphicsChoice.AUTOMATIC);
        try (ActivityScenario<DD1Activity> scenario = ActivityScenario.launch(DD1Activity.class)) {
            scenario.onActivity(activity -> {
                DD1SettingsFragment settings = new DD1SettingsFragment();
                activity.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.FLDD1Container, settings).commitNow();
                activity.getSupportFragmentManager().executePendingTransactions();

                RadioGroup group = activity.findViewById(R.id.RGGraphicsDriver);
                // Automatic plus the pairs, each followed by its own line.
                assertEquals((DD1GraphicsChoice.PAIRS.size() + 1) * 2, group.getChildCount());
                assertTrue("automatic is the first and is selected",
                    ((RadioButton)group.getChildAt(0)).isChecked());
                for (int i = 1; i < group.getChildCount(); i += 2)
                    assertTrue("every option carries a line: " + i,
                        ((TextView)group.getChildAt(i)).getText().length() > 20);

                // Choose the pair for non-Adreno parts.
                int wanted = 2 * (1 + DD1GraphicsChoice.PAIRS.indexOf("vortek,virgl"));
                ((RadioButton)group.getChildAt(wanted)).performClick();

                assertEquals("vortek,virgl", DD1GraphicsChoice.stored(context));
                assertEquals("and it survives being resolved again",
                    "vortek,virgl", DD1GraphicsChoice.resolve(context));
            });
        }
        finally {
            DD1GraphicsChoice.store(context, DD1GraphicsChoice.AUTOMATIC);
        }
    }
}
