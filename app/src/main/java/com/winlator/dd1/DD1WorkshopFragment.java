package com.winlator.dd1;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.winlator.R;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.text.DateFormat;
import java.util.Date;

public final class DD1WorkshopFragment extends Fragment {
    private static final int[] SORTS = {0, 1, 3, 12, 21};

    private DD1InstallService service;
    private boolean bound;
    private static final int TAB_STORE = 0, TAB_INSTALLED = 1, TAB_DLL = 2;
    private int tab = TAB_STORE;
    // What the two lists were last built from. A snapshot that only moved a
    // progress figure must not rebuild them: rebuilding drops the scroll position
    // and takes the card out from under a finger that was aiming at it.
    private String builtStore;
    private String builtInstalled;
    AlertDialog detailDialog;
    private final ExecutorService images = Executors.newSingleThreadExecutor();
    // The picker takes the screen, so onStop unbinds and the service is gone by
    // the time the answer comes back: binding again is asynchronous and lands
    // after this callback, so importing straight from here dropped the file on
    // the floor - no mod, no message, every time. Held until there is a service.
    private Uri pendingImport;
    private final ActivityResultLauncher<String[]> importZip = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(), uri -> {
            pendingImport = uri;
            importPending();
        });
    // The drop folder is a public one, so it takes the storage permission the
    // launcher never had a reason to ask for before. Asked for on this screen,
    // which is the only one that reads it.
    private final ActivityResultLauncher<String[]> storage = registerForActivityResult(
        new ActivityResultContracts.RequestMultiplePermissions(), granted -> {
            if (service != null) service.syncImportFolder();
        });
    private final ActivityResultLauncher<Uri> chooseFolder = registerForActivityResult(
        new ActivityResultContracts.OpenDocumentTree(), uri -> {
            pendingFolder = uri;
            importPending();
        });
    private Uri pendingFolder;
    private final DD1InstallService.WorkshopListener listener = this::renderSnapshot;
    private final ServiceConnection connection = new ServiceConnection();

    private final class ServiceConnection implements android.content.ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((DD1InstallService.LocalBinder)binder).getService();
            bound = true;
            service.observeWorkshop(listener);
            service.refreshWorkshop();
            service.syncImportFolder();
            importPending();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            bound = false;
        }
    }

    private void importPending() {
        if (service == null) return;
        if (pendingFolder != null) {
            Uri folder = pendingFolder;
            pendingFolder = null;
            service.setDropFolder(folder);
        }
        if (pendingImport != null) {
            Uri uri = pendingImport;
            pendingImport = null;
            service.importMod(uri);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dd1_workshop_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ((TextView)view.findViewById(R.id.TVScreenTitle)).setText(R.string.dd1_workshop);
        view.findViewById(R.id.BTScreenBack).setOnClickListener(v ->
            requireActivity().getSupportFragmentManager().popBackStack());
        view.findViewById(R.id.BTWorkshopTabStore).setOnClickListener(v -> showTab(TAB_STORE));
        view.findViewById(R.id.BTWorkshopTabInstalled).setOnClickListener(v -> showTab(TAB_INSTALLED));
        view.findViewById(R.id.BTWorkshopTabDll).setOnClickListener(v -> {
            showTab(TAB_DLL);
            loadDll();
        });
        view.findViewById(R.id.BTWorkshopDllOpen).setOnClickListener(v ->
            DD1Overrides.open(requireContext()));
        view.findViewById(R.id.BTWorkshopDllRefresh).setOnClickListener(v -> loadDll());
        ((TextView)view.findViewById(R.id.TVWorkshopDllHint)).setText(R.string.dd1_workshop_dll_hint);

        Spinner sort = view.findViewById(R.id.SPWorkshopSort);
        sort.setAdapter(new ArrayAdapter<>(requireContext(),
            android.R.layout.simple_spinner_dropdown_item, new String[] {
                getString(R.string.dd1_workshop_sort_popular),
                getString(R.string.dd1_workshop_sort_newest),
                getString(R.string.dd1_workshop_sort_trending),
                getString(R.string.dd1_workshop_sort_updated),
                getString(R.string.dd1_workshop_sort_rated)
            }));
        ((GridLayout)view.findViewById(R.id.GLWorkshopCards)).setColumnCount(columnCount());
        EditText search = view.findViewById(R.id.TIWorkshopSearch);
        search.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            search(false);
            return true;
        });
        view.findViewById(R.id.BTWorkshopSearch).setOnClickListener(v -> search(false));
        view.findViewById(R.id.BTWorkshopRefresh).setOnClickListener(v -> {
            if (service != null) {
                service.refreshWorkshop();
                service.syncImportFolder();
                search(false);
            }
        });
        view.findViewById(R.id.BTWorkshopMore).setOnClickListener(v -> search(true));
        view.findViewById(R.id.BTWorkshopSync).setOnClickListener(v -> {
            if (service != null) service.syncWorkshop();
        });
        // Any type: a ZIP reaches a phone as application/zip, x-zip-compressed,
        // octet-stream or nothing at all, depending on what put it there, and a
        // filter that greys the file out reads as the import being broken. What
        // the file actually is, importZip decides by looking for project.xml.
        // The store tab's Refresh is inside the store tab. This list had no way to
        // be asked again, which is where the folder is read.
        view.findViewById(R.id.BTWorkshopInstalledRefresh).setOnClickListener(v -> {
            if (service != null) {
                service.syncImportFolder();
                service.refreshWorkshop();
            }
        });
        // With the game in phone storage its mods folder is the place to put mods,
        // so the row opens it. Otherwise the row is about the folder that is
        // copied in, and changing which one that is.
        view.findViewById(R.id.BTWorkshopDropFolder).setOnClickListener(v -> {
            if (service != null && service.modsInGame()) {
                File mods = service.modsFolder();
                mods.mkdirs();
                DD1FileManager.open(requireContext(), mods);
                return;
            }
            new AlertDialog.Builder(requireContext(), R.style.DD1Dialog)
                .setTitle(R.string.dd1_workshop_folder_change_title)
                .setMessage(R.string.dd1_workshop_folder_change_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.dd1_workshop_folder_change,
                    (dialog, which) -> chooseFolder.launch(null)).show();
        });
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            storage.launch(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE});
        view.findViewById(R.id.BTWorkshopImport).setOnClickListener(v ->
            importZip.launch(new String[] {"*/*"}));
        showTab(TAB_STORE);
        loadDll();
    }

    @Override
    public void onStart() {
        super.onStart();
        requireContext().bindService(new Intent(requireContext(), DD1InstallService.class),
            connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        if (bound) {
            service.removeWorkshopObserver(listener);
            requireContext().unbindService(connection);
            bound = false;
            service = null;
        }
        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (detailDialog != null) detailDialog.dismiss();
        images.shutdownNow();
        super.onDestroy();
    }

    private void search(boolean more) {
        View view = getView();
        if (service == null || view == null) return;
        int position = ((Spinner)view.findViewById(R.id.SPWorkshopSort)).getSelectedItemPosition();
        service.browseWorkshop(((EditText)view.findViewById(R.id.TIWorkshopSearch))
            .getText().toString(), SORTS[Math.max(0, Math.min(SORTS.length - 1, position))], more);
    }

    private void showTab(int shown) {
        tab = shown;
        View view = getView();
        if (view == null) return;
        int[] panes = {R.id.LLWorkshopStore, R.id.LLWorkshopInstalled, R.id.LLWorkshopDll};
        int[] tabs = {R.id.BTWorkshopTabStore, R.id.BTWorkshopTabInstalled, R.id.BTWorkshopTabDll};
        for (int i = 0; i < panes.length; i++) {
            view.findViewById(panes[i]).setVisibility(i == shown ? View.VISIBLE : View.GONE);
            TextView tabView = view.findViewById(tabs[i]);
            tabView.setSelected(i == shown);
            tabView.setTextColor(themeColor(i == shown
                ? R.attr.colorPrimaryText : R.attr.colorSecondaryText));
        }
    }

    // The dll folder, read off the main thread: a folder of DLLs is small, but
    // shared storage answers slowly.
    private void loadDll() {
        android.content.Context context = getContext();
        if (context == null) return;
        android.content.Context app = context.getApplicationContext();
        images.execute(() -> {
            java.util.List<DD1Overrides.Item> items;
            try {
                items = DD1Overrides.list(DD1Overrides.folder(app), DD1GameLocation.gameDir(app));
            }
            catch (Exception error) {
                items = null;
            }
            java.util.List<DD1Overrides.Item> result = items;
            View view = getView();
            if (view != null) view.post(() -> renderDll(result));
        });
    }

    private void renderDll(java.util.List<DD1Overrides.Item> items) {
        View view = getView();
        if (view == null || !isAdded()) return;
        TextView summary = view.findViewById(R.id.TVWorkshopDllSummary);
        TextView tabView = view.findViewById(R.id.BTWorkshopTabDll);
        LinearLayout list = view.findViewById(R.id.LLWorkshopDllList);
        list.removeAllViews();
        if (items == null) {
            summary.setText(R.string.dd1_workshop_dll_no_access);
            tabView.setText(R.string.dd1_workshop_tab_dll);
            return;
        }
        int inUse = 0, waiting = 0, files = 0;
        for (DD1Overrides.Item item : items) {
            if (item.state == DD1Overrides.State.LEAVING) continue;
            files++;
            if (item.state == DD1Overrides.State.APPLIED) inUse++;
            else if (item.state == DD1Overrides.State.PENDING) waiting++;
        }
        tabView.setText(files == 0 ? getString(R.string.dd1_workshop_tab_dll)
            : getString(R.string.dd1_workshop_tab_dll_count, files));
        summary.setText(items.isEmpty() ? getString(R.string.dd1_workshop_dll_empty)
            : getString(R.string.dd1_workshop_dll_summary, inUse, waiting));
        float density = getResources().getDisplayMetrics().density;
        int pad = Math.round(12 * density);
        for (DD1Overrides.Item item : items) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(pad, pad / 2, pad, pad / 2);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = Math.round(6 * density);
            row.setLayoutParams(params);
            row.setBackgroundResource(R.drawable.dd1_card_background);

            TextView name = new TextView(requireContext());
            name.setText(item.path);
            name.setTextColor(themeColor(R.attr.colorPrimaryText));
            name.setTextSize(15);
            row.addView(name);

            StringBuilder line = new StringBuilder(getString(stateText(item.state)));
            if (item.state != DD1Overrides.State.SKIPPED && item.state != DD1Overrides.State.LEAVING) {
                if (item.isDll()) line.append(" · ").append(getString(R.string.dd1_workshop_dll_native));
                if (item.replacesGameFile)
                    line.append(" · ").append(getString(R.string.dd1_workshop_dll_replaces));
            }
            TextView state = new TextView(requireContext());
            state.setText(line);
            state.setTextSize(12);
            state.setTextColor(item.state == DD1Overrides.State.APPLIED
                ? themeColor(R.attr.colorAccent)
                : item.state == DD1Overrides.State.SKIPPED
                ? ContextCompat.getColor(requireContext(), R.color.dd1_warning)
                : themeColor(R.attr.colorSecondaryText));
            row.addView(state);
            list.addView(row);
        }
    }

    private static int stateText(DD1Overrides.State state) {
        switch (state) {
            case APPLIED: return R.string.dd1_workshop_dll_applied;
            case LEAVING: return R.string.dd1_workshop_dll_leaving;
            case SKIPPED: return R.string.dd1_workshop_dll_skipped;
            default: return R.string.dd1_workshop_dll_pending;
        }
    }

    private int themeColor(int attr) {
        android.util.TypedValue value = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(attr, value, true);
        return value.resourceId != 0 ? ContextCompat.getColor(requireContext(), value.resourceId)
            : value.data;
    }

    // A small outlined label: what a mod is, or how it stands. The colour says
    // whether it wants attention.
    private TextView chip(String text, int color) {
        TextView chip = new TextView(requireContext());
        float density = getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable outline =
            new android.graphics.drawable.GradientDrawable();
        outline.setCornerRadius(10 * density);
        outline.setStroke(Math.max(1, Math.round(density)), color);
        chip.setBackground(outline);
        chip.setText(text);
        chip.setTextColor(color);
        chip.setTextSize(11);
        int horizontal = Math.round(8 * density);
        int vertical = Math.round(2 * density);
        chip.setPadding(horizontal, vertical, horizontal, vertical);
        return chip;
    }

    private void addChip(LinearLayout chips, String text, int color) {
        TextView chip = chip(text, color);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(Math.round(6 * getResources().getDisplayMetrics().density));
        chips.addView(chip, params);
    }

    private int stateColor(DD1WorkshopSnapshot.State state) {
        switch (state) {
            case INSTALL:
            case UPDATE: return themeColor(R.attr.colorAccent);
            case ORPHAN:
            case SKIPPED: return ContextCompat.getColor(requireContext(), R.color.dd1_warning);
            default: return themeColor(R.attr.colorSecondaryText);
        }
    }

    void renderSnapshot(DD1WorkshopSnapshot snapshot) {
        View view = getView();
        if (view == null) return;
        renderStore(view, snapshot);
        renderInstalled(view, snapshot);
        showTab(tab);
    }

    private void renderStore(View view, DD1WorkshopSnapshot snapshot) {
        TextView message = view.findViewById(R.id.TVWorkshopStoreMessage);
        String text = snapshot.browseError;
        if (text == null && snapshot.phase == DD1WorkshopSnapshot.Phase.ERROR)
            text = snapshot.message;
        if (text == null && !snapshot.browseLoading && snapshot.page > 0
                && snapshot.browse.isEmpty()) text = getString(R.string.dd1_workshop_store_empty);
        message.setText(text);
        message.setVisibility(text == null ? View.GONE : View.VISIBLE);
        // Nothing to show yet and nothing gone wrong: the first page is on its
        // way, so the screen says so in its middle. With cards already there, a
        // further page or a new search only needs the thin bar along the top.
        boolean firstLoad = snapshot.browse.isEmpty() && text == null
            && (snapshot.browseLoading || snapshot.page == 0);
        view.findViewById(R.id.PBWorkshopStoreSpinner).setVisibility(
            firstLoad ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.PBWorkshopLoading).setVisibility(
            snapshot.browseLoading && !firstLoad ? View.VISIBLE : View.GONE);

        GridLayout grid = view.findViewById(R.id.GLWorkshopCards);
        String shape = storeShape(snapshot);
        if (shape.equals(builtStore) && grid.getChildCount() == snapshot.browse.size()) {
            Button showMore = view.findViewById(R.id.BTWorkshopMore);
            showMore.setVisibility(!snapshot.browseLoading
                && snapshot.browse.size() < snapshot.total ? View.VISIBLE : View.GONE);
            return;
        }
        builtStore = shape;
        grid.removeAllViews();
        grid.setColumnCount(columnCount());
        for (DD1WorkshopSnapshot.Card item : snapshot.browse) {
            View card = LayoutInflater.from(requireContext()).inflate(
                R.layout.dd1_workshop_card, grid, false);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            int margin = Math.round(4 * getResources().getDisplayMetrics().density);
            int verticalMargin = Math.round(6 * getResources().getDisplayMetrics().density);
            params.setMargins(margin, verticalMargin, margin, verticalMargin);
            card.setLayoutParams(params);

            ((TextView)card.findViewById(R.id.TVWorkshopCardTitle)).setText(item.item.title);
            ((TextView)card.findViewById(R.id.TVWorkshopCardMeta)).setText(getString(
                R.string.dd1_workshop_subscribers, item.item.subscriptions,
                formatSize(item.item.fileSize)));
            TextView state = card.findViewById(R.id.TVWorkshopCardState);
            String stateLabel = cardState(item);
            state.setVisibility(stateLabel.isEmpty() ? View.GONE : View.VISIBLE);
            if (!stateLabel.isEmpty()) {
                TextView styled = chip(stateLabel, item.updateAvailable
                    ? themeColor(R.attr.colorAccent) : themeColor(R.attr.colorSecondaryText));
                state.setText(stateLabel);
                state.setTextColor(styled.getTextColors());
                state.setTextSize(11);
                state.setBackground(styled.getBackground());
                state.setPadding(styled.getPaddingLeft(), styled.getPaddingTop(),
                    styled.getPaddingRight(), styled.getPaddingBottom());
            }

            // Subscribe or unsubscribe, and nothing else. What a download is doing
            // belongs on the installed tab: reporting it here meant this list
            // reacted to every chunk, which took the scroll position with it and
            // moved the card out from under the finger aiming at it.
            Button action = card.findViewById(R.id.BTWorkshopCardAction);
            action.setText(item.subscribed ? R.string.dd1_workshop_unsubscribe
                : R.string.dd1_workshop_subscribe);
            action.setEnabled(item.subscribed || item.item.downloadable);
            action.setOnClickListener(item.subscribed
                ? v -> confirmUnsubscribe(item.item)
                : v -> {
                    if (service != null) service.subscribeWorkshop(item.item.publishedFileId);
                });
            loadImage(card.findViewById(R.id.IVWorkshopCard), item.item);
            card.setOnClickListener(v -> showDetail(item));
            grid.addView(card);
        }
        Button more = view.findViewById(R.id.BTWorkshopMore);
        more.setVisibility(!snapshot.browseLoading && snapshot.browse.size() < snapshot.total
            ? View.VISIBLE : View.GONE);
    }

    private void renderInstalled(View view, DD1WorkshopSnapshot snapshot) {
        boolean syncing = snapshot.phase == DD1WorkshopSnapshot.Phase.SYNCING;
        ProgressBar progress = view.findViewById(R.id.PBWorkshopProgress);
        progress.setVisibility(syncing ? View.VISIBLE : View.GONE);
        progress.setProgress(snapshot.progress);
        Button sync = view.findViewById(R.id.BTWorkshopSync);
        sync.setVisibility(snapshot.syncable() ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.BTWorkshopImport).setEnabled(!syncing);
        view.findViewById(R.id.BTWorkshopInstalledRefresh).setEnabled(!syncing);

        int updates = 0;
        for (DD1WorkshopSnapshot.Row row : snapshot.rows)
            if (row.state == DD1WorkshopSnapshot.State.UPDATE
                    || row.state == DD1WorkshopSnapshot.State.INSTALL) updates++;
        boolean loading = snapshot.phase == DD1WorkshopSnapshot.Phase.LOADING
            && snapshot.rows.isEmpty();
        view.findViewById(R.id.PBWorkshopInstalledSpinner).setVisibility(
            loading ? View.VISIBLE : View.GONE);
        ((TextView)view.findViewById(R.id.TVWorkshopSummary)).setText(loading
            ? getString(R.string.dd1_workshop_loading)
            : updates > 0
                ? getString(R.string.dd1_workshop_summary_updates, snapshot.rows.size(), updates)
                : getString(R.string.dd1_workshop_summary, snapshot.rows.size()));
        ((TextView)view.findViewById(R.id.BTWorkshopTabInstalled)).setText(snapshot.rows.isEmpty()
            ? getString(R.string.dd1_workshop_tab_installed)
            : getString(R.string.dd1_workshop_tab_installed_count,
                getString(R.string.dd1_workshop_tab_installed), snapshot.rows.size()));

        TextView folder = view.findViewById(R.id.TVWorkshopDropFolder);
        Button folderAction = view.findViewById(R.id.BTWorkshopDropFolder);
        if (service != null && service.modsInGame()) {
            folder.setText(getString(R.string.dd1_workshop_mods_in_game, service.modsFolder()));
            folderAction.setText(R.string.dd1_workshop_folder_open);
        }
        else if (service != null) {
            folder.setText(getString(R.string.dd1_workshop_drop_folder,
                DD1Folders.base(requireContext()), service.dropFolder()));
            folderAction.setText(R.string.dd1_workshop_folder_change);
        }
        folderAction.setEnabled(!syncing);
        TextView message = view.findViewById(R.id.TVWorkshopMessage);
        String text = snapshot.message;
        if (snapshot.phase == DD1WorkshopSnapshot.Phase.READY && snapshot.rows.isEmpty())
            text = getString(R.string.dd1_workshop_empty);
        message.setText(text);
        message.setVisibility(text == null ? View.GONE : View.VISIBLE);

        LinearLayout list = view.findViewById(R.id.LLWorkshopList);
        LinearLayout local = view.findViewById(R.id.LLWorkshopLocalList);
        // Syncing hides and disables the rows' controls, so it is part of what the
        // rows were built from.
        String shape = syncing + installedShape(snapshot);
        if (shape.equals(builtInstalled)
                && list.getChildCount() + local.getChildCount() == snapshot.rows.size()) return;
        builtInstalled = shape;
        list.removeAllViews();
        local.removeAllViews();
        for (DD1WorkshopSnapshot.Row item : snapshot.rows) {
            View row = LayoutInflater.from(requireContext()).inflate(
                R.layout.dd1_workshop_row, list, false);
            ((TextView)row.findViewById(R.id.TVWorkshopTitle)).setText(item.title);
            LinearLayout chips = row.findViewById(R.id.LLWorkshopChips);
            if (item.publishedFileId != 0)
                addChip(chips, getString(R.string.dd1_workshop_source_steam),
                    themeColor(R.attr.colorSecondaryText));
            // A local mod is local whatever else is true of it; saying so twice
            // is noise.
            if (item.state != DD1WorkshopSnapshot.State.LOCAL || item.publishedFileId != 0)
                addChip(chips, getString(stateText(item.state)), stateColor(item.state));
            else addChip(chips, getString(R.string.dd1_workshop_source_local),
                themeColor(R.attr.colorSecondaryText));
            if (item.installed && item.disabled)
                addChip(chips, getString(R.string.dd1_workshop_state_disabled),
                    themeColor(R.attr.colorSecondaryText));

            Switch enabled = row.findViewById(R.id.SWWorkshopEnabled);
            enabled.setVisibility(item.installed ? View.VISIBLE : View.GONE);
            enabled.setChecked(!item.disabled);
            enabled.setEnabled(!syncing);
            enabled.setOnCheckedChangeListener((button, on) -> {
                if (service == null) return;
                if (on) service.enableMod(item.directoryName);
                else service.disableMod(item.directoryName);
            });

            Button update = row.findViewById(R.id.BTWorkshopUpdate);
            update.setText(item.state == DD1WorkshopSnapshot.State.INSTALL
                ? R.string.dd1_workshop_install : R.string.dd1_workshop_update);
            update.setVisibility((item.state == DD1WorkshopSnapshot.State.INSTALL
                || item.state == DD1WorkshopSnapshot.State.UPDATE) && !syncing
                ? View.VISIBLE : View.GONE);
            update.setOnClickListener(v -> { if (service != null) service.syncWorkshop(); });

            boolean subscribed = item.publishedFileId != 0
                && item.state != DD1WorkshopSnapshot.State.ORPHAN;
            boolean deletable = item.installed && !subscribed;
            View menu = row.findViewById(R.id.BTWorkshopMenu);
            menu.setVisibility((subscribed || deletable) && !syncing ? View.VISIBLE : View.GONE);
            menu.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(requireContext(), v);
                if (subscribed) popup.getMenu().add(0, 1, 0, R.string.dd1_workshop_unsubscribe);
                if (deletable) popup.getMenu().add(0, 2, 0, R.string.dd1_workshop_delete);
                popup.setOnMenuItemClickListener(choice -> {
                    if (choice.getItemId() == 1) confirmUnsubscribe(new DD1WorkshopItem(
                        item.publishedFileId, item.title, "", "", 0, 0, 0, 0, true));
                    else confirmDelete(item);
                    return true;
                });
                popup.show();
            });

            // A mod imported from a ZIP has no Workshop page to show.
            if (item.publishedFileId != 0) row.setOnClickListener(v -> showDetail(item));
            (item.publishedFileId == 0 ? local : list).addView(row);
        }
        // A heading for a section with nothing in it is a claim that something is
        // missing; the two lists are only ever named when they hold something.
        view.findViewById(R.id.TVWorkshopLocalHeading).setVisibility(
            local.getChildCount() == 0 ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.TVWorkshopSteamHeading).setVisibility(
            list.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private void loadImage(ImageView image, DD1WorkshopItem item) {
        loadImage(image, item.previewUrl);
    }

    private void loadImage(ImageView image, String url) {
        if (url == null || url.isEmpty()) return;
        image.setTag(url);
        Bitmap remembered = DD1WorkshopImages.inMemory(url);
        if (remembered != null) {
            image.setImageBitmap(remembered);
            return;
        }
        images.execute(() -> {
            Context context = getContext();
            if (context == null) return;
            Bitmap bitmap = DD1WorkshopImages.fetch(context.getCacheDir(), url);
            if (bitmap == null) return;
            image.post(() -> {
                if (url.equals(image.getTag())) image.setImageBitmap(bitmap);
            });
        });
    }

    void showDetail(DD1WorkshopSnapshot.Card card) {
        showDetail(card.item, card.subscribed);
    }

    // An installed mod is known by its title and its id; everything else in the
    // sheet comes from the same request the store cards make.
    void showDetail(DD1WorkshopSnapshot.Row row) {
        showDetail(new DD1WorkshopItem(row.publishedFileId, row.title, "", "",
            0, 0, 0, 0, true), true);
    }

    private void showDetail(DD1WorkshopItem item, boolean subscribed) {
        View content = LayoutInflater.from(requireContext()).inflate(
            R.layout.dd1_workshop_detail, null, false);
        renderDetail(content, item);
        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.DD1Dialog)
            .setView(content).create();
        detailDialog = dialog;
        content.findViewById(R.id.BTWorkshopDetailWeb).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://steamcommunity.com/sharedfiles/filedetails/?id="
                    + item.publishedFileId));
            try {
                startActivity(intent);
            }
            catch (ActivityNotFoundException e) {
                Toast.makeText(requireContext(), R.string.dd1_workshop_no_browser,
                    Toast.LENGTH_SHORT).show();
            }
        });
        Button action = content.findViewById(R.id.BTWorkshopDetailSubscribe);
        action.setText(subscribed ? R.string.dd1_workshop_unsubscribe
            : R.string.dd1_workshop_subscribe);
        action.setEnabled(service != null && (subscribed || item.downloadable));
        action.setOnClickListener(v -> {
            if (subscribed) confirmUnsubscribe(item, dialog::dismiss);
            else if (service != null) {
                dialog.dismiss();
                service.subscribeWorkshop(item.publishedFileId);
            }
        });
        content.findViewById(R.id.BTWorkshopDetailClose).setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(ignored -> {
            if (detailDialog == dialog) detailDialog = null;
        });
        dialog.setOnShowListener(ignored -> dialog.getWindow().setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dialog.show();

        DD1InstallService current = service;
        if (current == null) {
            content.findViewById(R.id.PBWorkshopDetail).setVisibility(View.GONE);
            return;
        }
        current.workshopDetail(item.publishedFileId).whenComplete((full, error) ->
            content.post(() -> {
                if (detailDialog != dialog || !dialog.isShowing()) return;
                content.findViewById(R.id.PBWorkshopDetail).setVisibility(View.GONE);
                if (full != null) renderDetail(content, full);
            }));
    }

    private void renderDetail(View view, DD1WorkshopItem item) {
        ((TextView)view.findViewById(R.id.TVWorkshopDetailTitle)).setText(item.title);
        TextView meta = view.findViewById(R.id.TVWorkshopDetailMeta);
        // Opened from an installed mod, the counts are not known until Steam
        // answers; zeroes would be a claim rather than a wait.
        boolean counted = item.subscriptions > 0 || item.fileSize > 0;
        meta.setVisibility(counted ? View.VISIBLE : View.GONE);
        if (counted) meta.setText(getString(R.string.dd1_workshop_detail_meta,
            item.subscriptions, formatSize(item.fileSize), item.score * 100));
        TextView updated = view.findViewById(R.id.TVWorkshopDetailUpdated);
        updated.setVisibility(item.updatedAt > 0 ? View.VISIBLE : View.GONE);
        if (item.updatedAt > 0) updated.setText(getString(R.string.dd1_workshop_detail_updated,
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(item.updatedAt * 1000))));
        String description = DD1WorkshopDescription.clean(item.description);
        ((TextView)view.findViewById(R.id.TVWorkshopDetailDescription)).setText(
            description.isEmpty() ? getString(R.string.dd1_workshop_no_description) : description);

        ImageView hero = view.findViewById(R.id.IVWorkshopDetailHero);
        hero.setVisibility(item.previewUrl == null || item.previewUrl.isEmpty()
            ? View.GONE : View.VISIBLE);
        loadImage(hero, item.previewUrl);
        LinearLayout gallery = view.findViewById(R.id.LLWorkshopDetailPictures);
        gallery.removeAllViews();
        int width = Math.round(220 * getResources().getDisplayMetrics().density);
        int height = Math.round(124 * getResources().getDisplayMetrics().density);
        int margin = Math.round(5 * getResources().getDisplayMetrics().density);
        for (int i = 1; i < item.previewUrls.size(); i++) {
            String url = item.previewUrls.get(i);
            ImageView picture = new ImageView(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
            params.setMargins(margin, 0, margin, 0);
            picture.setLayoutParams(params);
            picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
            picture.setBackgroundResource(android.R.color.darker_gray);
            picture.setContentDescription(getString(R.string.dd1_workshop_preview));
            picture.setOnClickListener(v -> loadImage(hero, url));
            loadImage(picture, url);
            gallery.addView(picture);
        }
        view.findViewById(R.id.HSVWorkshopDetailPictures).setVisibility(
            gallery.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    // The list, not its progress: rebuild when this changes and not otherwise.
    private String storeShape(DD1WorkshopSnapshot snapshot) {
        StringBuilder shape = new StringBuilder(columnCount() + "|" + snapshot.page + "|"
            + snapshot.total + "|" + snapshot.query + "|" + snapshot.sort);
        for (DD1WorkshopSnapshot.Card card : snapshot.browse)
            shape.append('|').append(card.item.publishedFileId).append(card.subscribed)
                .append(card.installed).append(card.disabled).append(card.updateAvailable)
                .append(card.item.downloadable).append(card.item.title);
        return shape.toString();
    }

    private String installedShape(DD1WorkshopSnapshot snapshot) {
        StringBuilder shape = new StringBuilder();
        for (DD1WorkshopSnapshot.Row row : snapshot.rows)
            shape.append('|').append(row.directoryName).append(row.publishedFileId)
                .append(row.state).append(row.installed).append(row.disabled)
                .append(row.title);
        return shape.toString();
    }

    private String cardState(DD1WorkshopSnapshot.Card card) {
        if (card.updateAvailable) return getString(R.string.dd1_workshop_state_update);
        if (card.disabled) return getString(R.string.dd1_workshop_state_disabled);
        if (card.installed) return getString(R.string.dd1_workshop_state_current);
        if (card.subscribed) return getString(R.string.dd1_workshop_state_install);
        return "";
    }

    private void confirmDelete(DD1WorkshopSnapshot.Row item) {
        new AlertDialog.Builder(requireContext(), R.style.DD1Dialog)
            .setTitle(R.string.dd1_workshop_delete_title)
            .setMessage(getString(R.string.dd1_workshop_delete_message, item.title))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dd1_workshop_delete, (dialog, which) -> {
                if (service != null) service.deleteMod(item.directoryName);
            }).show();
    }

    private void confirmUnsubscribe(DD1WorkshopItem item) {
        confirmUnsubscribe(item, null);
    }

    private void confirmUnsubscribe(DD1WorkshopItem item, Runnable confirmed) {
        new AlertDialog.Builder(requireContext(), R.style.DD1Dialog)
            .setTitle(R.string.dd1_workshop_unsubscribe_title)
            .setMessage(getString(R.string.dd1_workshop_unsubscribe_message, item.title))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dd1_workshop_unsubscribe, (dialog, which) -> {
                if (confirmed != null) confirmed.run();
                if (service != null) service.unsubscribeWorkshop(item.publishedFileId);
            }).show();
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1024L * 1024) return String.format(java.util.Locale.US, "%.1f MB",
            bytes / (1024f * 1024));
        if (bytes >= 1024) return String.format(java.util.Locale.US, "%.0f KB", bytes / 1024f);
        return bytes + " B";
    }

    // A card needs about 300dp to keep its picture and two lines of title side by
    // side, so the width decides: two on a landscape phone, more on a tablet.
    int columnCount() {
        return columnsFor(getResources().getConfiguration().screenWidthDp);
    }

    static int columnsFor(int widthDp) {
        return Math.max(1, Math.min(4, widthDp / 300));
    }

    private static int stateText(DD1WorkshopSnapshot.State state) {
        switch (state) {
            case INSTALL: return R.string.dd1_workshop_state_install;
            case UPDATE: return R.string.dd1_workshop_state_update;
            case CURRENT: return R.string.dd1_workshop_state_current;
            case ORPHAN: return R.string.dd1_workshop_state_orphan;
            case LOCAL: return R.string.dd1_workshop_state_local;
            default: return R.string.dd1_workshop_state_skipped;
        }
    }
}
