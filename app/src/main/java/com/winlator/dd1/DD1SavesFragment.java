package com.winlator.dd1;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
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
    // One transfer at a time: a second press while a slot is moving would race
    // the first over the same files and the same cloud batch.
    private boolean busy;
    // The transfer log, kept off the screen and shown on request.
    private final StringBuilder logText = new StringBuilder();
    // One line per finished transfer, newest first: what the record shows unless
    // the details are asked for.
    private final List<String> history = new ArrayList<>();
    private TextView openLog;
    // The save shown on the left, by slot folder; the cloud's own description of
    // each slot, read once per listing.
    private String selected;
    private boolean snapshotsTab;
    private final java.util.Map<String, DD1SaveSlot> cloudSlots = new java.util.HashMap<>();
    private final java.util.Set<String> describing = new java.util.HashSet<>();
    private static final int GREEN = 0xff43a047, ORANGE = 0xfffb8c00,
        YELLOW = 0xfffdd835, RED = 0xffe53935;
    // The backup folder is a public one, so it takes the storage permission the
    // way the workshop's drop folder does. Asked for at the press that needs it,
    // and the press finishes when it is granted.
    private final ActivityResultLauncher<String[]> storage = registerForActivityResult(
        new ActivityResultContracts.RequestMultiplePermissions(), granted -> {
            if (Boolean.TRUE.equals(granted.get(Manifest.permission.WRITE_EXTERNAL_STORAGE)))
                backup();
            else log(getString(R.string.dd1_saves_backup_denied));
        });
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            installService = ((DD1InstallService.LocalBinder)binder).getService();
            serviceBound = true;
            // What was read at sign-in, shown at once; then asked again.
            DD1CloudListing cached = installService.cachedCloudListing();
            if (cached.known() && !cloud.known()) {
                cloud = cached;
                cloudSlots.putAll(installService.cachedCloudSlots());
                render();
            }
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
        view.findViewById(R.id.BTSavesLog).setOnClickListener(v -> showLog());
        view.findViewById(R.id.BTSavesTabManage).setOnClickListener(v -> {
            snapshotsTab = false;
            render();
        });
        view.findViewById(R.id.BTSavesTabSnapshots).setOnClickListener(v -> {
            snapshotsTab = true;
            render();
        });
        view.findViewById(R.id.BTSavesRefresh).setOnClickListener(v -> {
            if (refuseWhileBusy()) return;
            cloudSlots.clear();
            loadCloud();
        });
        view.findViewById(R.id.BTSaveBackup).setOnClickListener(v -> {
            if (refuseWhileBusy()) return;
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) backup();
            else storage.launch(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE});
        });
        render();
    }

    // Where the keepsake goes: a folder a file manager can open, named by the
    // moment it was taken, beside the mods folder the workshop screen already
    // keeps out there. Nothing prunes it - it stays until the owner deletes it,
    // which is the point: the snapshot ring dies with the app, this does not.
    static File backupFolder(long atMillis) {
        return new File(android.os.Environment.getExternalStorageDirectory(),
            "DD1/saves/" + DD1SaveSnapshots.stamp(atMillis));
    }

    // Where the automatic mirror lives: its own folder under the manual backups,
    // because the mirror prunes itself and must never prune a backup the owner
    // took on purpose.
    static File autoMirrorFolder() {
        return new File(android.os.Environment.getExternalStorageDirectory(),
            "DD1/saves/auto");
    }

    private void backup() {
        if (refuseWhileBusy()) return;
        File filesDir = requireContext().getFilesDir();
        File destination = backupFolder(System.currentTimeMillis());
        setBusy(true);
        status(getString(R.string.dd1_saves_status_backup), 0, 0);
        new Thread(() -> {
            boolean done = DD1SaveSnapshots.export(filesDir, destination);
            main.post(() -> {
                String outcome = done
                    ? getString(R.string.dd1_saves_backup_done, destination.getPath())
                    : getString(R.string.dd1_saves_backup_failed);
                log(outcome);
                finish(outcome);
                DD1Notices.show(outcome, done ? DD1Notices.Kind.SUCCESS : DD1Notices.Kind.WARNING);
            });
        }).start();
    }

    private boolean refuseWhileBusy() {
        if (!busy) return false;
        DD1Notices.show(requireContext(), R.string.dd1_saves_busy, DD1Notices.Kind.WARNING);
        return true;
    }

    private void setBusy(boolean value) {
        busy = value;
        View view = getView();
        if (view == null) return;
        float alpha = value ? 0.45f : 1f;
        view.findViewById(R.id.LLSaveList).setAlpha(alpha);
        view.findViewById(R.id.LLSnapshots).setAlpha(alpha);
        view.findViewById(R.id.BTSaveBackup).setEnabled(!value);
        view.findViewById(R.id.BTSavesRefresh).setEnabled(!value);
        if (value) {
            view.findViewById(R.id.BTSaveUpload).setEnabled(false);
            view.findViewById(R.id.BTSaveDownload).setEnabled(false);
            view.findViewById(R.id.BTSaveDelete).setEnabled(false);
        }
        else render();
    }

    // The line across the top: what is moving and how far it has got. A total of
    // zero is a step with no count, shown as a running bar.
    private void status(String text, int done, int total) {
        View view = getView();
        if (view == null) return;
        view.findViewById(R.id.LLSavesStatus).setVisibility(View.VISIBLE);
        ((TextView)view.findViewById(R.id.TVSavesStatus)).setText(text);
        ProgressBar bar = view.findViewById(R.id.PBSavesTransfer);
        bar.setVisibility(View.VISIBLE);
        bar.setIndeterminate(total <= 0);
        if (total > 0) bar.setProgress(done * 100 / total);
    }

    // Ends a transfer: the outcome stays on the line, the bar goes.
    private void finish(String outcome) {
        history.add(0, new SimpleDateFormat("HH:mm", Locale.US).format(new Date()) + "  " + outcome);
        lastOutcome = outcome;
        lastOutcomeFor = selected;
        setBusy(false);
        View view = getView();
        if (view == null) return;
        ((TextView)view.findViewById(R.id.TVSavesStatus)).setText(outcome);
        view.findViewById(R.id.PBSavesTransfer).setVisibility(View.INVISIBLE);
    }

    // The line under the title: the outcome of what was just done to this save,
    // until another is chosen; otherwise what to do about it.
    private String lastOutcome, lastOutcomeFor;

    // The outcomes first; every file and every answer only behind Details.
    private void showLog() {
        android.widget.ScrollView scroll = new android.widget.ScrollView(requireContext());
        TextView text = new TextView(requireContext());
        int pad = Math.round(12 * getResources().getDisplayMetrics().density);
        text.setPadding(pad, pad, pad, pad);
        text.setTextIsSelectable(true);
        text.setTextSize(14);
        text.setTextColor(themeColor(R.attr.colorPrimaryText));
        text.setText(history.isEmpty() ? getString(R.string.dd1_saves_history_empty)
            : android.text.TextUtils.join("\n", history));
        scroll.addView(text);
        boolean[] details = {false};
        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.DD1Dialog)
            .setTitle(R.string.dd1_saves_log_title)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.dd1_saves_log_details, null)
            .setOnDismissListener(d -> openLog = null)
            .show();
        android.widget.Button toggle = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        toggle.setOnClickListener(v -> {
            details[0] = !details[0];
            if (details[0]) {
                text.setTypeface(android.graphics.Typeface.MONOSPACE);
                text.setTextSize(12);
                text.setText(logText);
                openLog = text;
                toggle.setText(R.string.dd1_saves_log_summary);
                scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            }
            else {
                text.setTypeface(android.graphics.Typeface.DEFAULT);
                text.setTextSize(14);
                text.setText(history.isEmpty() ? getString(R.string.dd1_saves_history_empty)
                    : android.text.TextUtils.join("\n", history));
                openLog = null;
                toggle.setText(R.string.dd1_saves_log_details);
            }
        });
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
            // Twice observed: right after sign-on the listing succeeds and comes
            // back with nothing at all, when sixteen files are up there. An empty
            // answer this early is not evidence of an empty cloud, so it is asked
            // again a few times before being taken at its word.
            DD1CloudListing listing = DD1CloudListing.unknown();
            for (int i = 0; saves != null && i < 10; i++) {
                listing = saves.list();
                if (listing.known() && !listing.files().isEmpty()) break;
                if (listing.known() && i >= 2) break;
                try {
                    Thread.sleep(3000L);
                }
                catch (InterruptedException stop) {
                    return;
                }
            }
            final DD1CloudListing answer = listing;
            if (installService != null) installService.rememberCloudListing(answer);
            main.post(() -> {
                cloud = answer.known() || !cloud.known() ? answer : cloud;
                cloudSlots.clear();
                View current = getView();
                if (current != null)
                    current.findViewById(R.id.PBSavesLoading).setVisibility(View.GONE);
                // What the cloud says it holds, by name. Steam does not always
                // report the folder a file is in, and when it does not there are
                // no slots to show - so the names go on the record.
                log(getString(R.string.dd1_saves_cloud) + ": "
                    + (answer.known() ? answer.files().size() + " files"
                        : getString(R.string.dd1_saves_cloud_unknown)));
                for (DD1SaveSummary.Entry file : answer.files()) log("  " + file.path);
                render();
            });
        }).start();
    }

    private void render() {
        View view = getView();
        if (view == null) return;
        File filesDir = requireContext().getFilesDir();
        showTab(view);
        renderSnapshots(view, filesDir);

        java.util.Map<String, DD1SaveSlot> local = new java.util.LinkedHashMap<>();
        for (DD1SaveSlot slot : DD1SaveSlots.local(filesDir)) local.put(slot.name, slot);
        java.util.Set<String> atRoot = DD1SaveSlots.namesAtRoot(filesDir);
        File root = DD1Saves.root(filesDir);

        // Every slot that is a save on either side; one with only the tutorial
        // record in it is not.
        List<String> names = new ArrayList<>(local.keySet());
        for (String name : DD1SaveSlots.cloudSlotNames(cloud)) {
            if (!names.contains(name)
                    && DD1SaveSync.hasGame(DD1SaveSlots.filesOf(cloud, name, atRoot)))
                names.add(name);
        }
        names.sort((left, right) ->
            Integer.compare(DD1Saves.slotOf(left), DD1Saves.slotOf(right)));
        if (selected == null || !names.contains(selected))
            selected = names.isEmpty() ? null : names.get(0);

        LinearLayout list = view.findViewById(R.id.LLSaveList);
        list.removeAllViews();
        ((TextView)view.findViewById(R.id.TVSaveLegend)).setText(legend());
        TextView empty = view.findViewById(R.id.TVSavesEmpty);
        empty.setVisibility(names.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(cloud.known() ? R.string.dd1_saves_none_anywhere
            : R.string.dd1_saves_none_here_cloud_unknown);
        view.findViewById(R.id.LLSaveDetail).setVisibility(names.isEmpty() ? View.GONE : View.VISIBLE);

        float density = getResources().getDisplayMetrics().density;
        int pad = Math.round(10 * density);
        for (String name : names) {
            List<DD1SaveSummary.Entry> here = filesHere(root, name, local.containsKey(name));
            List<DD1SaveSummary.Entry> there = DD1SaveSlots.filesOf(cloud, name, atRoot);
            DD1SaveSync.State state = DD1SaveSync.of(here, there);
            DD1SaveSlot mine = local.get(name);
            DD1SaveSlot theirs = cloudSlot(name, state, mine);

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = Math.round(4 * density);
            row.setLayoutParams(params);
            if (name.equals(selected)) row.setBackgroundResource(R.drawable.dd1_row_selected);

            TextView dot = new TextView(requireContext());
            dot.setText("●");
            dot.setTextSize(16);
            dot.setTextColor(colorOf(state));
            dot.setPadding(0, 0, pad, 0);
            row.addView(dot);

            TextView title = new TextView(requireContext());
            title.setText(estateOf(mine, theirs));
            title.setTextSize(16);
            title.setTextColor(themeColor(R.attr.colorPrimaryText));
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            long when = Math.max(DD1SaveSync.newest(here), DD1SaveSync.newest(there));
            TextView time = new TextView(requireContext());
            time.setText(when > 0 ? shortTime(when) : "");
            time.setTextSize(12);
            time.setTextColor(themeColor(R.attr.colorSecondaryText));
            row.addView(time);

            row.setOnClickListener(v -> {
                selected = name;
                render();
            });
            list.addView(row);

            if (name.equals(selected)) renderDetail(view, name, state, mine, theirs, here, there);
        }
        if (busy) {
            view.findViewById(R.id.BTSaveUpload).setEnabled(false);
            view.findViewById(R.id.BTSaveDownload).setEnabled(false);
            view.findViewById(R.id.BTSaveDelete).setEnabled(false);
        }
    }

    private void showTab(View view) {
        view.findViewById(R.id.LLSavesManage).setVisibility(snapshotsTab ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.LLSavesSnapshotsTab).setVisibility(snapshotsTab ? View.VISIBLE : View.GONE);
        TextView manage = view.findViewById(R.id.BTSavesTabManage);
        TextView snaps = view.findViewById(R.id.BTSavesTabSnapshots);
        manage.setSelected(!snapshotsTab);
        snaps.setSelected(snapshotsTab);
        manage.setTextColor(themeColor(snapshotsTab ? R.attr.colorSecondaryText : R.attr.colorPrimaryText));
        snaps.setTextColor(themeColor(snapshotsTab ? R.attr.colorPrimaryText : R.attr.colorSecondaryText));
    }

    private void renderSnapshots(View view, File filesDir) {
        LinearLayout snapshots = view.findViewById(R.id.LLSnapshots);
        snapshots.removeAllViews();
        SimpleDateFormat when = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        for (File snapshot : DD1SaveSnapshots.kept(filesDir)) {
            String taken;
            try {
                taken = when.format(new Date(Long.parseLong(snapshot.getName())));
            }
            catch (NumberFormatException notASnapshot) {
                continue;
            }
            View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.dd1_snapshot_row, snapshots, false);
            ((TextView)row.findViewById(R.id.TVSnapshot)).setText(taken);
            row.setOnClickListener(v -> confirmRestore(snapshot, taken));
            snapshots.addView(row);
        }
    }

    private void renderDetail(View view, String name, DD1SaveSync.State state, DD1SaveSlot mine,
            DD1SaveSlot theirs, List<DD1SaveSummary.Entry> here, List<DD1SaveSummary.Entry> there) {
        String mineName = mine == null ? null : mine.estate;
        String theirName = theirs == null ? null : theirs.estate;
        // The slot rides on the title's line, small, instead of taking one of
        // its own.
        String title = mineName != null && theirName != null && !mineName.equals(theirName)
            ? mineName + "  ↔  " + theirName : estateOf(mine, theirs);
        String slotText = "   " + getString(R.string.dd1_saves_slot, DD1Saves.slotOf(name) + 1);
        android.text.SpannableString titled = new android.text.SpannableString(title + slotText);
        titled.setSpan(new android.text.style.RelativeSizeSpan(0.55f), title.length(),
            titled.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        titled.setSpan(new android.text.style.ForegroundColorSpan(themeColor(R.attr.colorSecondaryText)),
            title.length(), titled.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        titled.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.NORMAL),
            title.length(), titled.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ((TextView)view.findViewById(R.id.TVSaveTitle)).setText(titled);
        TextView stateView = view.findViewById(R.id.TVSaveState);
        stateView.setText("● " + labelOf(state));
        stateView.setTextColor(colorOf(state));
        ((TextView)view.findViewById(R.id.TVSaveLocal)).setText(side(mine, here));
        // Green around both sides when they hold the same save; grey otherwise.
        boolean same = state == DD1SaveSync.State.SAME;
        outline((View)view.findViewById(R.id.TVSaveLocal).getParent(), same);
        outline((View)view.findViewById(R.id.TVSaveCloud).getParent(), same);
        boolean inCloud = state != DD1SaveSync.State.LOCAL_ONLY;
        ((TextView)view.findViewById(R.id.TVSaveCloud)).setText(!inCloud
            ? getString(R.string.dd1_saves_none)
            : side(theirs != null ? theirs : (state == DD1SaveSync.State.SAME ? mine : null), there));

        long localTime = DD1SaveSync.newest(here), cloudTime = DD1SaveSync.newest(there);
        String advice;
        switch (state) {
            case SAME: advice = getString(R.string.dd1_saves_advice_same); break;
            case LOCAL_ONLY: advice = getString(R.string.dd1_saves_advice_local_only); break;
            case CLOUD_ONLY: advice = getString(R.string.dd1_saves_advice_cloud_only); break;
            default:
                advice = localTime >= cloudTime
                    ? getString(R.string.dd1_saves_advice_local_newer, shortTime(localTime))
                    : getString(R.string.dd1_saves_advice_cloud_newer, shortTime(cloudTime));
        }
        if (!busy) {
            ((TextView)view.findViewById(R.id.TVSavesStatus)).setText(
                lastOutcome != null && name.equals(lastOutcomeFor) ? lastOutcome : advice);
            view.findViewById(R.id.PBSavesTransfer).setVisibility(View.INVISIBLE);
        }

        android.widget.Button up = view.findViewById(R.id.BTSaveUpload);
        android.widget.Button down = view.findViewById(R.id.BTSaveDownload);
        up.setEnabled(mine != null && state != DD1SaveSync.State.SAME);
        down.setEnabled(inCloud && state != DD1SaveSync.State.SAME);
        up.setOnClickListener(v -> {
            if (mine != null) upload(mine);
        });
        down.setOnClickListener(v -> download(name));
        android.widget.Button delete = view.findViewById(R.id.BTSaveDelete);
        delete.setEnabled(mine != null);
        delete.setAlpha(mine != null ? 1f : 0.4f);
        delete.setOnClickListener(v -> {
            if (mine != null) confirmDelete(name, mine, inCloud);
        });
    }

    private void outline(View box, boolean same) {
        float density = getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(themeColor(R.attr.colorPrimarySurface));
        shape.setCornerRadius(10 * density);
        shape.setStroke(Math.round((same ? 2 : 1) * density), same ? GREEN
            : ContextCompat.getColor(requireContext(), R.color.dd1_outline));
        box.setBackground(shape);
    }

    // Only this device's copy goes, and a snapshot is taken first, so the
    // snapshots tab can bring it back.
    private void confirmDelete(String name, DD1SaveSlot slot, boolean inCloud) {
        if (refuseWhileBusy()) return;
        new AlertDialog.Builder(requireContext(), R.style.DD1Dialog)
            .setTitle(R.string.dd1_saves_delete_title)
            .setMessage(getString(inCloud ? R.string.dd1_saves_delete_message_cloud
                    : R.string.dd1_saves_delete_message_only,
                slot.estate == null ? name : slot.estate, DD1Saves.slotOf(name) + 1))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dd1_saves_delete, (dialog, which) -> delete(name))
            .show();
    }

    private void delete(String name) {
        if (refuseWhileBusy()) return;
        File filesDir = requireContext().getFilesDir();
        setBusy(true);
        status(getString(R.string.dd1_saves_status_delete), 0, 0);
        new Thread(() -> {
            boolean kept = DD1SaveSnapshots.take(filesDir, System.currentTimeMillis()) != null;
            boolean gone = kept && deleteTree(new File(DD1Saves.root(filesDir), name));
            main.post(() -> {
                String outcome = !kept ? getString(R.string.dd1_saves_no_snapshot)
                    : gone ? getString(R.string.dd1_saves_deleted)
                    : getString(R.string.dd1_saves_delete_failed);
                log(outcome);
                finish(outcome);
            });
        }).start();
    }

    private static boolean deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) if (!deleteTree(child)) return false;
        return !file.exists() || file.delete();
    }

    // Estate, time played, when it was last saved.
    private String side(DD1SaveSlot slot, List<DD1SaveSummary.Entry> files) {
        if (slot == null && files.isEmpty()) return getString(R.string.dd1_saves_none);
        StringBuilder text = new StringBuilder();
        text.append(slot != null && slot.estate != null ? slot.estate : getString(R.string.dd1_saves_reading));
        if (slot != null && slot.playedSeconds >= 0)
            text.append(" · ").append(String.format(Locale.US,
                getString(R.string.dd1_saves_played), slot.playedSeconds / 60f));
        long when = DD1SaveSync.newest(files);
        if (when > 0) text.append('\n').append(longTime(when));
        return text.toString();
    }

    private List<DD1SaveSummary.Entry> filesHere(File root, String name, boolean present) {
        List<DD1SaveSummary.Entry> named = new ArrayList<>();
        if (!present) return named;
        for (DD1SaveSummary.Entry file : DD1SaveSummary.of(new File(root, name)))
            named.add(new DD1SaveSummary.Entry(name + "/" + file.path, file.length,
                file.modifiedMillis, file.sha1));
        return named;
    }

    // The same files need no download to be named; anything else asks the cloud
    // for its game file once, and the list is drawn again when it arrives.
    private DD1SaveSlot cloudSlot(String name, DD1SaveSync.State state, DD1SaveSlot mine) {
        if (state == DD1SaveSync.State.LOCAL_ONLY) return null;
        if (state == DD1SaveSync.State.SAME && mine != null) return mine;
        DD1SaveSlot known = cloudSlots.get(name);
        if (known != null || describing.contains(name) || installService == null) return known;
        describing.add(name);
        DD1CloudSaves saves = installService.cloudSaves();
        new Thread(() -> {
            DD1SaveSlot slot = DD1SaveSlot.of(name, saves.fetch(name + "/persist.game.json"));
            main.post(() -> {
                describing.remove(name);
                if (slot != null) {
                    cloudSlots.put(name, slot);
                    render();
                }
            });
        }).start();
        return null;
    }

    // How a slot is named to a person: the estate, and which slot it is.
    private String display(String slot) {
        DD1SaveSlot here = DD1SaveSlot.of(new File(DD1Saves.root(requireContext().getFilesDir()), slot));
        DD1SaveSlot there = cloudSlots.get(slot);
        String estate = here != null && here.estate != null ? here.estate
            : there != null && there.estate != null ? there.estate : null;
        String number = getString(R.string.dd1_saves_slot, DD1Saves.slotOf(slot) + 1);
        return estate == null ? number : estate + " (" + number + ")";
    }

    private String estateOf(DD1SaveSlot mine, DD1SaveSlot theirs) {
        if (mine != null && mine.estate != null) return mine.estate;
        if (theirs != null && theirs.estate != null) return theirs.estate;
        return getString(R.string.dd1_saves_reading);
    }

    private String labelOf(DD1SaveSync.State state) {
        switch (state) {
            case SAME: return getString(R.string.dd1_saves_state_same);
            case DIFFERENT: return getString(R.string.dd1_saves_state_different);
            case CLOUD_ONLY: return getString(R.string.dd1_saves_state_cloud_only);
            default: return getString(R.string.dd1_saves_state_local_only);
        }
    }

    private static int colorOf(DD1SaveSync.State state) {
        switch (state) {
            case SAME: return GREEN;
            case DIFFERENT: return ORANGE;
            case CLOUD_ONLY: return YELLOW;
            default: return RED;
        }
    }

    private CharSequence legend() {
        android.text.SpannableStringBuilder text = new android.text.SpannableStringBuilder();
        for (DD1SaveSync.State state : DD1SaveSync.State.values()) {
            int start = text.length();
            text.append("● ");
            text.setSpan(new android.text.style.ForegroundColorSpan(colorOf(state)), start,
                start + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.append(labelOf(state)).append("   ");
        }
        return text;
    }

    private static String shortTime(long millis) {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.US).format(new Date(millis));
    }

    private static String longTime(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(millis));
    }

    private int themeColor(int attr) {
        android.util.TypedValue value = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(attr, value, true);
        return value.resourceId != 0 ? ContextCompat.getColor(requireContext(), value.resourceId)
            : value.data;
    }

    // The estate's name and how far the campaign has got. The time it was saved
    // is left to the dialog: minutes played is the better answer to "which of
    // these two do I want", and a date beside it only competes with it.
    private String describe(DD1SaveSlot slot) {
        StringBuilder text = new StringBuilder();
        if (slot.estate != null) text.append(slot.estate);
        if (slot.playedSeconds >= 0) {
            if (text.length() > 0) text.append('\n');
            text.append(String.format(Locale.US, getString(R.string.dd1_saves_played),
                slot.playedSeconds / 60f));
        }
        return text.length() == 0 ? slot.name : text.toString();
    }

    // Replacing a save is the one choice that cannot be taken back, so this one
    // says everything known, the save time included.
    private String describeFully(DD1SaveSlot slot) {
        String summary = describe(slot).replace('\n', ' ');
        return slot.savedAt == null ? summary : summary + " · " + slot.savedAt;
    }

    // Kept for the log dialog, and written into it while it is open. Called on
    // the main thread only.
    private void log(String line) {
        if (logText.length() > 0) logText.append('\n');
        logText.append(line);
        if (openLog != null) openLog.append(openLog.length() == 0 ? line : "\n" + line);
    }

    private void upload(DD1SaveSlot slot) {
        if (refuseWhileBusy()) return;
        setBusy(true);
        File filesDir = requireContext().getFilesDir();
        File root = DD1Saves.root(filesDir);
        // The summary of one slot names its files relative to the slot, and the
        // cloud holds them under it.
        List<DD1SaveSummary.Entry> named = new ArrayList<>();
        for (DD1SaveSummary.Entry file : DD1SaveSummary.of(new File(root, slot.name)))
            named.add(new DD1SaveSummary.Entry(slot.name + "/" + file.path, file.length,
                file.modifiedMillis, file.sha1));
        log(slot.name + " → " + getString(R.string.dd1_saves_cloud)
            + " (" + named.size() + ")");
        status(getString(R.string.dd1_saves_status_up, display(slot.name), 0, named.size()), 0, named.size());

        new Thread(() -> {
            if (DD1SaveSnapshots.take(filesDir, System.currentTimeMillis()) == null) {
                main.post(() -> {
                    log(getString(R.string.dd1_saves_no_snapshot));
                    finish(getString(R.string.dd1_saves_no_snapshot));
                });
                return;
            }
            main.post(() -> log(getString(R.string.dd1_saves_snapshot_taken)));
            for (DD1SaveSummary.Entry file : named)
                main.post(() -> log("  " + file.path + "  " + file.length));
            List<String> local = new ArrayList<>();
            for (DD1SaveSummary.Entry file : named) local.add(file.path);
            List<String> stale = DD1SaveSlots.staleInCloud(cloud, slot.name,
                DD1SaveSlots.namesAtRoot(filesDir), local);
            for (String path : stale)
                main.post(() -> log("  − " + path));
            DD1CloudSaves saves = installService == null ? null : installService.cloudSaves();
            boolean sent = saves != null && saves.upload(root, named, stale, done ->
                main.post(() -> status(getString(R.string.dd1_saves_status_up, display(slot.name),
                    done, named.size()), done, named.size())));
            String why = saves == null ? null : saves.lastFailure();
            main.post(() -> {
                String outcome = sent ? getString(R.string.dd1_saves_sent, display(slot.name), named.size())
                    : getString(R.string.dd1_saves_send_failed)
                        + (why == null ? "" : " - " + why);
                log(outcome);
                finish(outcome);
                loadCloud();
            });
        }).start();
    }

    private void download(String slot) {
        if (refuseWhileBusy()) return;
        setBusy(true);
        File filesDir = requireContext().getFilesDir();
        List<DD1SaveSummary.Entry> files = DD1SaveSlots.filesOf(cloud, slot,
            DD1SaveSlots.namesAtRoot(filesDir));
        log(getString(R.string.dd1_saves_cloud) + " → " + slot
            + " (" + files.size() + ")");
        status(getString(R.string.dd1_saves_status_down, display(slot), 0, files.size()), 0, files.size());

        new Thread(() -> {
            DD1SaveStaging.clear(filesDir, slot);
            int done = 0;
            for (DD1SaveSummary.Entry file : files) {
                byte[] content = installService == null ? null
                    : installService.cloudSaves().fetch(file.path);
                boolean kept = content != null
                    && DD1SaveStaging.put(filesDir, slot, file.path, content);
                final String line = "  " + file.path + "  "
                    + (kept ? String.valueOf(content.length) : "?");
                main.post(() -> log(line));
                if (!kept) break;
                done++;
                final int count = done;
                main.post(() -> status(getString(R.string.dd1_saves_status_down, display(slot),
                    count, files.size()), count, files.size()));
            }
            final boolean whole = done == files.size() && !files.isEmpty();
            main.post(() -> {
                if (whole) confirmReplace(slot);
                else {
                    DD1SaveStaging.clear(filesDir, slot);
                    log(getString(R.string.dd1_saves_get_failed));
                    finish(getString(R.string.dd1_saves_get_failed));
                }
            });
        }).start();
    }

    private void confirmRestore(File snapshot, String taken) {
        if (refuseWhileBusy()) return;
        new AlertDialog.Builder(requireContext(), R.style.DD1Dialog)
            .setTitle(R.string.dd1_saves_restore_title)
            .setMessage(getString(R.string.dd1_saves_restore, taken))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                boolean back = DD1SaveSnapshots.restore(
                    requireContext().getFilesDir(), snapshot);
                String outcome = back ? getString(R.string.dd1_saves_restored, taken)
                    : getString(R.string.dd1_saves_restore_failed);
                log(outcome);
                finish(outcome);
                render();
            })
            .show();
    }

    // The staged slot is described before anything is replaced, so the choice is
    // made against what the cloud actually holds rather than against its name.
    private void confirmReplace(String slot) {
        log(getString(R.string.dd1_saves_asking));
        File filesDir = requireContext().getFilesDir();
        DD1SaveSlot staged = DD1SaveStaging.describe(filesDir, slot);
        if (staged == null) {
            DD1SaveStaging.clear(filesDir, slot);
            log(getString(R.string.dd1_saves_get_failed));
            finish(getString(R.string.dd1_saves_get_failed));
            return;
        }
        DD1SaveSlot live = DD1SaveSlot.of(new File(DD1Saves.root(filesDir), slot));
        new AlertDialog.Builder(requireContext(), R.style.DD1Dialog)
            .setTitle(R.string.dd1_saves_replace_title)
            .setMessage(getString(R.string.dd1_saves_replace,
                live == null ? getString(R.string.dd1_saves_none) : describeFully(live),
                describeFully(staged)))
            .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                DD1SaveStaging.clear(filesDir, slot);
                log(getString(R.string.dd1_saves_kept_local));
                finish(getString(R.string.dd1_saves_kept_local));
            })
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                boolean applied = DD1SaveStaging.apply(filesDir, slot);
                String outcome = applied ? getString(R.string.dd1_saves_replaced, display(slot))
                    : getString(R.string.dd1_saves_get_failed);
                log(outcome);
                finish(outcome);
                render();
            })
            .setOnCancelListener(dialog -> {
                DD1SaveStaging.clear(filesDir, slot);
                log(getString(R.string.dd1_saves_kept_local));
                finish(getString(R.string.dd1_saves_kept_local));
            })
            .show();
    }
}
