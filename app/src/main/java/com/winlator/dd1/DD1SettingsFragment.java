package com.winlator.dd1;

import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
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
    private DD1InstallService installService;
    private boolean serviceBound;
    private String pendingLocation;
    private final android.content.ServiceConnection serviceConnection =
        new android.content.ServiceConnection() {
            @Override
            public void onServiceConnected(android.content.ComponentName name,
                    android.os.IBinder binder) {
                installService = ((DD1InstallService.LocalBinder)binder).getService();
                serviceBound = true;
            }

            @Override
            public void onServiceDisconnected(android.content.ComponentName name) {
                installService = null;
                serviceBound = false;
            }
        };
    // Phone storage takes the storage permission, asked for at the choice that
    // needs it; the move carries on once it is granted.
    private final androidx.activity.result.ActivityResultLauncher<String[]> storage =
        registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
            granted -> {
                if (Boolean.TRUE.equals(granted.get(
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE))) {
                    if (pendingLocation == null) DD1Overrides.open(requireContext());
                    else confirmMove(pendingLocation);
                }
                else android.widget.Toast.makeText(requireContext(), pendingLocation == null
                    ? R.string.dd1_storage_needed_overrides : R.string.dd1_storage_needed,
                    android.widget.Toast.LENGTH_LONG).show();
            });
    private int section;

    @Override
    public void onStart() {
        super.onStart();
        requireContext().bindService(new android.content.Intent(requireContext(),
            DD1InstallService.class), serviceConnection, android.content.Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        if (serviceBound) {
            requireContext().unbindService(serviceConnection);
            serviceBound = false;
            installService = null;
        }
        super.onStop();
    }
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
        buildRuntimeMenu(view.findViewById(R.id.CBRuntimeMenu));
        buildCpuBudget(view.findViewById(R.id.RGCpuBudget));
        buildRefreshRate(view.findViewById(R.id.RGRefreshRate));
        buildGraphicsDriver(view.findViewById(R.id.RGGraphicsDriver));
        buildGameLocation(view);
        if (savedInstanceState != null) section = savedInstanceState.getInt("section", 0);
        LinearLayout tabs = view.findViewById(R.id.LLSettingsTabs);
        for (int i = 0; i < tabs.getChildCount(); i++) {
            int index = i;
            tabs.getChildAt(i).setOnClickListener(v -> showSection(index));
        }
        showSection(section);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("section", section);
    }

    private void showSection(int index) {
        View view = getView();
        if (view == null) return;
        section = index;
        LinearLayout tabs = view.findViewById(R.id.LLSettingsTabs);
        ViewGroup sections = view.findViewById(R.id.FLSettingsSections);
        for (int i = 0; i < sections.getChildCount(); i++) {
            boolean shown = i == index;
            sections.getChildAt(i).setVisibility(shown ? View.VISIBLE : View.GONE);
            TextView tab = (TextView)tabs.getChildAt(i);
            tab.setSelected(shown);
            tab.setTextColor(themeColor(shown ? R.attr.colorPrimaryText : R.attr.colorSecondaryText));
        }
        view.findViewById(R.id.SVSettings).scrollTo(0, 0);
    }

    private int themeColor(int attr) {
        android.util.TypedValue value = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(attr, value, true);
        return value.resourceId != 0
            ? androidx.core.content.ContextCompat.getColor(requireContext(), value.resourceId)
            : value.data;
    }

    // Where the game is, and the one thing to do about it. There is one
    // direction only: in phone storage the game took minutes per scene change
    // (issue #4), and what people kept it there for - mods and DLLs - now goes in
    // DD1/mods and DD1/overrides. Inside the app the button opens DD1/overrides.
    private void buildGameLocation(View view) {
        boolean inStorage = DD1GameLocation.STORAGE.equals(DD1GameLocation.chosen(requireContext()));
        ((TextView)view.findViewById(R.id.TVGameLocation)).setText(inStorage
            ? getString(R.string.dd1_game_location_here_storage,
                DD1GameLocation.gameDir(requireContext()).getPath())
            : getString(R.string.dd1_game_location_here_app));
        Button move = view.findViewById(R.id.BTGameLocationMove);
        move.setVisibility(inStorage ? View.VISIBLE : View.GONE);
        move.setText(R.string.dd1_game_location_to_app);
        move.setOnClickListener(v -> requestMove(DD1GameLocation.APP));
        Button open = view.findViewById(R.id.BTGameLocationOpen);
        open.setVisibility(View.VISIBLE);
        if (inStorage) {
            open.setText(R.string.dd1_workshop_folder_open);
            open.setOnClickListener(v -> DD1FileManager.open(requireContext(),
                DD1GameLocation.gameDir(requireContext())));
        }
        else {
            open.setText(R.string.dd1_game_files_overrides);
            open.setOnClickListener(v -> requestMove(null));
        }
    }


    // Either way the move reads or writes phone storage, so the permission comes
    // first; the move carries on once it is granted.
    private void requestMove(String location) {
        pendingLocation = location;
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            storage.launch(new String[] {android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE});
            return;
        }
        if (location == null) DD1Overrides.open(requireContext());
        else confirmMove(location);
    }

    private void confirmMove(String location) {
        String where = DD1GameLocation.STORAGE.equals(location)
            ? DD1GameLocation.storageRoot().getPath() + "/game"
            : getString(R.string.dd1_game_location_app);
        new androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.DD1Dialog)
            .setTitle(R.string.dd1_game_location_move_title)
            .setMessage(getString(R.string.dd1_game_location_move_message, where))
            .setPositiveButton(R.string.dd1_game_location_move, (dialog, which) -> {
                if (installService == null || !installService.moveGame(location)) {
                    android.widget.Toast.makeText(requireContext(),
                        R.string.dd1_game_location_busy, android.widget.Toast.LENGTH_LONG).show();
                    return;
                }
                // The move reports on the home screen, where the download does.
                requireActivity().getSupportFragmentManager().popBackStack();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
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
        String renderer = GPUHelper.glGetRenderer(requireContext());
        String detected = DD1GraphicsDriver.forRenderer(renderer);
        // The answer first: which GPU, and what it is drawing with now. It is the
        // line to quote in a bug report, and usually all anyone needs to see.
        TextView status = ((View)group.getParent()).findViewById(R.id.TVGraphicsStatus);
        String inUse = DD1GraphicsChoice.resolve(requireContext());
        status.setText(getString(R.string.dd1_graphics_status,
            renderer == null || renderer.isEmpty() ? "?" : renderer,
            options.contains(inUse) ? getString(labelOf(inUse)) : inUse));
        for (String option : options) {
            RadioButton button = choice(label(option, detected), option.equals(stored));
            group.addView(button);
            TextView hint = new TextView(requireContext());
            hint.setText(hintOf(option));
            hint.setTextColor(themeColor(R.attr.colorSecondaryText));
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
        text.setSpan(new ForegroundColorSpan(themeColor(R.attr.colorSecondaryText)),
            name.length(), text.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new RelativeSizeSpan(0.85f), name.length(), text.length(),
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
    private void buildBox64Preset(android.widget.CompoundButton toggle) {
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

    private void buildRuntimeMenu(android.widget.CompoundButton toggle) {
        toggle.setChecked(DD1RuntimeMenu.enabled(requireContext()));
        toggle.setOnCheckedChangeListener((ignored, checked) ->
            DD1RuntimeMenu.setEnabled(requireContext(), checked));
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
        // Most groups lie in a row now; the gap keeps one choice from running
        // into the next.
        RadioGroup.LayoutParams params = new RadioGroup.LayoutParams(
            RadioGroup.LayoutParams.WRAP_CONTENT, RadioGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(Math.round(20 * getResources().getDisplayMetrics().density));
        button.setLayoutParams(params);
        return button;
    }

    private int indexOf(RadioGroup group, int checkedId) {
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i).getId() == checkedId) return i;
        }
        return 0;
    }
}
