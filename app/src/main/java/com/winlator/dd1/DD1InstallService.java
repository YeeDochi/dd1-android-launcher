package com.winlator.dd1;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
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
    private volatile boolean deliveryPending;
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
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(DD1Locale.wrap(base));
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
        publish(downloadSnapshot(0, 0, 0, getString(R.string.dd1_state_starting), null));
        worker.execute(this::runDownload);
    }

    public void cancel() {
        cancelled = true;
        DepotDownloader active;
        CountDownLatch waiting;
        synchronized (this) {
            active = downloader;
            waiting = completion;
        }
        // Releasing the worker is what stops the download; the button must not
        // wait on it.
        if (waiting != null) waiting.countDown();
        publish(stoppedSnapshot("Download cancelled"));
        stopForeground(true);
        stopSelf();
        // Closing shuts down the downloader's connections and blocks, so it
        // never runs on the thread that pressed the button.
        if (active != null) new Thread(active::close, "dd1-cancel").start();
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
                getString(R.string.dd1_state_verifying), null, null, log.visibleLines()));
            DD1Installer.markDownloadComplete(getFilesDir());
            DlcInstallFilter.apply(staging, dlcSelection().selected());
            DD1Installer.Result result = DD1Installer.activate(getFilesDir());
            if (!result.success) throw new IllegalStateException(result.error);
            log.append("Darkest Dungeon installation ready");
            publish(new DD1InstallSnapshot(DD1InstallPhase.READY, 0, 0, 0,
                getString(R.string.dd1_state_ready_to_play), null, null, log.visibleLines()));
        }
        catch (Throwable error) {
            if (!cancelled) publish(errorSnapshot(error.getMessage() == null
                ? error.getClass().getSimpleName() : error.getMessage()));
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

    // Stopping a download is a choice, not a failure: the account still owns the
    // game, so the screen goes back to offering it with the DLC list.
    private DD1InstallSnapshot stoppedSnapshot(String detail) {
        if (!ownsGame) return errorSnapshot(detail);
        log.append(detail);
        return new DD1InstallSnapshot(DD1InstallPhase.READY_TO_INSTALL, 0, 0, 0,
            getString(R.string.dd1_state_ready_to_install), null, null, log.visibleLines());
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
        deliver(value.phase == DD1InstallPhase.DOWNLOADING);
    }

    // Chunk callbacks arrive far faster than the screen can draw, and each one
    // relays a whole log into a monospace TextView. Delivering every one buries
    // the main thread in layout passes and the window stops answering touches,
    // so a burst is coalesced into the latest state.
    private void deliver(boolean coalesce) {
        Listener observer = listener;
        if (observer == null) return;
        if (!coalesce) {
            main.post(() -> observer.onSnapshot(snapshot));
            return;
        }
        if (deliveryPending) return;
        deliveryPending = true;
        main.postDelayed(() -> {
            deliveryPending = false;
            Listener current = listener;
            if (current != null) current.onSnapshot(snapshot);
        }, 200);
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
            progress.onStatus(message);
            publish(new DD1InstallSnapshot(DD1InstallPhase.DOWNLOADING,
                snapshot.downloadedBytes, snapshot.totalBytes, 0,
                describe(), snapshot.currentFile, null, log.visibleLines()));
        }

        @Override
        public void onFileCompleted(int depotId, String fileName, float percent) {
            log.append(fileName);
            progress.onDepotSeen(depotId);
            publish(downloadSnapshot(describe(), fileName));
        }

        @Override
        public void onChunkCompleted(int depotId, float percent, long bytes, long uncompressed) {
            progress.onDepotProgress(depotId, percent);
            publish(downloadSnapshot(describe(), snapshot.currentFile));
        }

        @Override
        public void onDepotCompleted(int depotId, long bytes, long uncompressed) {
            log.append("Depot " + depotId + " completed");
            progress.onDepotFinished(depotId);
            publish(downloadSnapshot(describe(), snapshot.currentFile));
        }

        // The status text comes from the downloader itself, so it says what is
        // actually happening: allocating, validating or fetching.
        private DD1InstallSnapshot downloadSnapshot(String message, String file) {
            double percent = progress.currentPercent();
            return new DD1InstallSnapshot(DD1InstallPhase.DOWNLOADING,
                percent < 0 ? 0 : (long)(percent * 100), percent < 0 ? 0 : 10000L, 0,
                message, file == null ? "" : file, null, log.visibleLines());
        }

        // The left box says which part is in hand; the log carries the detail.
        private String describe() {
            return getString(R.string.dd1_state_part, progress.part());
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
