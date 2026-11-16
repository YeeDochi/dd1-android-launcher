package com.winlator.dd1;

import com.winlator.XServerDisplayActivity;
import com.winlator.R;
import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.GPUHelper;
import com.winlator.dd1.DD1Game;
import com.winlator.dd1.DD1HomeState;
import com.winlator.dd1.DD1InstallPhase;
import com.winlator.dd1.DD1InstallService;
import com.winlator.dd1.DD1InstallSnapshot;
import com.winlator.dd1.DD1ProfileConfig;
import com.winlator.xenvironment.RootFS;

import org.json.JSONObject;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.File;

import java.util.Locale;

public class DD1HomeFragment extends Fragment {
    private final Handler handler = new Handler();
    private View rootView;
    private boolean creatingProfile;
    private boolean profileCreationFailed;
    private boolean driverChecked;
    private boolean moveInsideTried;
    private DD1InstallService installService;
    private boolean serviceBound;
    private DD1InstallSnapshot installSnapshot = DD1InstallSnapshot.restoring();
    private final DD1InstallService.Listener installListener = this::renderInstallSnapshot;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            installService = ((DD1InstallService.LocalBinder)binder).getService();
            serviceBound = true;
            installService.observe(installListener);
            moveInsideIfLeftOutside();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            installService = null;
            serviceBound = false;
        }
    };

    private java.util.function.Consumer<Boolean> afterStorage;
    private final androidx.activity.result.ActivityResultLauncher<String[]> storagePermission =
        registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
            granted -> {
                java.util.function.Consumer<Boolean> next = afterStorage;
                afterStorage = null;
                if (next != null) next.accept(Boolean.TRUE.equals(
                    granted.get(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)));
            });

    private boolean hasStorage() {
        return androidx.core.content.ContextCompat.checkSelfPermission(requireContext(),
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    // For work on a game that may sit in phone storage: the permission can be
    // taken back in system settings after the game was put there.
    private void withStorageWhereNeeded(Runnable then) {
        if (!DD1GameLocation.STORAGE.equals(DD1GameLocation.chosen(requireContext()))) {
            then.run();
            return;
        }
        withStorage(granted -> {
            if (granted) then.run();
            else Toast.makeText(requireContext(), R.string.dd1_storage_needed,
                Toast.LENGTH_LONG).show();
        });
    }

    private void withStorage(java.util.function.Consumer<Boolean> then) {
        if (hasStorage()) {
            then.accept(true);
            return;
        }
        afterStorage = then;
        storagePermission.launch(new String[] {android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE});
    }

    // The game goes inside the app. Storage access is still asked for first:
    // mods and replacement files are dropped in DD1/mods and DD1/overrides, and
    // without it DD1/game cannot be seen either, so a reinstall - or an install
    // from 0.1.13 or 0.1.14 - would download 4 GB over a game already there.
    // Found whole, that game is used where it is, and the home screen offers to
    // bring it inside. Refused, the download goes ahead all the same.
    private void startDownload() {
        Context context = requireContext();
        if (DD1GameLocation.STORAGE.equals(DD1GameLocation.chosen(context)) || !hasStorage()) {
            withStorage(granted -> {
                if (!granted && DD1GameLocation.STORAGE.equals(DD1GameLocation.chosen(context))) {
                    DD1GameLocation.choose(context, DD1GameLocation.APP);
                    Toast.makeText(context, R.string.dd1_storage_denied, Toast.LENGTH_LONG).show();
                }
                else if (granted && useGameAlreadyThere(context)) return;
                startJob(DD1InstallService::download);
            });
            return;
        }
        if (useGameAlreadyThere(context)) return;
        startJob(DD1InstallService::download);
    }

    // A whole game where the launcher looks, or in DD1/game when it looks inside
    // the app and finds nothing, is used rather than fetched again: a retry after
    // a move that ran out of room must not put 4 GB over the game it was moving.
    private boolean useGameAlreadyThere(Context context) {
        if (DD1Game.findExecutable(DD1GameLocation.root(context)) == null) {
            if (DD1Game.findExecutable(DD1GameLocation.storageRoot()) == null) return false;
            DD1GameLocation.choose(context, DD1GameLocation.STORAGE);
        }
        if (DD1GameLocation.STORAGE.equals(DD1GameLocation.chosen(context)))
            Toast.makeText(context, R.string.dd1_storage_found_game, Toast.LENGTH_LONG).show();
        refresh();
        return true;
    }

    private void startJob(java.util.function.Predicate<DD1InstallService> job) {
        if (installService != null && !job.test(installService))
            Toast.makeText(requireContext(), R.string.dd1_busy, Toast.LENGTH_LONG).show();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dd1_home_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rootView = view;
        view.findViewById(R.id.BTMenu).setOnClickListener(
            v -> ((DD1Activity)requireActivity()).toggleDrawer());
        view.findViewById(R.id.BTDeleteGame).setOnClickListener(v -> confirmDeleteGame());
        view.findViewById(R.id.BTGameFiles).setOnClickListener(v -> openGameFiles());
        view.findViewById(R.id.BTRepairGame).setOnClickListener(v -> confirmRepair());
        view.findViewById(R.id.BTSignOutAlways)
            .setOnClickListener(v -> withService(DD1InstallService::signOut));
        view.findViewById(R.id.BTCancelSignIn)
            .setOnClickListener(v -> withService(DD1InstallService::signOut));
        view.findViewById(R.id.BTSteamLogin).setOnClickListener(v -> withService(DD1InstallService::startQr));
        view.findViewById(R.id.BTSteamCredentials).setOnClickListener(v -> startCredentials());
        view.findViewById(R.id.BTSteamCode).setOnClickListener(v -> submitCode());
        view.findViewById(R.id.BTDownload).setOnClickListener(v -> startDownload());
        view.findViewById(R.id.BTRetryDownload).setOnClickListener(v -> startDownload());
        view.findViewById(R.id.BTCancelDownload).setOnClickListener(v -> withService(DD1InstallService::cancel));
        view.findViewById(R.id.BTSteamSignOut).setOnClickListener(v -> withService(DD1InstallService::signOut));
        DD1Update.checkOnce(requireContext(), this::offerUpdate);
    }

    // The launcher cannot install itself over itself, so the offer is the page the
    // APK is on. Refusing for the day is a third button rather than a checkbox:
    // the answer is what to do now, and there are three answers.
    private void offerUpdate(String version) {
        Activity activity = getActivity();
        if (activity == null) return;
        new AlertDialog.Builder(activity, R.style.DD1Dialog)
            .setTitle(getString(R.string.dd1_update_title, version))
            .setMessage(getString(R.string.dd1_update_message,
                DD1Update.installedVersion(activity)))
            .setNeutralButton(R.string.dd1_update_hide_today,
                (dialog, which) -> DD1Update.hideForToday(activity))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dd1_update_get, (dialog, which) -> openReleases(activity))
            .show();
    }

    private void openReleases(Activity activity) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse(DD1Update.RELEASES_PAGE)));
        }
        // A device with no browser at all: saying so beats disappearing.
        catch (Exception noBrowser) {
            Toast.makeText(activity, DD1Update.RELEASES_PAGE, Toast.LENGTH_LONG).show();
        }
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

    @Override
    public void onResume() {
        super.onResume();
        refresh();
        // What the game wrote last session, put where deleting the app cannot
        // reach it. This screen resuming is both moments that matter - the app
        // opening, and the game closing - and the mirror skips itself when the
        // saves have not moved, so an idle resume costs one scan. Without the
        // storage permission (asked for on the saves and workshop screens) the
        // copy quietly does not happen; it is a net, not a promise.
        File filesDir = requireActivity().getFilesDir();
        new Thread(() -> android.util.Log.i("DD1Mirror",
            DD1SaveSnapshots.mirror(filesDir, DD1SavesFragment.autoMirrorFolder(),
                System.currentTimeMillis())), "dd1-save-mirror").start();
    }

    @Override
    public void onPause() {
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    private void refresh() {
        if (rootView == null) return;

        Activity activity = getActivity();
        File executable = DD1Game.findExecutable(DD1GameLocation.root(activity));
        ContainerManager manager = DD1Profiles.manager(activity);
        Container container = DD1Profiles.first(manager);
        boolean runtimeReady = RootFS.find(activity).isValid();

        if (runtimeReady && container == null && manager != null
                && !creatingProfile && !profileCreationFailed) {
            creatingProfile = true;
            JSONObject data = new JSONObject(DD1ProfileConfig.create(
                DD1GraphicsChoice.resolve(activity), Container.getFallbackCPUList()));
            manager.createContainerAsync(data, created -> {
                creatingProfile = false;
                profileCreationFailed = created == null;
                refresh();
            });
        }
        // A profile made on an earlier build kept whichever driver was picked
        // then, and a wrong one has no way out: this screen only ever creates a
        // profile, never revisits one. Nothing else sets this field, so bringing
        // it back in line with the device is safe - and has to be revisited the
        // day the settings screen lets a person choose.
        // Asked once: reading the renderer blocks the caller until a GL context
        // comes up, and refresh() runs on every resume and once a second while
        // the runtime unpacks. What it settles on is the choice if one was made and
        // the device's own answer otherwise, so correcting a profile can no longer
        // undo what somebody picked in the settings.
        else if (container != null && !driverChecked) {
            driverChecked = true;
            String belongs = DD1GraphicsChoice.resolve(activity);
            boolean changed = DD1ProfileConfig.trim(container);
            if (!belongs.equals(container.getGraphicsDriver())) {
                container.setGraphicsDriver(belongs);
                changed = true;
            }
            if (changed) container.saveData();
        }

        DD1HomeState state = DD1HomeState.from(runtimeReady, executable != null, container != null);

        TextView status = rootView.findViewById(R.id.TVStatus);
        Button primary = rootView.findViewById(R.id.BTPrimaryAction);
        primary.setVisibility(View.VISIBLE);
        // Signing in does not wait for the runtime, so the Steam state decides the
        // right half even while the root filesystem is still unpacking.
        if (installSnapshot != null && installSnapshot.phase != DD1InstallPhase.READY
                && DD1RightPane.from(installSnapshot.phase, executable != null) != DD1RightPane.LOG) {
            renderInstallSnapshot(installSnapshot);
            return;
        }
        setInstallPanelVisible(false);
        rootView.findViewById(R.id.FLChecking).setVisibility(View.GONE);
        rootView.findViewById(R.id.SVInstallLog).setVisibility(View.VISIBLE);
        // Cancel sits outside the install panel, so hiding the panel leaves it
        // behind after a repair or DLC download, offering to stop nothing.
        rootView.findViewById(R.id.BTCancelDownload).setVisibility(View.GONE);
        applySignInControls(installSnapshot == null
            ? DD1InstallPhase.SIGNED_OUT : installSnapshot.phase);
        applyDeleteVisibility();
        primary.setEnabled(state == DD1HomeState.READY || state == DD1HomeState.PROFILE_MISSING);

        switch (state) {
            case READY:
                // A game left in DD1/game by 0.1.13 or 0.1.14 takes minutes to
                // change scenes (issue #4); the way out is offered where Play is.
                if (DD1GameLocation.STORAGE.equals(DD1GameLocation.chosen(activity))) {
                    status.setText(R.string.dd1_status_ready_storage);
                    status.setOnClickListener(v -> confirmMoveIntoApp());
                }
                else {
                    status.setText(R.string.dd1_status_ready);
                    status.setOnClickListener(null);
                    status.setClickable(false);
                }
                primary.setText(R.string.dd1_play);
                primary.setOnClickListener(v -> launch(activity, container, executable));
                break;
            case PROFILE_MISSING:
                status.setText(profileCreationFailed ? R.string.dd1_status_profile_error : R.string.dd1_status_runtime_preparing);
                primary.setText(R.string.dd1_play);
                primary.setEnabled(false);
                primary.setOnClickListener(null);
                break;
            case GAME_MISSING:
                primary.setVisibility(View.GONE);
                renderInstallSnapshot(installSnapshot);
                break;
            default:
                status.setText(R.string.dd1_status_runtime_preparing);
                primary.setText(R.string.dd1_play);
                primary.setOnClickListener(null);
                handler.postDelayed(this::refresh, 1000);
        }
    }

    void renderInstallSnapshot(DD1InstallSnapshot snapshot) {
        installSnapshot = snapshot;
        if (rootView == null) return;
        setLog(TextUtils.join("\n", snapshot.logLines));

        boolean installed = DD1Game.findExecutable(DD1GameLocation.root(requireContext())) != null;
        DD1RightPane pane = DD1RightPane.from(snapshot.phase, installed,
            snapshot.challengeUrl != null || snapshot.codePrompt != null);
        setInstallPanelVisible(pane == DD1RightPane.SIGN_IN || pane == DD1RightPane.INSTALL);
        rootView.findViewById(R.id.SVInstallLog)
            .setVisibility(pane == DD1RightPane.LOG ? View.VISIBLE : View.GONE);
        rootView.findViewById(R.id.FLChecking)
            .setVisibility(pane == DD1RightPane.CHECKING ? View.VISIBLE : View.GONE);

        // Reaching the log means the account side is settled, so the home state
        // takes over and shows Play.
        if (pane == DD1RightPane.LOG) {
            refresh();
            return;
        }
        applySignInControls(snapshot.phase);
        applyDeleteVisibility();
        // Sign-in is a short form and reads better centred; the download screen
        // needs the height for the DLC list.
        ((LinearLayout)rootView.findViewById(R.id.LLSteamInstall)).setGravity(
            pane == DD1RightPane.SIGN_IN ? android.view.Gravity.CENTER_VERTICAL
                : android.view.Gravity.TOP);
        setInstallPanelFills(snapshot.phase != DD1InstallPhase.DOWNLOADING
            && snapshot.phase != DD1InstallPhase.VERIFYING);
        rootView.findViewById(R.id.BTPrimaryAction).setVisibility(View.GONE);
        TextView status = rootView.findViewById(R.id.TVStatus);
        status.setText(snapshot.message);
        // Only the ready screen's storage warning answers a tap.
        status.setOnClickListener(null);
        status.setClickable(false);

        int[] controls = {R.id.IVSteamQr, R.id.BTSteamLogin, R.id.ETSteamAccount,
            R.id.ETSteamPassword, R.id.BTSteamCredentials, R.id.BTDownload,
            R.id.TVSteamCodeHint, R.id.ETSteamCode, R.id.BTSteamCode,
            R.id.LLDownloadBar,
            R.id.BTCancelDownload, R.id.BTRetryDownload, R.id.BTSteamSignOut,
            R.id.TVDlcTitle, R.id.SVDlcChoices};
        for (int id : controls) rootView.findViewById(id).setVisibility(View.GONE);

        if (snapshot.phase == DD1InstallPhase.SIGNED_OUT) {
            show(R.id.BTSteamLogin, R.id.ETSteamAccount, R.id.ETSteamPassword,
                R.id.BTSteamCredentials);
        }
        else if (snapshot.phase == DD1InstallPhase.AUTHENTICATING) {
            if (snapshot.codePrompt != null) showCodePrompt(snapshot.codePrompt);
            else if (snapshot.challengeUrl != null) {
                ImageView qr = rootView.findViewById(R.id.IVSteamQr);
                qr.setImageBitmap(qr(snapshot.challengeUrl));
                qr.setVisibility(View.VISIBLE);
            }
        }
        else if (snapshot.phase == DD1InstallPhase.READY_TO_INSTALL) {
            show(R.id.BTDownload);
            renderDlcChoices();
        }
        else if (snapshot.phase == DD1InstallPhase.DOWNLOADING ||
                snapshot.phase == DD1InstallPhase.VERIFYING) {
            show(R.id.LLDownloadBar);
            // Verifying moves the finished tree into place. There is nothing left
            // to call off by then, and stopping halfway would leave the install
            // half promoted.
            if (snapshot.phase == DD1InstallPhase.DOWNLOADING) show(R.id.BTCancelDownload);
            ProgressBar progress = rootView.findViewById(R.id.PBDownload);
            progress.setIndeterminate(snapshot.totalBytes <= 0);
            if (snapshot.totalBytes > 0)
                progress.setProgress((int)Math.min(1000, snapshot.downloadedBytes * 1000 / snapshot.totalBytes));
            String summary = progressSummary(snapshot);
            ((TextView)rootView.findViewById(R.id.TVDownloadPercent))
                .setText(summary == null ? getString(R.string.dd1_state_preparing) : summary);
            rootView.findViewById(R.id.SVInstallLog).setVisibility(View.VISIBLE);
        }
        else if (snapshot.phase == DD1InstallPhase.NOT_OWNED) {
            // Nothing to offer but a different account.
        }
        else if (snapshot.phase == DD1InstallPhase.ERROR) {
            if (installService != null && installService.canDownload()) show(R.id.BTRetryDownload);
            else show(R.id.ETSteamAccount, R.id.ETSteamPassword, R.id.BTSteamCredentials,
                R.id.BTSteamLogin);
        }
    }

    // Fixed-height log with its own scroller: without this the growing text
    // pushes the rest of the pane around on every update.
    // The choice belongs next to the download button, not behind a menu.
    private void renderDlcChoices() {
        LinearLayout list = rootView.findViewById(R.id.LLDlcChoices);
        list.removeAllViews();
        if (installService == null) return;
        DlcSelection selection = installService.dlcSelection();
        if (selection.owned().isEmpty()) return;

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int appId : selection.owned()) {
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
        show(R.id.TVDlcTitle, R.id.SVDlcChoices);
    }

    // Deleting depends only on whether files are on disk, so it is decided once
    // instead of in every branch that draws the screen. A half-finished download
    // holds gigabytes in staging and has to be removable too.
    private void applyDeleteVisibility() {
        File filesDir = DD1GameLocation.root(requireContext());
        boolean anything = new File(filesDir, "game").isDirectory()
            || new File(filesDir, "staging").isDirectory();
        rootView.findViewById(R.id.BTDeleteGame)
            .setVisibility(anything ? View.VISIBLE : View.GONE);
        // Only with a game to open or check, and not while a download owns it.
        boolean installed = new File(filesDir, "game").isDirectory();
        boolean idle = installSnapshot == null
            || (installSnapshot.phase != DD1InstallPhase.DOWNLOADING
                && installSnapshot.phase != DD1InstallPhase.VERIFYING);
        rootView.findViewById(R.id.BTGameFiles)
            .setVisibility(installed && idle ? View.VISIBLE : View.GONE);
        rootView.findViewById(R.id.BTRepairGame).setVisibility(installed && idle
            && installService != null && installService.canDownload()
            ? View.VISIBLE : View.GONE);
    }

    // In phone storage the game is a folder like any other, so it opens in the
    // phone's own file manager. Inside the app no file manager can reach it, so
    // what opens is DD1/overrides, whose files are laid over the game at Play,
    // or the runtime's Windows file manager, which sees the game itself.
    private void openGameFiles() {
        Context context = requireContext();
        if (DD1GameLocation.STORAGE.equals(DD1GameLocation.chosen(context))) {
            withStorageWhereNeeded(() -> DD1FileManager.open(context, DD1GameLocation.gameDir(context)));
            return;
        }
        new AlertDialog.Builder(context, R.style.DD1Dialog)
            .setTitle(R.string.dd1_game_files_in_app_title)
            .setMessage(R.string.dd1_game_files_in_app_message)
            .setPositiveButton(R.string.dd1_game_files_overrides, (dialog, which) -> openOverrides())
            .setNeutralButton(R.string.dd1_game_files_windows, (dialog, which) -> openWindowsFiles())
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void openOverrides() {
        Context context = requireContext();
        withStorage(granted -> {
            if (!granted) {
                Toast.makeText(context, R.string.dd1_storage_needed_overrides,
                    Toast.LENGTH_LONG).show();
                return;
            }
            DD1Overrides.open(context);
        });
    }

    // A game left in DD1/game by 0.1.13 or 0.1.14 is brought inside on the first
    // start after the update, so updating is all anyone has to do. Tried once per
    // screen: refused storage access, too little room or a busy service leave
    // the warning on the home screen, which offers the same move by hand, and the
    // next start tries again. Too little room is judged from free space alone,
    // so a phone that cannot take the copy is not walked through it every start.
    private void moveInsideIfLeftOutside() {
        Context context = getContext();
        if (moveInsideTried || context == null || installService == null) return;
        if (!DD1GameLocation.STORAGE.equals(DD1GameLocation.chosen(context))) return;
        moveInsideTried = true;
        withStorage(granted -> {
            if (!granted || installService == null) return;
            if (DD1Game.findExecutable(DD1GameLocation.root(context)) == null) return;
            if (context.getFilesDir().getUsableSpace() < DD1GameLocation.ROOM_FOR_A_MOVE) return;
            installService.moveGame(DD1GameLocation.APP);
        });
    }

    private void confirmMoveIntoApp() {
        new AlertDialog.Builder(requireContext(), R.style.DD1Dialog)
            .setTitle(R.string.dd1_storage_slow_title)
            .setMessage(R.string.dd1_storage_slow_message)
            .setPositiveButton(R.string.dd1_game_location_to_app, (dialog, which) -> {
                if (installService == null || !installService.moveGame(DD1GameLocation.APP))
                    Toast.makeText(requireContext(), R.string.dd1_game_location_busy,
                        Toast.LENGTH_LONG).show();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    // A DLL that stops the game from starting must still be removable while the
    // game is inside the app. This starts the runtime with nothing but its file
    // manager: the game is G: and the phone's Download folder is D:.
    private void openWindowsFiles() {
        Activity activity = getActivity();
        if (activity == null) return;
        Container container = DD1Profiles.first(activity);
        if (container == null) return;
        mapGameDrive(activity, container);
        Intent intent = new Intent(activity, XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        activity.startActivity(intent);
    }

    private void confirmRepair() {
        new AlertDialog.Builder(requireContext(), R.style.DD1Dialog)
            .setTitle(R.string.dd1_repair_game)
            .setMessage(R.string.dd1_repair_game_message)
            .setPositiveButton(R.string.dd1_repair_game_start,
                (dialog, which) -> withStorageWhereNeeded(() -> startJob(DD1InstallService::repair)))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    // G: follows the game when it moves, so an older G: is replaced rather than
    // a second one added beside it.
    private static void mapGameDrive(Activity activity, Container container) {
        String drives = DD1Drives.withGame(container.getDrives(),
            DD1GameLocation.gameDir(activity).getPath());
        if (drives.equals(container.getDrives())) return;
        container.setDrives(drives);
        container.saveData();
    }

    // While downloading the panel is one progress bar, so it takes only the
    // height it needs and the log runs from there down.
    private void setInstallPanelFills(boolean fills) {
        View panel = rootView.findViewById(R.id.LLSteamInstall);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)panel.getLayoutParams();
        params.height = fills ? 0 : LinearLayout.LayoutParams.WRAP_CONTENT;
        params.weight = fills ? 1 : 0;
        panel.setLayoutParams(params);
    }

    private void applySignInControls(DD1InstallPhase phase) {
        DD1SignInControls controls = DD1SignInControls.from(phase);
        rootView.findViewById(R.id.BTCancelSignIn)
            .setVisibility(controls.showCancel ? View.VISIBLE : View.GONE);
        rootView.findViewById(R.id.BTSignOutAlways)
            .setVisibility(controls.showSignOut ? View.VISIBLE : View.GONE);
    }

    // The install panel only earns screen space while it has something to show;
    // the log takes the rest.
    private void setInstallPanelVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        rootView.findViewById(R.id.LLSteamInstall).setVisibility(visibility);
    }

    private void setLog(String text) {
        ((TextView)rootView.findViewById(R.id.TVInstallLog)).setText(text);
        // New lines are what matters, so the view follows the tail.
        ScrollView scroller = rootView.findViewById(R.id.SVInstallLog);
        scroller.post(() -> scroller.fullScroll(View.FOCUS_DOWN));
    }

    private void confirmDeleteGame() {
        Activity activity = getActivity();
        if (activity == null) return;
        new AlertDialog.Builder(activity, R.style.DD1Dialog)
            .setTitle(R.string.dd1_delete_game)
            .setMessage(R.string.dd1_delete_game_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dd1_delete_game, (dialog, which) -> deleteGame(activity))
            .show();
    }

    private void deleteGame(Activity activity) {
        // A running download would keep writing into the folder we just removed.
        withService(DD1InstallService::cancel);
        if (!DD1Installer.uninstall(DD1GameLocation.root(activity)))
            Toast.makeText(activity, R.string.dd1_delete_game_failed, Toast.LENGTH_LONG).show();
        refresh();
    }

    // The part being fetched is named in the status box; this is only the figure.
    static String progressSummary(DD1InstallSnapshot snapshot) {
        // The downloader creates a depot's files before it fetches their
        // contents and reports no progress while it does, so there is nothing
        // to put a number on yet. The caller names that stage.
        if (snapshot.totalBytes <= 0) return null;
        return String.format(Locale.US, "%.0f%%",
            snapshot.downloadedBytes * 100.0 / snapshot.totalBytes);
    }

    private void show(int... ids) {
        for (int id : ids) rootView.findViewById(id).setVisibility(View.VISIBLE);
    }

    private Bitmap qr(String value) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 512, 512);
            Bitmap bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
            for (int y = 0; y < 512; y++)
                for (int x = 0; x < 512; x++) bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            return bitmap;
        }
        catch (WriterException error) {
            return null;
        }
    }

    private void withService(java.util.function.Consumer<DD1InstallService> action) {
        if (installService != null) action.accept(installService);
    }

    private void showCodePrompt(DD1SignInCode prompt) {
        TextView hint = rootView.findViewById(R.id.TVSteamCodeHint);
        String asked = prompt.source == DD1SignInCode.Source.EMAIL
            ? getString(R.string.dd1_steam_code_email_hint,
                prompt.emailHint == null ? "" : prompt.emailHint)
            : getString(R.string.dd1_steam_code_app_hint);
        // A rejected code is the one thing a person needs told; without it the same
        // box comes back looking like nothing happened.
        hint.setText(prompt.previousWasWrong
            ? getString(R.string.dd1_steam_code_wrong) + " " + asked : asked);
        EditText code = rootView.findViewById(R.id.ETSteamCode);
        if (prompt.previousWasWrong) code.getText().clear();
        show(R.id.TVSteamCodeHint, R.id.ETSteamCode, R.id.BTSteamCode);
    }

    private void submitCode() {
        if (installService == null) return;
        EditText code = rootView.findViewById(R.id.ETSteamCode);
        if (installService.submitCode(code.getText().toString())) code.getText().clear();
    }

    private void startCredentials() {
        if (installService == null) return;
        EditText account = rootView.findViewById(R.id.ETSteamAccount);
        EditText password = rootView.findViewById(R.id.ETSteamPassword);
        installService.startCredentials(account.getText().toString(), password.getText().toString());
        password.getText().clear();
    }

    // Mods dropped in DD1/mods and files in DD1/overrides are brought in first;
    // Play stays off meanwhile so a second press cannot start the game twice.
    private void launch(Activity activity, Container container, File executable) {
        if (installService == null) {
            start(activity, container, executable);
            return;
        }
        Button primary = rootView == null ? null : rootView.findViewById(R.id.BTPrimaryAction);
        if (primary != null) primary.setEnabled(false);
        installService.prepareForPlay(hasStorage(), () -> {
            if (primary != null) primary.setEnabled(true);
            if (getActivity() == activity && !activity.isFinishing())
                start(activity, container, executable);
        });
    }

    private void start(Activity activity, Container container, File executable) {
        // Before the game gets a chance to write, and only when the saves have
        // moved since the last one, because three slots are all there are.
        if (DD1SavePolicy.worthTaking(activity.getFilesDir()))
            DD1SaveSnapshots.take(activity.getFilesDir(), System.currentTimeMillis());

        File gameDir = DD1GameLocation.gameDir(activity);
        mapGameDrive(activity, container);

        File system32 = new File(container.getRootDir(), ".wine/drive_c/windows/system32");
        File redist = DD1Game.pendingRedistributable(gameDir, system32);

        Intent intent = new Intent(activity, XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("exec_path", redist != null ? redist.getPath() : executable.getPath());
        intent.putExtra(DD1LaunchCover.EXTRA, true);
        // The game reads its data relative to the install root, not the folder
        // holding Darkest.exe, so it exits at once when started from win64.
        activity.startActivity(intent);
    }
}
