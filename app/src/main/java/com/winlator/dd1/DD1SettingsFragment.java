package com.winlator.dd1;

import android.os.Bundle;
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
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;

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
    }

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
        ContainerManager manager = new ContainerManager(requireContext());
        Container container = manager.getContainers().isEmpty()
            ? null : manager.getContainers().get(0);
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

    private RadioButton choice(String label, boolean checked) {
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
