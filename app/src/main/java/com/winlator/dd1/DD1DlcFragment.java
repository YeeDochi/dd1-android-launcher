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
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.winlator.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

// Which DLC the launcher installs. A screen rather than a dialog because the
// list is as long as the account is old, and because there is nothing modal
// about it: every tick is saved as it is made.
public class DD1DlcFragment extends Fragment {
    private DD1InstallService installService;
    private boolean serviceBound;
    // Ownership arrives seconds after the service starts, so the list has to be
    // told when it lands: drawn once on binding, it read "no DLC found" for good.
    private final DD1InstallService.Listener installListener = snapshot -> renderList();
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            installService = ((DD1InstallService.LocalBinder)binder).getService();
            serviceBound = true;
            installService.observe(installListener);
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
        return inflater.inflate(R.layout.dd1_dlc_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ((TextView)view.findViewById(R.id.TVScreenTitle)).setText(R.string.dd1_dlc);
        view.findViewById(R.id.BTScreenBack).setOnClickListener(v ->
            requireActivity().getSupportFragmentManager().popBackStack());
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
            installService.removeObserver(installListener);
            requireContext().unbindService(serviceConnection);
            serviceBound = false;
            installService = null;
        }
        super.onStop();
    }

    private void renderList() {
        View view = getView();
        if (view == null || installService == null) return;
        LinearLayout list = view.findViewById(R.id.LLDlcList);
        list.removeAllViews();

        DlcSelection selection = installService.dlcSelection();
        List<Integer> owned = selection.owned();
        view.findViewById(R.id.TVDlcEmpty)
            .setVisibility(owned.isEmpty() ? View.VISIBLE : View.GONE);

        // The same rows the home screen offers on a first install, store artwork
        // and all: the cover is how anyone recognises which DLC this is.
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int appId : owned) {
            View row = inflater.inflate(R.layout.dd1_dlc_item, list, false);
            CheckBox box = row.findViewById(R.id.CBDlc);
            box.setText(DlcSelection.nameOf(appId));
            box.setChecked(selection.isSelected(appId));
            box.setOnCheckedChangeListener((button, checked) -> {
                selection.setSelected(appId, checked);
                installService.saveDlcSelection(selection);
                // What is on disk no longer matches what is ticked, and the offer
                // to act on that has to appear with the tick, not on the next
                // redraw that happens to come along.
                renderPending(view, selection);
            });
            DlcCovers.load(row.findViewById(R.id.IVDlcCover), appId);
            list.addView(row);
        }
        renderPending(view, selection);
    }

    // Ticking a box only records a choice. Acting on it is deliberate: removing
    // content cannot be undone without downloading it again, so it takes a press
    // and a confirmation.
    private void renderPending(View view, DlcSelection selection) {
        File gameDir = new File(requireContext().getFilesDir(), "game");
        List<Integer> installed = DlcInstallFilter.installed(gameDir);

        List<Integer> removable = new ArrayList<>(installed);
        removable.removeAll(selection.selected());

        List<Integer> missing = new ArrayList<>(selection.selected());
        missing.removeAll(installed);

        // Installed is not the same as current: a DLC Steam has since updated is
        // on disk at a version the record remembers, and only the record can tell.
        List<Integer> outdated = new ArrayList<>();
        DD1DepotCatalog catalog = installService.depotCatalog();
        File filesDir = requireContext().getFilesDir();
        DD1DlcVersions.adopt(filesDir, installed, catalog);
        java.util.Map<Integer, String> known = DD1DlcVersions.installed(filesDir);
        for (int appId : installed) {
            if (!selection.isSelected(appId)) continue;
            String offered = catalog.manifestOf(appId);
            if (offered == null) continue;
            if (!offered.equals(known.get(appId))) outdated.add(appId);
        }

        TextView pending = view.findViewById(R.id.TVDlcMissing);
        pending.setVisibility(missing.isEmpty() ? View.GONE : View.VISIBLE);
        if (!missing.isEmpty())
            pending.setText(getString(R.string.dd1_dlc_missing, names(missing)));

        TextView stale = view.findViewById(R.id.TVDlcOutdated);
        stale.setVisibility(outdated.isEmpty() ? View.GONE : View.VISIBLE);
        if (!outdated.isEmpty())
            stale.setText(getString(R.string.dd1_dlc_outdated, names(outdated)));

        List<Integer> fetchable = new ArrayList<>(missing);
        fetchable.addAll(outdated);
        Button fetch = view.findViewById(R.id.BTDlcFetch);
        fetch.setVisibility(fetchable.isEmpty() ? View.GONE : View.VISIBLE);
        fetch.setOnClickListener(v -> {
            installService.downloadDlc(fetchable);
            // The download reports itself on the home screen, which is where the
            // log and the progress bar live.
            requireActivity().getSupportFragmentManager().popBackStack();
        });

        Button apply = view.findViewById(R.id.BTDlcApply);
        apply.setVisibility(removable.isEmpty() ? View.GONE : View.VISIBLE);
        apply.setOnClickListener(v -> confirmRemoval(gameDir, selection, removable));
    }

    private void confirmRemoval(File gameDir, DlcSelection selection, List<Integer> removable) {
        new AlertDialog.Builder(requireContext(), R.style.DD1Dialog)
            .setTitle(R.string.dd1_dlc_apply_title)
            .setMessage(getString(R.string.dd1_dlc_apply_message, names(removable)))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dd1_dlc_apply, (dialog, which) -> {
                DlcInstallFilter.apply(gameDir, selection.selected());
                // Content that is gone has no version, and leaving the line would
                // report an install that is not there.
                DD1DlcVersions.forget(requireContext().getFilesDir(), removable);
                renderList();
            })
            .show();
    }

    private static String names(List<Integer> appIds) {
        StringBuilder text = new StringBuilder();
        for (int appId : appIds) {
            if (text.length() > 0) text.append(", ");
            text.append(DlcSelection.nameOf(appId));
        }
        return text.toString();
    }
}
