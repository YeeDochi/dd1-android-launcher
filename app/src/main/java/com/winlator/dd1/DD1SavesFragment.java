package com.winlator.dd1;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.winlator.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// What each side holds, slot by slot, and a way to move one. Nothing here decides
// anything: it shows the estate, the time played and when it was saved, and the
// player chooses. Both directions are deliberate, and neither happens at Play.
public class DD1SavesFragment extends Fragment {
    private final Handler main = new Handler(Looper.getMainLooper());
    private DD1InstallService installService;
    private boolean serviceBound;
    private DD1CloudListing cloud = DD1CloudListing.unknown();
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            installService = ((DD1InstallService.LocalBinder)binder).getService();
            serviceBound = true;
            loadCloud();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            installService = null;
            serviceBound = false;
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dd1_saves_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ((TextView)view.findViewById(R.id.TVScreenTitle)).setText(R.string.dd1_saves);
        view.findViewById(R.id.BTScreenBack).setOnClickListener(v ->
            requireActivity().getSupportFragmentManager().popBackStack());
        render();
    }

    @Override
    public void onStart() {
        super.onStart();
        requireContext().bindService(new Intent(requireContext(), DD1InstallService.class),
            serviceConnection, Context.BIND_AUTO_CREATE);
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

    // The listing fails until Steam has signed the account in, and that is not the
    // same as the cloud being empty, so it is asked again rather than believed.
    private void loadCloud() {
        View view = getView();
        if (view != null) view.findViewById(R.id.PBSavesLoading).setVisibility(View.VISIBLE);
        new Thread(() -> {
            DD1CloudSaves saves = installService == null ? null : installService.cloudSaves();
            DD1CloudListing listing = DD1CloudListing.unknown();
            for (int i = 0; saves != null && i < 10 && !listing.known(); i++) {
                listing = saves.list();
                if (!listing.known()) {
                    try {
                        Thread.sleep(3000L);
                    }
                    catch (InterruptedException stop) {
                        return;
                    }
                }
            }
            final DD1CloudListing answer = listing;
            main.post(() -> {
                cloud = answer;
                View current = getView();
                if (current != null)
                    current.findViewById(R.id.PBSavesLoading).setVisibility(View.GONE);
                render();
            });
        }).start();
    }

    private void render() {
        View view = getView();
        if (view == null) return;
        File filesDir = requireContext().getFilesDir();

        LinearLayout localList = view.findViewById(R.id.LLLocalSlots);
        localList.removeAllViews();
        List<DD1SaveSlot> local = DD1SaveSlots.local(filesDir);
        if (local.isEmpty()) localList.addView(note(getString(R.string.dd1_saves_none)));
        for (DD1SaveSlot slot : local)
            localList.addView(row(describe(slot), R.string.dd1_saves_upload,
                v -> upload(slot)));

        LinearLayout cloudList = view.findViewById(R.id.LLCloudSlots);
        cloudList.removeAllViews();
        if (!cloud.known())
            cloudList.addView(note(getString(R.string.dd1_saves_cloud_unknown)));
        else {
            List<String> names = DD1SaveSlots.cloudSlotNames(cloud);
            if (names.isEmpty()) cloudList.addView(note(getString(R.string.dd1_saves_none)));
            for (String name : names)
                cloudList.addView(row(name, R.string.dd1_saves_download,
                    v -> download(name)));
        }

        LinearLayout snapshots = view.findViewById(R.id.LLSnapshots);
        snapshots.removeAllViews();
        SimpleDateFormat when = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        for (File snapshot : DD1SaveSnapshots.kept(filesDir)) {
            try {
                snapshots.addView(note(
                    when.format(new Date(Long.parseLong(snapshot.getName())))));
            }
            catch (NumberFormatException notASnapshot) {
                // A directory nobody wrote on purpose is not a snapshot.
            }
        }
    }

    private TextView note(String text) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        return view;
    }

    private View row(String label, int action, View.OnClickListener onClick) {
        View row = LayoutInflater.from(requireContext())
            .inflate(R.layout.dd1_save_slot_row, null, false);
        ((TextView)row.findViewById(R.id.TVSlot)).setText(label);
        Button button = row.findViewById(R.id.BTSlotAction);
        button.setText(action);
        button.setOnClickListener(onClick);
        return row;
    }

    private String describe(DD1SaveSlot slot) {
        StringBuilder text = new StringBuilder(slot.name);
        if (slot.estate != null) text.append(" · ").append(slot.estate);
        if (slot.playedSeconds >= 0) text.append(" · ").append(
            String.format(Locale.US, getString(R.string.dd1_saves_played),
                slot.playedSeconds / 60f));
        if (slot.savedAt != null) text.append(" · ").append(slot.savedAt);
        return text.toString();
    }

    private void status(String text) {
        View view = getView();
        if (view != null) ((TextView)view.findViewById(R.id.TVSavesStatus)).setText(text);
    }

    private void upload(DD1SaveSlot slot) {
        File filesDir = requireContext().getFilesDir();
        File root = DD1Saves.root(filesDir);
        // The summary of one slot names its files relative to the slot, and the
        // cloud holds them under it.
        List<DD1SaveSummary.Entry> named = new ArrayList<>();
        for (DD1SaveSummary.Entry file : DD1SaveSummary.of(new File(root, slot.name)))
            named.add(new DD1SaveSummary.Entry(slot.name + "/" + file.path, file.length,
                file.modifiedMillis, file.sha1));
        status(getString(R.string.dd1_saves_progress, 0, named.size()));

        new Thread(() -> {
            if (DD1SaveSnapshots.take(filesDir, System.currentTimeMillis()) == null) return;
            boolean sent = installService != null
                && installService.cloudSaves().upload(root, named);
            main.post(() -> {
                status(sent ? getString(R.string.dd1_saves_sent, named.size())
                    : getString(R.string.dd1_saves_send_failed));
                loadCloud();
            });
        }).start();
    }

    private void download(String slot) {
        File filesDir = requireContext().getFilesDir();
        List<DD1SaveSummary.Entry> files = DD1SaveSlots.filesOf(cloud, slot);
        status(getString(R.string.dd1_saves_progress, 0, files.size()));

        new Thread(() -> {
            DD1SaveStaging.clear(filesDir, slot);
            int done = 0;
            for (DD1SaveSummary.Entry file : files) {
                byte[] content = installService == null ? null
                    : installService.cloudSaves().fetch(file.path);
                if (content == null || !DD1SaveStaging.put(filesDir, slot, file.path, content))
                    break;
                done++;
                final int sofar = done;
                main.post(() -> status(getString(R.string.dd1_saves_progress,
                    sofar, files.size())));
            }
            final boolean whole = done == files.size() && !files.isEmpty();
            main.post(() -> {
                if (whole) confirmReplace(slot);
                else {
                    DD1SaveStaging.clear(filesDir, slot);
                    status(getString(R.string.dd1_saves_get_failed));
                }
            });
        }).start();
    }

    // The staged slot is described before anything is replaced, so the choice is
    // made against what the cloud actually holds rather than against its name.
    private void confirmReplace(String slot) {
        File filesDir = requireContext().getFilesDir();
        DD1SaveSlot staged = DD1SaveStaging.describe(filesDir, slot);
        if (staged == null) {
            DD1SaveStaging.clear(filesDir, slot);
            status(getString(R.string.dd1_saves_get_failed));
            return;
        }
        DD1SaveSlot live = DD1SaveSlot.of(new File(DD1Saves.root(filesDir), slot));
        new AlertDialog.Builder(requireContext(), R.style.DD1Dialog)
            .setTitle(R.string.dd1_saves_replace_title)
            .setMessage(getString(R.string.dd1_saves_replace,
                live == null ? getString(R.string.dd1_saves_none) : describe(live),
                describe(staged)))
            .setNegativeButton(android.R.string.cancel,
                (dialog, which) -> DD1SaveStaging.clear(filesDir, slot))
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                DD1SaveStaging.apply(filesDir, slot);
                render();
            })
            .show();
    }
}
