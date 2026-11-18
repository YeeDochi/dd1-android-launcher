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
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.winlator.R;
import com.winlator.box64.Box64Preset;
import com.winlator.container.Container;
import com.winlator.container.GraphicsDrivers;
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
        android.widget.CompoundButton hideButtons = view.findViewById(R.id.CBHideScreenButtons);
        hideButtons.setChecked(DD1TouchOverlay.hidesButtons(requireContext()));
        hideButtons.setOnCheckedChangeListener((button, checked) ->
            DD1TouchOverlay.chooseHiddenButtons(requireContext(), checked));
        buildDiagnosticLogs(view.findViewById(R.id.CBDiagnosticLogs));
        buildCpuBudget(view.findViewById(R.id.RGCpuBudget));
        buildRefreshRate(view.findViewById(R.id.RGRefreshRate));
        buildFrameLimit(view.findViewById(R.id.RGFrameLimit));
        buildGraphicsDriver(view.findViewById(R.id.SPGraphicsDriver));
        buildVulkanDriver(view);
        buildShowFps(view.findViewById(R.id.CBShowFps));
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
    // DD1/mods and DD1/dll. Inside the app the button opens DD1/dll.
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

    // Automatic first, then the pairs. Each one has a line saying who it is
    // for: a person here is here because the game will not draw, and a list of
    // driver names without that line is a guessing game. As radio buttons the
    // six of them filled the tab; a dropdown shows the choice and its line,
    // and the open list shows every line.
    private void buildGraphicsDriver(Spinner spinner) {
        View card = (View)spinner.getParent();
        List<String> options = new ArrayList<>();
        options.add(DD1GraphicsChoice.AUTOMATIC);
        options.addAll(DD1GraphicsChoice.PAIRS);
        String stored = DD1GraphicsChoice.stored(requireContext());
        if (!DD1GraphicsChoice.accepted(stored)) stored = DD1GraphicsChoice.AUTOMATIC;
        // A pair made in Advanced that no preset names: shown as Custom.
        if (!options.contains(stored)) options.add(stored);
        String renderer = GPUHelper.glGetRenderer(requireContext());
        String detected = DD1GraphicsDriver.forRenderer(renderer);
        // The answer first: which GPU, and what it is drawing with now. It is the
        // line to quote in a bug report, and usually all anyone needs to see.
        TextView status = card.findViewById(R.id.TVGraphicsStatus);
        String inUse = DD1GraphicsChoice.resolve(requireContext());
        status.setText(getString(R.string.dd1_graphics_status,
            renderer == null || renderer.isEmpty() ? "?" : renderer,
            DD1GraphicsChoice.PAIRS.contains(inUse) ? getString(labelOf(inUse))
                : getString(R.string.dd1_graphics_custom) + " (" + inUse + ")"));
        TextView hint = card.findViewById(R.id.TVGraphicsDriverChoiceHint);

        float density = getResources().getDisplayMetrics().density;
        int padH = Math.round(12 * density), padV = Math.round(8 * density);
        spinner.setPopupBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
            themeColor(R.attr.colorSecondarySurface)));
        spinner.setOnItemSelectedListener(null);
        spinner.setAdapter(new android.widget.BaseAdapter() {
            @Override public int getCount() { return options.size(); }
            @Override public Object getItem(int position) { return options.get(position); }
            @Override public long getItemId(int position) { return position; }

            // Closed: the name alone, the line sits under the dropdown.
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView text = convertView instanceof TextView ? (TextView)convertView
                    : new TextView(requireContext());
                text.setText(label(options.get(position), detected));
                text.setTextColor(themeColor(R.attr.colorPrimaryText));
                text.setTextSize(15);
                text.setPadding(0, padV, padH, padV);
                return text;
            }

            // Open: the name and who it is for.
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                String option = options.get(position);
                SpannableString text = new SpannableString(
                    label(option, detected) + "\n" + hintOf(option));
                int split = text.toString().indexOf('\n');
                text.setSpan(new ForegroundColorSpan(themeColor(R.attr.colorSecondaryText)),
                    split, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new RelativeSizeSpan(0.8f), split, text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                TextView view = convertView instanceof TextView ? (TextView)convertView
                    : new TextView(requireContext());
                view.setText(text);
                view.setTextColor(themeColor(R.attr.colorPrimaryText));
                view.setTextSize(15);
                view.setPadding(padH, padV, padH, padV);
                return view;
            }
        });
        int selected = options.indexOf(stored);
        spinner.setSelection(selected, false);
        hint.setText(hintOf(stored));
        buildGraphicsAdvanced(card, spinner, inUse, !DD1GraphicsChoice.PAIRS.contains(stored)
            && !stored.equals(DD1GraphicsChoice.AUTOMATIC));
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                chooseGraphics(options.get(position), spinner);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void chooseGraphics(String chosen, Spinner presets) {
        if (chosen.equals(DD1GraphicsChoice.stored(requireContext()))) return;
        DD1GraphicsChoice.store(requireContext(), chosen);
        Container container = firstContainer();
        if (container != null) {
            container.setGraphicsDriver(DD1GraphicsChoice.resolve(requireContext()));
            container.saveData();
        }
        // Redrawn so the status line, the presets and the advanced pair all say
        // what the choice now resolves to.
        buildGraphicsDriver(presets);
        if (getView() != null) buildVulkanDriver(getView());
    }

    private boolean graphicsAdvancedOpen;

    // For people who know the runtime from Winlator: the Vulkan driver and the
    // OpenGL one apart, any of each, and the driver file they brought. Folded
    // away, since a preset is all most people should touch.
    private void buildGraphicsAdvanced(View card, Spinner presets, String inUse, boolean custom) {
        if (custom) graphicsAdvancedOpen = true;
        TextView toggle = card.findViewById(R.id.TVGraphicsAdvanced);
        View section = card.findViewById(R.id.LLGraphicsAdvanced);
        View vulkanCard = getView() != null ? getView().findViewById(R.id.LLVulkanDriverCard) : null;
        Runnable show = () -> {
            toggle.setText(graphicsAdvancedOpen ? R.string.dd1_graphics_advanced_hide
                : R.string.dd1_graphics_advanced_show);
            section.setVisibility(graphicsAdvancedOpen ? View.VISIBLE : View.GONE);
            if (vulkanCard != null)
                vulkanCard.setVisibility(graphicsAdvancedOpen ? View.VISIBLE : View.GONE);
        };
        show.run();
        toggle.setOnClickListener(v -> {
            graphicsAdvancedOpen = !graphicsAdvancedOpen;
            show.run();
        });

        String vulkan = DD1GraphicsChoice.accepted(inUse) ? DD1GraphicsChoice.vulkanOf(inUse)
            : DD1GraphicsChoice.VULKAN.get(0);
        String opengl = DD1GraphicsChoice.accepted(inUse) ? DD1GraphicsChoice.openglOf(inUse)
            : DD1GraphicsChoice.OPENGL.get(0);
        List<CharSequence> vulkanNames = new ArrayList<>();
        for (String id : DD1GraphicsChoice.VULKAN)
            vulkanNames.add(getString(id.equals(GraphicsDrivers.TURNIP)
                ? R.string.dd1_graphics_vulkan_turnip : R.string.dd1_graphics_vulkan_vortek));
        List<CharSequence> openglNames = new ArrayList<>();
        for (String id : DD1GraphicsChoice.OPENGL) openglNames.add(GraphicsDrivers.getName(id));

        Spinner vulkanSpinner = card.findViewById(R.id.SPGraphicsVulkan);
        Spinner openglSpinner = card.findViewById(R.id.SPGraphicsOpenGL);
        fill(vulkanSpinner, vulkanNames, DD1GraphicsChoice.VULKAN.indexOf(vulkan), position ->
            chooseGraphics(DD1GraphicsChoice.pair(DD1GraphicsChoice.VULKAN.get(position), opengl), presets));
        fill(openglSpinner, openglNames, DD1GraphicsChoice.OPENGL.indexOf(opengl), position ->
            chooseGraphics(DD1GraphicsChoice.pair(vulkan, DD1GraphicsChoice.OPENGL.get(position)), presets));
    }

    // A plain dropdown of names in the screen's own colours.
    private void fill(Spinner spinner, List<CharSequence> names, int selected,
            java.util.function.IntConsumer onChoose) {
        float density = getResources().getDisplayMetrics().density;
        int padH = Math.round(12 * density), padV = Math.round(8 * density);
        spinner.setPopupBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
            themeColor(R.attr.colorSecondarySurface)));
        spinner.setOnItemSelectedListener(null);
        spinner.setAdapter(new android.widget.BaseAdapter() {
            @Override public int getCount() { return names.size(); }
            @Override public Object getItem(int position) { return names.get(position); }
            @Override public long getItemId(int position) { return position; }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return row(convertView, position, 0);
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return row(convertView, position, padH);
            }

            private View row(View convertView, int position, int start) {
                TextView text = convertView instanceof TextView ? (TextView)convertView
                    : new TextView(requireContext());
                text.setText(names.get(position));
                text.setTextColor(themeColor(R.attr.colorPrimaryText));
                text.setTextSize(15);
                text.setPadding(start, padV, padH, padV);
                return text;
            }
        });
        spinner.setSelection(Math.max(0, selected), false);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position != selected) onChoose.accept(position);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    // Automatic says what it resolved to, and the option it resolved to says so as
    // well - otherwise the two have to be matched by reading driver names, which is
    // the work this screen exists to save.
    private CharSequence label(String option, String detected) {
        String name = getString(labelOf(option));
        String note = option.equals(DD1GraphicsChoice.AUTOMATIC) ? detected
            : !DD1GraphicsChoice.PAIRS.contains(option) ? option
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
        if (pair.equals("vortek,zink")) return R.string.dd1_graphics_vortek_zink;
        if (pair.equals("vortek,virgl")) return R.string.dd1_graphics_vortek_virgl;
        if (pair.equals("vortek,gladio")) return R.string.dd1_graphics_vortek_gladio;
        return R.string.dd1_graphics_custom;
    }

    private String hintOf(String pair) {
        if (pair.equals(DD1GraphicsChoice.AUTOMATIC))
            return getString(R.string.dd1_graphics_auto_hint);
        if (pair.equals("turnip,gladio"))
            return getString(R.string.dd1_graphics_turnip_gladio_hint);
        if (pair.equals("turnip,zink"))
            return getString(R.string.dd1_graphics_turnip_zink_hint);
        if (pair.equals("vortek,zink"))
            return getString(R.string.dd1_graphics_vortek_zink_hint);
        if (pair.equals("vortek,virgl"))
            return getString(R.string.dd1_graphics_vortek_virgl_hint);
        if (pair.equals("vortek,gladio"))
            return getString(R.string.dd1_graphics_vortek_gladio_hint);
        return getString(R.string.dd1_graphics_custom_hint);
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

    // A driver file the player brings, which Vortek loads in place of the device's
    // own (DD1VulkanDriver). The line above the buttons says which one the
    // profile names and whether the chosen pair uses it at all.
    private final androidx.activity.result.ActivityResultLauncher<String[]> pickVulkanDriver =
        registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri == null || getContext() == null) return;
                Container container = firstContainer();
                if (container == null) return;
                android.content.Context context = requireContext().getApplicationContext();
                new Thread(() -> {
                    String installed = null;
                    String error = null;
                    try {
                        installed = DD1VulkanDriver.install(context, uri);
                    }
                    catch (java.io.IOException e) {
                        error = e.getMessage();
                    }
                    String name = installed;
                    String why = error;
                    android.app.Activity activity = getActivity();
                    if (activity == null) return;
                    activity.runOnUiThread(() -> {
                        if (getView() == null) return;
                        if (name != null) DD1VulkanDriver.choose(container, name);
                        else android.widget.Toast.makeText(context,
                            getString(R.string.dd1_vulkan_driver_failed, why),
                            android.widget.Toast.LENGTH_LONG).show();
                        buildVulkanDriver(getView());
                    });
                }).start();
            });

    private void buildVulkanDriver(View view) {
        TextView current = view.findViewById(R.id.TVVulkanDriver);
        Button pick = view.findViewById(R.id.BTVulkanDriverPick);
        Button reset = view.findViewById(R.id.BTVulkanDriverReset);
        Container container = firstContainer();
        if (container == null) {
            pick.setEnabled(false);
            reset.setEnabled(false);
            return;
        }
        String name = DD1VulkanDriver.chosen(container);
        boolean system = DD1VulkanDriver.SYSTEM.equals(name);
        String shown = system ? getString(R.string.dd1_vulkan_driver_system) : name;
        current.setText(DD1VulkanDriver.appliesTo(container.getGraphicsDriver())
            ? getString(R.string.dd1_vulkan_driver_in_use, shown)
            : getString(R.string.dd1_vulkan_driver_not_used, shown));
        reset.setEnabled(!system);
        pick.setOnClickListener(v -> pickVulkanDriver.launch(new String[]{"*/*"}));
        reset.setOnClickListener(v -> {
            DD1VulkanDriver.choose(container, DD1VulkanDriver.SYSTEM);
            buildVulkanDriver(view);
        });
    }

    // The runtime's own counter: frames per second, with GPU, CPU and memory
    // beside it - a number to put next to "it stutters".
    private void buildShowFps(android.widget.CompoundButton toggle) {
        Container container = firstContainer();
        if (container == null) {
            toggle.setEnabled(false);
            return;
        }
        toggle.setChecked(container.getHUDMode() != com.winlator.widget.FrameRating.Mode.DISABLED.ordinal());
        toggle.setOnCheckedChangeListener((ignored, checked) -> {
            container.setHUDMode((byte)(checked
                ? com.winlator.widget.FrameRating.Mode.FULL.ordinal()
                : com.winlator.widget.FrameRating.Mode.DISABLED.ordinal()));
            container.saveData();
        });
    }

    // The log is written to DD1/logs, so it needs the storage permission; asked
    // for here, where the switch is turned on, rather than at Play, where a
    // refusal would be silent.
    private android.widget.CompoundButton diagnosticToggle;
    private final androidx.activity.result.ActivityResultLauncher<String> storageForLogs =
        registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            granted -> {
                if (getContext() == null) return;
                DD1Diagnostics.setEnabled(requireContext(), granted);
                if (diagnosticToggle != null) diagnosticToggle.setChecked(granted);
            });

    private void buildDiagnosticLogs(android.widget.CompoundButton toggle) {
        diagnosticToggle = toggle;
        toggle.setChecked(DD1Diagnostics.enabled(requireContext()));
        toggle.setOnCheckedChangeListener((ignored, checked) -> {
            if (checked && androidx.core.content.ContextCompat.checkSelfPermission(requireContext(),
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                storageForLogs.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
                return;
            }
            DD1Diagnostics.setEnabled(requireContext(), checked);
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

    // Applied when Play is pressed (DD1GladioClient), so a change waits for the
    // next start.
    private void buildFrameLimit(RadioGroup group) {
        String[] names = getResources().getStringArray(R.array.dd1_frame_limits);
        int chosen = DD1GladioClient.frameLimit(requireContext());
        for (int i = 0; i < names.length && i < DD1GladioClient.FRAME_LIMITS.length; i++)
            group.addView(choice(names[i], DD1GladioClient.FRAME_LIMITS[i] == chosen));
        group.setOnCheckedChangeListener((ignored, checkedId) ->
            DD1GladioClient.chooseFrameLimit(requireContext(),
                DD1GladioClient.FRAME_LIMITS[indexOf(group, checkedId)]));
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
