package com.winlator.dd1;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.winlator.R;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DD1HomeLayoutTest {
    @Test
    public void aLongStatusNeverPushesThePlayButtonOffScreen() {
        View root = LayoutInflater.from(new ContextThemeWrapper(
                getApplicationContext(), R.style.AppThemeDark))
            .inflate(R.layout.dd1_home_fragment, null);
        TextView status = root.findViewById(R.id.TVStatus);
        View play = root.findViewById(R.id.BTPrimaryAction);

        measure(root, status, "Ready");
        int shortBottom = play.getBottom();

        StringBuilder wall = new StringBuilder();
        for (int index = 0; index < 60; index++) wall.append("Downloading a very long depot name ");
        measure(root, status, wall.toString());

        assertEquals(3, status.getLineCount());
        assertTrue("play button moved with the status text", play.getBottom() <= shortBottom + 1);
    }

    private static void measure(View root, TextView status, String text) {
        status.setText(text);
        int width = View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY);
        int height = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY);
        root.measure(width, height);
        root.layout(0, 0, 2340, 1080);
    }
}
