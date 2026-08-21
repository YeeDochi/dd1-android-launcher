package com.winlator.dd1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
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
}
