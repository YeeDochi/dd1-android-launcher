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
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.winlator.R;

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
            });
            DlcCovers.load(row.findViewById(R.id.IVDlcCover), appId);
            list.addView(row);
        }
    }
}
