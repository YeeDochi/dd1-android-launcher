package com.winlator.dd1;

import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.winlator.R;
import com.winlator.box64.Box64Preset;
import com.winlator.container.Container;
import com.winlator.core.GPUHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Language and the size the game draws at. Both are read back from where they
// actually live rather than mirrored in the launcher's own preferences.
public class DD1SettingsFragment extends Fragment {
    // The panel is 1080 tall, so 1920x1080 is 1:1 and the sharpest. Smaller is
    // the same interface at fewer pixels, not a bigger one; 2340x1080 fills the
    // screen and takes the bars the Esc button sits in.
    private static final List<String> RESOLUTIONS =
        Arrays.asList("1280x720", "1600x900", "1920x1080", "2340x1080");

    private static final List<String> LANGUAGE_TAGS =
        Arrays.asList(DD1Locale.SYSTEM, "en", "ko");

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dd1_settings_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ((TextView)view.findViewById(R.id.TVScreenTitle)).setText(R.string.dd1_settings);
        view.findViewById(R.id.BTScreenBack).setOnClickListener(v ->
            requireActivity().getSupportFragmentManager().popBackStack());
        buildLanguage(view.findViewById(R.id.RGLanguage));
        buildResolution(view.findViewById(R.id.RGResolution),
            view.findViewById(R.id.TVResolutionHint));
        buildBox64Preset(view.findViewById(R.id.CBBox64Performance));
        buildCpuBudget(view.findViewById(R.id.RGCpuBudget));
        buildRefreshRate(view.findViewById(R.id.RGRefreshRate));
        buildGraphicsDriver(view.findViewById(R.id.RGGraphicsDriver));
    }

    // Automatic first, then the pairs, each with a line saying who it is for. A
    // person here is here because the game will not draw, and a list of driver
    // names without that line is a guessing game.
    private void buildGraphicsDriver(RadioGroup group) {
        group.setOnCheckedChangeListener(null);
        group.removeAllViews();
        List<String> options = new ArrayList<>();
        options.add(DD1GraphicsChoice.AUTOMATIC);
        options.addAll(DD1GraphicsChoice.PAIRS);
        String stored = DD1GraphicsChoice.stored(requireContext());
        if (!options.contains(stored)) stored = DD1GraphicsChoice.AUTOMATIC;
        String detected = DD1GraphicsDriver.forRenderer(
            GPUHelper.glGetRenderer(requireContext()));
        for (String option : options) {
            RadioButton button = choice(label(option, detected), option.equals(stored));
            group.addView(button);
            TextView hint = new TextView(requireContext());
            hint.setText(option.equals(DD1GraphicsChoice.AUTOMATIC)
                ? autoHint() : hintOf(option));
            hint.setTextColor(getResources().getColor(
                android.R.color.darker_gray, requireContext().getTheme()));
            hint.setTextSize(12);
            // Closer to the button it belongs to than to the next one, or it reads
            // as the beginning of the option below.
            int gap = Math.round(8 * getResources().getDisplayMetrics().density);
            RadioGroup.LayoutParams params = new RadioGroup.LayoutParams(
                RadioGroup.LayoutParams.MATCH_PARENT, RadioGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = gap;
            params.leftMargin = Math.round(34 * getResources().getDisplayMetrics().density);
            hint.setLayoutParams(params);
            group.addView(hint);
        }
        group.setOnCheckedChangeListener((ignored, checkedId) -> {
            // Two views per option: the button, then its line.
            int index = indexOf(group, checkedId) / 2;
            String chosen = index >= 0 && index < options.size()
                ? options.get(index) : DD1GraphicsChoice.AUTOMATIC;
            DD1GraphicsChoice.store(requireContext(), chosen);
            Container container = firstContainer();
            if (container != null) {
                container.setGraphicsDriver(DD1GraphicsChoice.resolve(requireContext()));
                container.saveData();
            }
            // The list redraws so the automatic row can say what it now resolves to.
            buildGraphicsDriver(group);
        });
    }

    // Automatic says what it resolved to, and the option it resolved to says so as
    // well - otherwise the two have to be matched by reading driver names, which is
    // the work this screen exists to save.
    private CharSequence label(String option, String detected) {
        String name = getString(labelOf(option));
        String note = option.equals(DD1GraphicsChoice.AUTOMATIC) ? detected
            : option.equals(detected) ? getString(R.string.dd1_graphics_same_as_auto) : null;
        if (note == null) return name;
        SpannableString text = new SpannableString(name + "   " + note);
        text.setSpan(new ForegroundColorSpan(0x99AAAAAA), name.length(), text.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new RelativeSizeSpan(0.85f), name.length(), text.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    // The GPU this device reported, above the explanation, because it is what the
    // automatic answer was read from - and the line to quote in a bug report.
    private CharSequence autoHint() {
        String renderer = GPUHelper.glGetRenderer(requireContext());
        String explanation = getString(R.string.dd1_graphics_auto_hint);
        if (renderer == null || renderer.isEmpty()) return explanation;
        SpannableString text = new SpannableString(renderer + "\n" + explanation);
        text.setSpan(new ForegroundColorSpan(0xFFBBBBBB), 0, renderer.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    private int labelOf(String pair) {
        if (pair.equals(DD1GraphicsChoice.AUTOMATIC)) return R.string.dd1_graphics_auto;
        if (pair.equals("turnip,gladio")) return R.string.dd1_graphics_turnip_gladio;
        if (pair.equals("turnip,zink")) return R.string.dd1_graphics_turnip_zink;
        if (pair.equals("vortek,virgl")) return R.string.dd1_graphics_vortek_virgl;
        return R.string.dd1_graphics_vortek_gladio;
    }

    private String hintOf(String pair) {
        if (pair.equals(DD1GraphicsChoice.AUTOMATIC))
            return getString(R.string.dd1_graphics_auto_hint);
        if (pair.equals("turnip,gladio"))
            return getString(R.string.dd1_graphics_turnip_gladio_hint);
        if (pair.equals("turnip,zink"))
            return getString(R.string.dd1_graphics_turnip_zink_hint);
        if (pair.equals("vortek,virgl"))
            return getString(R.string.dd1_graphics_vortek_virgl_hint);
        return getString(R.string.dd1_graphics_vortek_gladio_hint);
    }

    // Which GPU this is and which drivers the profile ended up on. A screen that
    // will not draw is decided here, and on a device nobody debugging it can
    // reach, this line is the whole story.
    private void buildLanguage(RadioGroup group) {
        String[] names = getResources().getStringArray(R.array.dd1_languages);
        String chosen = DD1Locale.chosen(requireContext());
        for (int i = 0; i < LANGUAGE_TAGS.size() && i < names.length; i++) {
            group.addView(choice(names[i], LANGUAGE_TAGS.get(i).equals(chosen)));
        }
        group.setOnCheckedChangeListener((ignored, checkedId) -> {
            String tag = LANGUAGE_TAGS.get(indexOf(group, checkedId));
            if (tag.equals(DD1Locale.chosen(requireContext()))) return;
            DD1Locale.choose(requireContext(), tag);
            requireActivity().recreate();
        });
    }

    private void buildResolution(RadioGroup group, TextView hint) {
        Container container = firstContainer();
        if (container == null) {
            hint.setText(R.string.dd1_resolution_unavailable);
            return;
        }

        String current = container.getScreenSize();
        for (String size : RESOLUTIONS) group.addView(choice(size, size.equals(current)));
        // A size the profile chose that is not on the list still deserves showing,
        // or the screen would claim none of them is in use.
        if (!RESOLUTIONS.contains(current)) group.addView(choice(current, true));

        group.setOnCheckedChangeListener((ignored, checkedId) -> {
            RadioButton button = group.findViewById(checkedId);
            if (button == null) return;
            String chosen = button.getText().toString();
            if (chosen.equals(container.getScreenSize())) return;
            container.setScreenSize(chosen);
            container.saveData();
        });
    }

    // Lowering the resolution cost 9% of the battery and 24C off the hottest core,
    // which says the draw is in translating instructions rather than drawing
    // pixels. This is the only knob on that side, and it trades safety for it, so
    // it is off by default and says what it risks.
    private void buildBox64Preset(android.widget.CheckBox toggle) {
        Container container = firstContainer();
        if (container == null) {
            toggle.setEnabled(false);
            return;
        }
        toggle.setChecked(Box64Preset.PERFORMANCE.equals(container.getBox64Preset()));
        toggle.setOnCheckedChangeListener((ignored, checked) -> {
            container.setBox64Preset(checked ? Box64Preset.PERFORMANCE : Box64Preset.DEFAULT);
            container.saveData();
        });
    }

    private void buildCpuBudget(RadioGroup group) {
        Container container = firstContainer();
        if (container == null) return;
        int cores = DD1CpuBudget.cores();
        String[] names = getResources().getStringArray(R.array.dd1_cpu_budgets);
        int chosen = DD1CpuBudget.of(container.getCPUList(), cores);
        for (int i = 0; i < names.length; i++) group.addView(choice(names[i], i == chosen));
        group.setOnCheckedChangeListener((ignored, checkedId) -> {
            String list = DD1CpuBudget.list(indexOf(group, checkedId), cores);
            container.setCPUList(list);
            container.setCPUListWoW64(list);
            container.saveData();
        });
    }

    // The runtime's activity owns the window, so the rate is asked for from the
    // overlay the launcher already attaches to it rather than by editing it.
    private void buildRefreshRate(RadioGroup group) {
        String[] names = getResources().getStringArray(R.array.dd1_refresh_rates);
        boolean halved = DD1TouchOverlay.prefersHalfRefreshRate(requireContext());
        for (int i = 0; i < names.length; i++)
            group.addView(choice(names[i], (i == 1) == halved));
        group.setOnCheckedChangeListener((ignored, checkedId) ->
            DD1TouchOverlay.chooseHalfRefreshRate(requireContext(),
                indexOf(group, checkedId) == 1));
    }

    private Container firstContainer() {
        return DD1Profiles.first(requireContext());
    }

    private RadioButton choice(CharSequence label, boolean checked) {
        RadioButton button = new RadioButton(requireContext());
        button.setId(View.generateViewId());
        button.setText(label);
        button.setChecked(checked);
        return button;
    }

    private int indexOf(RadioGroup group, int checkedId) {
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i).getId() == checkedId) return i;
        }
        return 0;
    }
}
