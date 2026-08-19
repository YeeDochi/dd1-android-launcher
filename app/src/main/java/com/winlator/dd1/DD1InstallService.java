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

    private volatile DD1InstallSnapshot snapshot = DD1InstallSnapshot.signedOut();
    private volatile Listener listener;
    private volatile DepotDownloader downloader;
    private volatile CountDownLatch completion;
    private volatile boolean cancelled;
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
        steam.startQr();
    }

    public synchronized void download() {
        if (snapshot.phase != DD1InstallPhase.READY_TO_INSTALL || downloader != null) return;
        cancelled = false;
        startService(new Intent(this, DD1InstallService.class));
        startForeground(1, notification(getString(R.string.dd1_install_downloading)));
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

    public void signOut() {
        cancel();
        steam.signOut();
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
                false, 8, 4, 1, true);
            downloader.addListener(new ProgressListener(failure));
            downloader.add(new AppItem(DD1SteamEvents.APP_ID, false, staging.getAbsolutePath(),
                "public", "", false, "windows", false, "64", false, "english",
                false, Collections.emptyList(), Collections.emptyList(), true, false));
            downloader.finishAdding();
            completion.await();
            if (cancelled) return;
            if (failure.get() != null) throw failure.get();

            publish(new DD1InstallSnapshot(DD1InstallPhase.VERIFYING, 0, 0, 0,
                "Verifying downloaded game", null, null, log.visibleLines()));
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
        snapshot = value;
        Listener observer = listener;
        if (observer != null) main.post(() -> observer.onSnapshot(value));
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
        private long lastBytes;
        private long lastTime = System.currentTimeMillis();

        private ProgressListener(AtomicReference<Throwable> failure) {
            this.failure = failure;
        }

        @Override
        public void onStatusUpdate(String message) {
            log.append(message);
            publish(downloadSnapshot(snapshot.downloadedBytes, snapshot.totalBytes,
                snapshot.bytesPerSecond, message, snapshot.currentFile));
        }

        @Override
        public void onFileCompleted(int depotId, String fileName, float percent) {
            log.append(fileName);
            publish(downloadSnapshot(snapshot.downloadedBytes, snapshot.totalBytes,
                snapshot.bytesPerSecond, "Downloading depot " + depotId, fileName));
        }

        @Override
        public void onChunkCompleted(int depotId, float percent, long bytes, long uncompressed) {
            long now = System.currentTimeMillis();
            long elapsed = Math.max(1, now - lastTime);
            long speed = Math.max(0, bytes - lastBytes) * 1000 / elapsed;
            long total = percent > 0 ? (long)(bytes / percent) : 0;
            lastBytes = bytes;
            lastTime = now;
            publish(downloadSnapshot(bytes, total, speed,
                "Downloading depot " + depotId, snapshot.currentFile));
        }

        @Override
        public void onDepotCompleted(int depotId, long bytes, long uncompressed) {
            log.append("Depot " + depotId + " completed");
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
