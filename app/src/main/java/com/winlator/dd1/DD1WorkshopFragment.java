package com.winlator.dd1;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
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
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.winlator.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DD1WorkshopFragment extends Fragment {
    private static final int[] SORTS = {0, 1, 3, 12, 21};

    private DD1InstallService service;
    private boolean bound;
    private boolean storeTab = true;
    private final ExecutorService images = Executors.newSingleThreadExecutor();
    private final DD1InstallService.WorkshopListener listener = this::renderSnapshot;
    private final ServiceConnection connection = new ServiceConnection();

    private final class ServiceConnection implements android.content.ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((DD1InstallService.LocalBinder)binder).getService();
            bound = true;
            service.observeWorkshop(listener);
            service.refreshWorkshop();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            bound = false;
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
        view.findViewById(R.id.BTWorkshopTabStore).setOnClickListener(v -> showTab(true));
        view.findViewById(R.id.BTWorkshopTabInstalled).setOnClickListener(v -> showTab(false));

        Spinner sort = view.findViewById(R.id.SPWorkshopSort);
        sort.setAdapter(new ArrayAdapter<>(requireContext(),
            android.R.layout.simple_spinner_dropdown_item, new String[] {
                getString(R.string.dd1_workshop_sort_popular),
                getString(R.string.dd1_workshop_sort_newest),
                getString(R.string.dd1_workshop_sort_trending),
                getString(R.string.dd1_workshop_sort_updated),
                getString(R.string.dd1_workshop_sort_rated)
            }));
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
                search(false);
            }
        });
        view.findViewById(R.id.BTWorkshopMore).setOnClickListener(v -> search(true));
        view.findViewById(R.id.BTWorkshopSync).setOnClickListener(v -> {
            if (service != null) service.syncWorkshop();
        });
        showTab(true);
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

    private void showTab(boolean store) {
        storeTab = store;
        View view = getView();
        if (view == null) return;
        view.findViewById(R.id.LLWorkshopStore).setVisibility(store ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.LLWorkshopInstalled).setVisibility(store ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.BTWorkshopTabStore).setSelected(store);
        view.findViewById(R.id.BTWorkshopTabInstalled).setSelected(!store);
        view.findViewById(R.id.BTWorkshopTabStore).setAlpha(store ? 1f : .5f);
        view.findViewById(R.id.BTWorkshopTabInstalled).setAlpha(store ? .5f : 1f);
    }

    void renderSnapshot(DD1WorkshopSnapshot snapshot) {
        View view = getView();
        if (view == null) return;
        renderStore(view, snapshot);
        renderInstalled(view, snapshot);
        showTab(storeTab);
    }

    private void renderStore(View view, DD1WorkshopSnapshot snapshot) {
        view.findViewById(R.id.PBWorkshopLoading).setVisibility(
            snapshot.browseLoading ? View.VISIBLE : View.GONE);
        TextView message = view.findViewById(R.id.TVWorkshopStoreMessage);
        String text = snapshot.browseError;
        if (text == null && snapshot.phase == DD1WorkshopSnapshot.Phase.ERROR)
            text = snapshot.message;
        if (text == null && !snapshot.browseLoading && snapshot.page > 0
                && snapshot.browse.isEmpty()) text = getString(R.string.dd1_workshop_store_empty);
        message.setText(text);
        message.setVisibility(text == null ? View.GONE : View.VISIBLE);

        GridLayout grid = view.findViewById(R.id.GLWorkshopCards);
        grid.removeAllViews();
        int columns = getResources().getConfiguration().screenWidthDp >= 700 ? 2 : 1;
        grid.setColumnCount(columns);
        for (DD1WorkshopSnapshot.Card item : snapshot.browse) {
            View card = LayoutInflater.from(requireContext()).inflate(
                R.layout.dd1_workshop_card, grid, false);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            int margin = Math.round(4 * getResources().getDisplayMetrics().density);
            params.setMargins(margin, 0, margin, 0);
            card.setLayoutParams(params);

            ((TextView)card.findViewById(R.id.TVWorkshopCardTitle)).setText(item.item.title);
            ((TextView)card.findViewById(R.id.TVWorkshopCardMeta)).setText(getString(
                R.string.dd1_workshop_subscribers, item.item.subscriptions,
                formatSize(item.item.fileSize)));
            ((TextView)card.findViewById(R.id.TVWorkshopCardState)).setText(cardState(item));
            ProgressBar progress = card.findViewById(R.id.PBWorkshopCard);
            boolean busy = snapshot.phase == DD1WorkshopSnapshot.Phase.SYNCING
                && item.item.title.equals(snapshot.message);
            progress.setVisibility(busy ? View.VISIBLE : View.GONE);
            progress.setProgress(snapshot.progress);

            Button action = card.findViewById(R.id.BTWorkshopCardAction);
            if (item.updateAvailable) {
                action.setText(R.string.dd1_workshop_update);
                action.setOnClickListener(v -> { if (service != null) service.syncWorkshop(); });
            }
            else if (item.subscribed) {
                action.setText(R.string.dd1_workshop_unsubscribe);
                action.setOnClickListener(v -> confirmUnsubscribe(item.item));
            }
            else {
                action.setText(R.string.dd1_workshop_subscribe);
                action.setOnClickListener(v -> {
                    if (service != null) service.subscribeWorkshop(item.item.publishedFileId);
                });
            }
            action.setEnabled(!busy && item.item.downloadable);
            loadImage(card.findViewById(R.id.IVWorkshopCard), item.item);
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

        TextView message = view.findViewById(R.id.TVWorkshopMessage);
        String text = snapshot.message;
        if (snapshot.phase == DD1WorkshopSnapshot.Phase.READY && snapshot.rows.isEmpty())
            text = getString(R.string.dd1_workshop_empty);
        message.setText(text);
        message.setVisibility(text == null ? View.GONE : View.VISIBLE);

        LinearLayout list = view.findViewById(R.id.LLWorkshopList);
        list.removeAllViews();
        for (DD1WorkshopSnapshot.Row item : snapshot.rows) {
            View row = LayoutInflater.from(requireContext()).inflate(
                R.layout.dd1_workshop_row, list, false);
            ((TextView)row.findViewById(R.id.TVWorkshopTitle)).setText(item.title);
            String source = item.publishedFileId == 0
                ? getString(R.string.dd1_workshop_source_local)
                : getString(R.string.dd1_workshop_source_steam) + " #" + item.publishedFileId;
            ((TextView)row.findViewById(R.id.TVWorkshopMeta)).setText(getString(
                R.string.dd1_workshop_source, source, getString(stateText(item.state))));
            Button enable = row.findViewById(R.id.BTWorkshopEnable);
            enable.setVisibility(item.installed && item.disabled && !syncing
                ? View.VISIBLE : View.GONE);
            enable.setOnClickListener(v -> { if (service != null) service.enableMod(item.directoryName); });
            Button disable = row.findViewById(R.id.BTWorkshopDisable);
            disable.setVisibility(item.installed && !item.disabled && !syncing
                ? View.VISIBLE : View.GONE);
            disable.setOnClickListener(v -> { if (service != null) service.disableMod(item.directoryName); });
            Button update = row.findViewById(R.id.BTWorkshopUpdate);
            update.setVisibility((item.state == DD1WorkshopSnapshot.State.INSTALL
                || item.state == DD1WorkshopSnapshot.State.UPDATE) && !syncing
                ? View.VISIBLE : View.GONE);
            update.setOnClickListener(v -> { if (service != null) service.syncWorkshop(); });
            Button unsubscribe = row.findViewById(R.id.BTWorkshopUnsubscribe);
            boolean subscribed = item.publishedFileId != 0
                && item.state != DD1WorkshopSnapshot.State.ORPHAN;
            unsubscribe.setVisibility(subscribed && !syncing ? View.VISIBLE : View.GONE);
            unsubscribe.setOnClickListener(v -> confirmUnsubscribe(new DD1WorkshopItem(
                item.publishedFileId, item.title, "", "", 0, 0, 0, 0, true)));
            Button delete = row.findViewById(R.id.BTWorkshopDelete);
            delete.setVisibility(item.installed && !subscribed && !syncing
                ? View.VISIBLE : View.GONE);
            delete.setOnClickListener(v -> confirmDelete(item));
            list.addView(row);
        }
    }

    private void loadImage(ImageView image, DD1WorkshopItem item) {
        if (item.previewUrl == null || item.previewUrl.isEmpty()) return;
        image.setTag(item.publishedFileId);
        images.execute(() -> {
            Context context = getContext();
            if (context == null) return;
            Bitmap bitmap = DD1WorkshopImages.fetch(context.getCacheDir(), item.previewUrl);
            if (bitmap == null) return;
            image.post(() -> {
                if (Long.valueOf(item.publishedFileId).equals(image.getTag()))
                    image.setImageBitmap(bitmap);
            });
        });
    }

    private String cardState(DD1WorkshopSnapshot.Card card) {
        if (card.updateAvailable) return getString(R.string.dd1_workshop_state_update);
        if (card.disabled) return getString(R.string.dd1_workshop_disable);
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
        new AlertDialog.Builder(requireContext(), R.style.DD1Dialog)
            .setTitle(R.string.dd1_workshop_unsubscribe_title)
            .setMessage(getString(R.string.dd1_workshop_unsubscribe_message, item.title))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dd1_workshop_unsubscribe, (dialog, which) -> {
                if (service != null) service.unsubscribeWorkshop(item.publishedFileId);
            }).show();
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1024L * 1024) return String.format(java.util.Locale.US, "%.1f MB",
            bytes / (1024f * 1024));
        if (bytes >= 1024) return String.format(java.util.Locale.US, "%.0f KB", bytes / 1024f);
        return bytes + " B";
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
