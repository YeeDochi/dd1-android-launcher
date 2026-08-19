package com.winlator.dd1;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import com.winlator.R;

import java.util.List;

// Lets the owner pick which DLC the launcher installs.
public final class DlcDialog {
    private DlcDialog() {}

    public static void show(Context context, DD1InstallService service) {
        View view = LayoutInflater.from(context).inflate(R.layout.dd1_dlc_dialog, null);
        LinearLayout list = view.findViewById(R.id.LLDlcList);
        DlcSelection selection = service.dlcSelection();
        List<Integer> owned = selection.owned();

        view.findViewById(R.id.TVDlcEmpty).setVisibility(owned.isEmpty() ? View.VISIBLE : View.GONE);
        for (int appId : owned) {
            CheckBox box = new CheckBox(context);
            box.setText(DlcSelection.nameOf(appId));
            box.setChecked(selection.isSelected(appId));
            box.setOnCheckedChangeListener((button, checked) -> selection.setSelected(appId, checked));
            list.addView(box);
        }

        new AlertDialog.Builder(context)
            .setTitle(R.string.dd1_dlc)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok,
                (dialog, which) -> service.saveDlcSelection(selection))
            .show();
    }
}
