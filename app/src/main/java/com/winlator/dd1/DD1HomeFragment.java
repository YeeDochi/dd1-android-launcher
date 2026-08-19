package com.winlator.dd1;

import com.winlator.XServerDisplayActivity;
import com.winlator.R;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.GraphicsDrivers;
import com.winlator.contentdialog.AboutDialog;
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
    private DD1InstallService installService;
    private boolean serviceBound;
    private DD1InstallSnapshot installSnapshot = DD1InstallSnapshot.signedOut();
    private final DD1InstallService.Listener installListener = this::renderInstallSnapshot;
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
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dd1_home_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rootView = view;
        view.findViewById(R.id.BTAbout).setOnClickListener(v -> new AboutDialog(getContext()).show());
        view.findViewById(R.id.BTDeleteGame).setOnClickListener(v -> confirmDeleteGame());
        view.findViewById(R.id.BTSteamLogin).setOnClickListener(v -> withService(DD1InstallService::startQr));
        view.findViewById(R.id.BTSteamCredentials).setOnClickListener(v -> startCredentials());
        view.findViewById(R.id.BTDownload).setOnClickListener(v -> withService(DD1InstallService::download));
        view.findViewById(R.id.BTRetryDownload).setOnClickListener(v -> withService(DD1InstallService::download));
        view.findViewById(R.id.BTCancelDownload).setOnClickListener(v -> withService(DD1InstallService::cancel));
        view.findViewById(R.id.BTSteamSignOut).setOnClickListener(v -> withService(DD1InstallService::signOut));
    }

    @Override
    public void onStart() {
        super.onStart();
        if (DD1Game.findExecutable(requireContext().getFilesDir()) != null) return;
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
    }

    @Override
    public void onPause() {
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    private void refresh() {
        if (rootView == null) return;

        Activity activity = getActivity();
        File executable = DD1Game.findExecutable(activity.getFilesDir());
        ContainerManager manager = new ContainerManager(activity);
        Container container = manager.getContainers().isEmpty() ? null : manager.getContainers().get(0);
        boolean runtimeReady = RootFS.find(activity).isValid();

        if (runtimeReady && container == null && !creatingProfile && !profileCreationFailed) {
            creatingProfile = true;
            JSONObject data = new JSONObject(DD1ProfileConfig.create(
                GraphicsDrivers.getDefaultDriver(activity), Container.getFallbackCPUList()));
            manager.createContainerAsync(data, created -> {
                creatingProfile = false;
                profileCreationFailed = created == null;
                refresh();
            });
        }

        DD1HomeState state = DD1HomeState.from(runtimeReady, executable != null, container != null);

        TextView status = rootView.findViewById(R.id.TVStatus);
        Button primary = rootView.findViewById(R.id.BTPrimaryAction);
        primary.setVisibility(View.VISIBLE);
        setInstallPanelVisible(false);
        rootView.findViewById(R.id.BTDeleteGame)
            .setVisibility(executable != null ? View.VISIBLE : View.GONE);
        primary.setEnabled(state == DD1HomeState.READY || state == DD1HomeState.PROFILE_MISSING);

        switch (state) {
            case READY:
                status.setText(R.string.dd1_status_ready);
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
        // The log stays on screen in every state; save synchronisation will write
        // into it too.
        setLog(TextUtils.join("\n", snapshot.logLines));
        if (snapshot.phase == DD1InstallPhase.READY) {
            refresh();
            return;
        }
        if (DD1Game.findExecutable(requireContext().getFilesDir()) != null) {
            setInstallPanelVisible(false);
            return;
        }
        rootView.findViewById(R.id.BTPrimaryAction).setVisibility(View.GONE);
        setInstallPanelVisible(true);
        ((TextView)rootView.findViewById(R.id.TVStatus)).setText(snapshot.message);

        int[] controls = {R.id.IVSteamQr, R.id.BTSteamLogin, R.id.ETSteamAccount,
            R.id.ETSteamPassword, R.id.BTSteamCredentials, R.id.BTDownload,
            R.id.PBDownload, R.id.TVDownloadFile,
            R.id.BTCancelDownload, R.id.BTRetryDownload, R.id.BTSteamSignOut};
        for (int id : controls) rootView.findViewById(id).setVisibility(View.GONE);

        if (snapshot.phase == DD1InstallPhase.SIGNED_OUT) {
            show(R.id.BTSteamLogin, R.id.ETSteamAccount, R.id.ETSteamPassword,
                R.id.BTSteamCredentials);
        }
        else if (snapshot.phase == DD1InstallPhase.AUTHENTICATING) {
            show(R.id.BTSteamSignOut);
            if (snapshot.challengeUrl != null) {
                ImageView qr = rootView.findViewById(R.id.IVSteamQr);
                qr.setImageBitmap(qr(snapshot.challengeUrl));
                qr.setVisibility(View.VISIBLE);
            }
        }
        else if (snapshot.phase == DD1InstallPhase.READY_TO_INSTALL) {
            show(R.id.BTDownload, R.id.BTSteamSignOut);
        }
        else if (snapshot.phase == DD1InstallPhase.DOWNLOADING ||
                snapshot.phase == DD1InstallPhase.VERIFYING) {
            show(R.id.PBDownload, R.id.TVDownloadFile, R.id.BTCancelDownload);
            ProgressBar progress = rootView.findViewById(R.id.PBDownload);
            progress.setIndeterminate(snapshot.totalBytes <= 0);
            if (snapshot.totalBytes > 0)
                progress.setProgress((int)Math.min(1000, snapshot.downloadedBytes * 1000 / snapshot.totalBytes));
            ((TextView)rootView.findViewById(R.id.TVDownloadFile))
                .setText(progressSummary(snapshot) + "\n" + snapshot.currentFile);
        }
        else if (snapshot.phase == DD1InstallPhase.NOT_OWNED) {
            show(R.id.BTSteamSignOut);
        }
        else if (snapshot.phase == DD1InstallPhase.ERROR) {
            show(R.id.BTSteamSignOut);
            if (installService != null && installService.canDownload()) show(R.id.BTRetryDownload);
            else show(R.id.BTSteamLogin);
        }
    }

    // Fixed-height log with its own scroller: without this the growing text
    // pushes the rest of the pane around on every update.
    // The install panel only earns screen space while it has something to show;
    // the log takes the rest.
    private void setInstallPanelVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        rootView.findViewById(R.id.SVSteamInstall).setVisibility(visibility);
        rootView.findViewById(R.id.LLSteamInstall).setVisibility(visibility);
    }

    private void setLog(String text) {
        TextView log = rootView.findViewById(R.id.TVInstallLog);
        if (log.getMovementMethod() == null) log.setMovementMethod(new ScrollingMovementMethod());
        log.setText(text);
        log.post(() -> {
            int overflow = log.getLayout() == null ? 0
                : log.getLayout().getLineBottom(log.getLineCount() - 1)
                    - (log.getHeight() - log.getPaddingTop() - log.getPaddingBottom());
            log.scrollTo(0, Math.max(0, overflow));
        });
    }

    private void confirmDeleteGame() {
        Activity activity = getActivity();
        if (activity == null) return;
        new AlertDialog.Builder(activity)
            .setTitle(R.string.dd1_delete_game)
            .setMessage(R.string.dd1_delete_game_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dd1_delete_game, (dialog, which) -> deleteGame(activity))
            .show();
    }

    private void deleteGame(Activity activity) {
        if (!DD1Installer.uninstall(activity.getFilesDir()))
            Toast.makeText(activity, R.string.dd1_delete_game_failed, Toast.LENGTH_LONG).show();
        refresh();
    }

    // The downloader only reports a percentage reliably, so that is all this shows.
    static String progressSummary(DD1InstallSnapshot snapshot) {
        if (snapshot.totalBytes <= 0) return "";
        return String.format(Locale.US, "%.1f%%", snapshot.downloadedBytes * 100.0 / snapshot.totalBytes);
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

    private void startCredentials() {
        if (installService == null) return;
        EditText account = rootView.findViewById(R.id.ETSteamAccount);
        EditText password = rootView.findViewById(R.id.ETSteamPassword);
        installService.startCredentials(account.getText().toString(), password.getText().toString());
        password.getText().clear();
    }

    private void launch(Activity activity, Container container, File executable) {
        File gameDir = new File(activity.getFilesDir(), "game");
        if (!container.getDrives().contains(gameDir.getPath())) {
            container.setDrives(container.getDrives()+"G:"+gameDir.getPath());
            container.saveData();
        }

        File system32 = new File(container.getRootDir(), ".wine/drive_c/windows/system32");
        File redist = DD1Game.pendingRedistributable(gameDir, system32);

        Intent intent = new Intent(activity, XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("exec_path", redist != null ? redist.getPath() : executable.getPath());
        // The game reads its data relative to the install root, not the folder
        // holding Darkest.exe, so it exits at once when started from win64.
        activity.startActivity(intent);
    }
}
