package com.winlator.dd1;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import com.winlator.R;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import in.dragonbra.javasteam.depotdownloader.DepotDownloader;
import in.dragonbra.javasteam.depotdownloader.IDownloadListener;
import in.dragonbra.javasteam.depotdownloader.data.AppItem;
import in.dragonbra.javasteam.depotdownloader.data.DownloadItem;

public final class DD1InstallService extends Service {
    public interface Listener {
        void onSnapshot(DD1InstallSnapshot snapshot);
    }

    public final class LocalBinder extends Binder {
        public DD1InstallService getService() {
            return DD1InstallService.this;
        }
    }

    private static final String CHANNEL = "dd1_install";
    private final LocalBinder binder = new LocalBinder();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final DD1InstallLog log = new DD1InstallLog(1000);

    private volatile DD1InstallSnapshot snapshot = DD1InstallSnapshot.restoring();
    private volatile Listener listener;
    private volatile DepotDownloader downloader;
    private volatile CountDownLatch completion;
    private volatile boolean cancelled;
    private volatile boolean ownsGame;
    private DD1SteamSession steam;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        steam = new DD1SteamSession(this, this::publish);
        steam.restore();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    public void observe(Listener listener) {
        this.listener = listener;
        listener.onSnapshot(snapshot);
    }

    public void removeObserver(Listener listener) {
        if (this.listener == listener) this.listener = null;
    }

    public void startQr() {
        keepAlive(R.string.dd1_install_signing_in);
        steam.startQr();
    }

    public void startCredentials(String account, String password) {
        keepAlive(R.string.dd1_install_signing_in);
        steam.startCredentials(account, password);
    }

    // Steam Mobile approval sends the user to another app; without a started
    // foreground service the unbind on onStop() would destroy the session.
    private void keepAlive(int message) {
        startService(new Intent(this, DD1InstallService.class));
        startForeground(1, notification(getString(message)));
    }

    public synchronized void download() {
        if (!ownsGame || downloader != null) return;
        cancelled = false;
        keepAlive(R.string.dd1_install_downloading);
        publish(downloadSnapshot(0, 0, 0, "Starting download", null));
        worker.execute(this::runDownload);
    }

    public synchronized void cancel() {
        cancelled = true;
        if (downloader != null) downloader.close();
        if (completion != null) completion.countDown();
        publish(errorSnapshot("Download cancelled"));
        stopForeground(true);
        stopSelf();
    }

    // Closing the Steam connection blocks, so it never runs on the caller's thread.
    public void signOut() {
        cancel();
        worker.execute(steam::signOut);
    }

    public java.util.List<Integer> ownedDlc() {
        return steam.ownedDlc();
    }

    public DlcSelection dlcSelection() {
        return DlcSelection.parse(preferences().getString("dlc_excluded", null), ownedDlc());
    }

    public void saveDlcSelection(DlcSelection selection) {
        preferences().edit().putString("dlc_excluded", selection.serialize()).apply();
    }

    private android.content.SharedPreferences preferences() {
        return getSharedPreferences("dd1", MODE_PRIVATE);
    }

    public boolean canDownload() {
        return ownsGame;
    }

    @Override
    public void onDestroy() {
        cancel();
        steam.close();
        worker.shutdownNow();
        super.onDestroy();
    }

    private void runDownload() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        completion = new CountDownLatch(1);
        File staging = new File(getFilesDir(), "staging/game");
        staging.mkdirs();
        try {
            downloader = new DepotDownloader(steam.client(), steam.licenses(), false,
                false, 2, 1, 1, true);
            downloader.addListener(new ProgressListener(failure));
            // The second-to-last flag is verify: with it set the downloader only
            // checks the files it allocated and never fetches their contents.
            downloader.add(new AppItem(DD1SteamEvents.APP_ID, false, staging.getAbsolutePath(),
                "public", "", false, "windows", false, "64", false, "english",
                false, Collections.emptyList(), Collections.emptyList(), false, false));
            downloader.finishAdding();
            completion.await();
            if (cancelled) return;
            if (failure.get() != null) throw failure.get();

            publish(new DD1InstallSnapshot(DD1InstallPhase.VERIFYING, 0, 0, 0,
                "Verifying downloaded game", null, null, log.visibleLines()));
            DlcInstallFilter.apply(staging, dlcSelection().selected());
            DD1Installer.Result result = DD1Installer.activate(getFilesDir());
            if (!result.success) throw new IllegalStateException(result.error);
            log.append("Darkest Dungeon installation ready");
            publish(new DD1InstallSnapshot(DD1InstallPhase.READY, 0, 0, 0,
                "Ready to play", null, null, log.visibleLines()));
        }
        catch (Throwable error) {
            if (!cancelled) publish(errorSnapshot(error.getClass().getSimpleName()));
        }
        finally {
            if (downloader != null) downloader.close();
            downloader = null;
            completion = null;
            stopForeground(true);
            stopSelf();
        }
    }

    private DD1InstallSnapshot downloadSnapshot(long bytes, long total, long speed,
            String message, String file) {
        return new DD1InstallSnapshot(DD1InstallPhase.DOWNLOADING, bytes, total, speed,
            message, file, null, log.visibleLines());
    }

    private DD1InstallSnapshot errorSnapshot(String detail) {
        log.append(detail);
        return new DD1InstallSnapshot(DD1InstallPhase.ERROR, 0, 0, 0,
            detail, null, null, log.visibleLines());
    }

    private void publish(DD1InstallSnapshot value) {
        if (value.phase == DD1InstallPhase.READY_TO_INSTALL) ownsGame = true;
        else if (value.phase == DD1InstallPhase.SIGNED_OUT || value.phase == DD1InstallPhase.NOT_OWNED)
            ownsGame = false;
        snapshot = value;
        if (isIdle(value.phase)) {
            stopForeground(true);
            stopSelf();
        }
        Listener observer = listener;
        if (observer != null) main.post(() -> observer.onSnapshot(value));
    }

    private static boolean isIdle(DD1InstallPhase phase) {
        return phase == DD1InstallPhase.SIGNED_OUT || phase == DD1InstallPhase.NOT_OWNED
            || phase == DD1InstallPhase.READY_TO_INSTALL || phase == DD1InstallPhase.READY
            || phase == DD1InstallPhase.ERROR;
    }

    private Notification notification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .build();
    }

    private void createNotificationChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
            getString(R.string.dd1_install_channel), NotificationManager.IMPORTANCE_LOW));
    }

    private final class ProgressListener implements IDownloadListener {
        private final AtomicReference<Throwable> failure;
        private final DownloadProgress progress = new DownloadProgress();

        private ProgressListener(AtomicReference<Throwable> failure) {
            this.failure = failure;
        }

        @Override
        public void onStatusUpdate(String message) {
            log.append(message);
            publish(new DD1InstallSnapshot(DD1InstallPhase.DOWNLOADING,
                snapshot.downloadedBytes, snapshot.totalBytes, 0,
                message, snapshot.currentFile, null, log.visibleLines()));
        }

        @Override
        public void onFileCompleted(int depotId, String fileName, float percent) {
            log.append(fileName);
            progress.onDepotProgress(depotId, percent);
            publish(downloadSnapshot(depotId, fileName));
        }

        @Override
        public void onChunkCompleted(int depotId, float percent, long bytes, long uncompressed) {
            progress.onDepotProgress(depotId, percent);
            publish(downloadSnapshot(depotId, snapshot.currentFile));
        }

        @Override
        public void onDepotCompleted(int depotId, long bytes, long uncompressed) {
            log.append("Depot " + depotId + " completed");
            progress.onDepotFinished(depotId);
            publish(downloadSnapshot(depotId, snapshot.currentFile));
        }

        private DD1InstallSnapshot downloadSnapshot(int depotId, String file) {
            double overall = progress.overall();
            return new DD1InstallSnapshot(DD1InstallPhase.DOWNLOADING,
                overall < 0 ? 0 : (long)(overall * 100), overall < 0 ? 0 : 10000L,
                progress.currentIndex(), progress.totalKnown()
                    ? "Part " + progress.currentIndex() + " of " + progress.depotCount()
                    : "Part " + progress.currentIndex(),
                file == null ? "" : file, null, log.visibleLines());
        }

        @Override
        public void onDownloadCompleted(DownloadItem item) {
            completion.countDown();
        }

        @Override
        public void onDownloadFailed(DownloadItem item, Throwable error) {
            failure.set(error);
            completion.countDown();
        }
    }
}
