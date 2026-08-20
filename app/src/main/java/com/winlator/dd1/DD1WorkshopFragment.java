package com.winlator.dd1;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.winlator.R;

public final class DD1WorkshopFragment extends Fragment {
    private DD1InstallService service;
    private boolean bound;
    private final DD1InstallService.WorkshopListener listener = this::renderSnapshot;
    private final ServiceConnection connection = new ServiceConnection() {
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
    };

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
        view.findViewById(R.id.BTWorkshopRefresh).setOnClickListener(v -> {
            if (service != null) service.refreshWorkshop();
        });
        view.findViewById(R.id.BTWorkshopSync).setOnClickListener(v -> {
            if (service != null) service.syncWorkshop();
        });
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

    void renderSnapshot(DD1WorkshopSnapshot snapshot) {
        View view = getView();
        if (view == null) return;
        boolean loading = snapshot.phase == DD1WorkshopSnapshot.Phase.LOADING;
        boolean syncing = snapshot.phase == DD1WorkshopSnapshot.Phase.SYNCING;
        view.findViewById(R.id.PBWorkshopLoading).setVisibility(loading ? View.VISIBLE : View.GONE);

        ProgressBar progress = view.findViewById(R.id.PBWorkshopProgress);
        progress.setVisibility(syncing ? View.VISIBLE : View.GONE);
        progress.setProgress(snapshot.progress);

        Button refresh = view.findViewById(R.id.BTWorkshopRefresh);
        refresh.setEnabled(!loading && !syncing);
        Button sync = view.findViewById(R.id.BTWorkshopSync);
        sync.setVisibility(snapshot.syncable() ? View.VISIBLE : View.GONE);

        TextView message = view.findViewById(R.id.TVWorkshopMessage);
        String text = snapshot.message;
        if (snapshot.phase == DD1WorkshopSnapshot.Phase.READY && snapshot.rows.isEmpty())
            text = getString(R.string.dd1_workshop_empty);
        message.setVisibility(text == null ? View.GONE : View.VISIBLE);
        message.setText(text);

        LinearLayout list = view.findViewById(R.id.LLWorkshopList);
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (DD1WorkshopSnapshot.Row item : snapshot.rows) {
            View row = inflater.inflate(R.layout.dd1_workshop_row, list, false);
            ((TextView)row.findViewById(R.id.TVWorkshopTitle)).setText(item.title);
            String source = item.publishedFileId == 0
                ? getString(R.string.dd1_workshop_source_local)
                : getString(R.string.dd1_workshop_source_steam) + " #" + item.publishedFileId;
            ((TextView)row.findViewById(R.id.TVWorkshopMeta)).setText(getString(
                R.string.dd1_workshop_source, source, getString(stateText(item.state))));
            Button delete = row.findViewById(R.id.BTWorkshopDelete);
            delete.setVisibility(item.installed && !syncing ? View.VISIBLE : View.GONE);
            delete.setOnClickListener(v -> confirmDelete(item));
            list.addView(row);
        }

        TextView log = view.findViewById(R.id.TVWorkshopLog);
        log.setText(android.text.TextUtils.join("\n", snapshot.log));
        ScrollView scroll = view.findViewById(R.id.SVWorkshopLog);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void confirmDelete(DD1WorkshopSnapshot.Row item) {
        new AlertDialog.Builder(requireContext(), R.style.DD1Dialog)
            .setTitle(R.string.dd1_workshop_delete_title)
            .setMessage(getString(R.string.dd1_workshop_delete_message, item.title))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dd1_workshop_delete, (dialog, which) -> {
                if (service != null) service.deleteMod(item.directoryName);
            })
            .show();
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
