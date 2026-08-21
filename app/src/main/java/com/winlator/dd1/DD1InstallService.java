package com.winlator.dd1;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    public interface WorkshopListener {
        void onSnapshot(DD1WorkshopSnapshot snapshot);
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
    private static final int NO_PROGRESS = Integer.MIN_VALUE;
    private String lastNotification;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final DD1InstallLog log = new DD1InstallLog(1000);
    private final DD1InstallLog workshopLog = new DD1InstallLog(300);

    private volatile DD1InstallSnapshot snapshot = DD1InstallSnapshot.restoring();
    private volatile Listener listener;
    private volatile WorkshopListener workshopListener;
    private volatile DD1WorkshopSnapshot workshopSnapshot = DD1WorkshopSnapshot.loading();
    private volatile List<ModSyncPlan.Subscribed> workshopSubscriptions = Collections.emptyList();
    private volatile DepotDownloader downloader;
    private volatile CountDownLatch completion;
    private volatile boolean cancelled;
    private volatile boolean ownsGame;
    private volatile boolean downloading;
    private volatile long lastHeard;
    private DD1SteamSession steam;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        steam = new DD1SteamSession(this, this::publishFromSteam);
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

    public void observeWorkshop(WorkshopListener listener) {
        workshopListener = listener;
        listener.onSnapshot(workshopSnapshot);
    }

    public void removeWorkshopObserver(WorkshopListener listener) {
        if (workshopListener == listener) workshopListener = null;
    }

    public void refreshWorkshop() {
        if (!ownsGame) {
            if (snapshot.phase == DD1InstallPhase.RESTORING
                    || snapshot.phase == DD1InstallPhase.AUTHENTICATING) {
                publishWorkshop(DD1WorkshopSnapshot.loading());
                return;
            }
            publishWorkshop(DD1WorkshopSnapshot.error(getString(R.string.dd1_workshop_sign_in)));
            return;
        }
        if (workshopSnapshot.phase == DD1WorkshopSnapshot.Phase.SYNCING) return;
        publishWorkshop(DD1WorkshopSnapshot.loading());
        steam.workshop().whenComplete((subscriptions, error) -> worker.execute(() -> {
            if (error != null) {
                publishWorkshop(DD1WorkshopSnapshot.error(reason(error)));
                return;
            }
            workshopSubscriptions = Collections.unmodifiableList(new ArrayList<>(subscriptions));
            publishWorkshop(DD1WorkshopSnapshot.ready(workshopSubscriptions,
                DD1Workshop.scan(getFilesDir())));
        }));
    }

    public synchronized void syncWorkshop() {
        if (downloader != null || !workshopSnapshot.syncable()) return;
        List<ModSyncPlan.Subscribed> items = new ArrayList<>(workshopSnapshot.syncItems());
        cancelled = false;
        downloading = true;
        keepAlive(R.string.dd1_install_downloading);
        worker.execute(() -> runWorkshop(items));
    }

    public void deleteMod(String directoryName) {
        if (downloader != null) return;
        worker.execute(() -> {
            try {
                DD1Workshop.delete(getFilesDir(), directoryName);
                workshopLog.append("Deleted " + directoryName);
                publishWorkshop(DD1WorkshopSnapshot.ready(workshopSubscriptions,
                    DD1Workshop.scan(getFilesDir())));
            }
            catch (Exception error) {
                publishWorkshop(DD1WorkshopSnapshot.error(reason(error)));
            }
        });
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
        begin();
        worker.execute(() -> runDownload(Collections.emptyList(), null));
    }

    // Adding or updating one DLC on a game that is already installed. Only the
    // depots asked for are fetched, and what arrives is merged into the install
    // rather than replacing it.
    public synchronized void downloadDlc(java.util.Collection<Integer> appIds) {
        if (!ownsGame || downloader != null || appIds.isEmpty()) return;
        DD1DepotCatalog catalog = steam.catalog();
        java.util.List<Integer> depots = new java.util.ArrayList<>();
        java.util.Map<Integer, String> versions = new java.util.LinkedHashMap<>();
        for (int appId : appIds) {
            int depot = catalog.depotOf(appId);
            if (depot <= 0) continue;
            depots.add(depot);
            versions.put(appId, catalog.manifestOf(appId));
        }
        if (depots.isEmpty()) {
            publish(errorSnapshot(getString(R.string.dd1_dlc_no_depot)));
            return;
        }
        begin();
        worker.execute(() -> runDownload(depots, versions));
    }

    private void begin() {
        cancelled = false;
        downloading = true;
        keepAlive(R.string.dd1_install_downloading);
        publish(downloadSnapshot(0, 0, 0, getString(R.string.dd1_state_starting), null));
    }

    public void cancel() {
        cancelled = true;
        downloading = false;
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

    public DD1DepotCatalog depotCatalog() {
        return steam.catalog();
    }

    public DD1CloudSaves cloudSaves() {
        return new DD1CloudSaves(steam.cloud());
    }

    private void runWorkshop(List<ModSyncPlan.Subscribed> items) {
        DD1WorkshopSnapshot base = workshopSnapshot;
        try {
            int done = 0;
            for (ModSyncPlan.Subscribed item : items) {
                AtomicReference<Throwable> failure = new AtomicReference<>();
                completion = new CountDownLatch(1);
                File staging = DD1Workshop.staging(getFilesDir(), item.publishedFileId);
                workshopLog.append("Downloading " + item.title);
                publishWorkshop(base.syncing(item.title, done * 100 / items.size(),
                    workshopLog.visibleLines()));
                try {
                    downloader = new DepotDownloader(steam.client(), steam.licenses(), false,
                        false, 2, 1, 1, true);
                    downloader.addListener(new WorkshopProgressListener(base, item, done,
                        items.size(), failure));
                    downloader.add(DD1WorkshopCatalog.download(item.publishedFileId,
                        staging.getAbsolutePath()));
                    downloader.finishAdding();
                    awaitCompletion();
                    if (cancelled) return;
                    if (failure.get() != null) throw failure.get();
                    DD1Workshop.promote(getFilesDir(), item.publishedFileId, item.updatedAt,
                        item.title);
                    workshopLog.append("Installed " + item.title);
                }
                catch (Throwable error) {
                    workshopLog.append(item.title + ": " + reason(error));
                }
                finally {
                    if (downloader != null) downloader.close();
                    downloader = null;
                    completion = null;
                }
                done++;
            }
            publishWorkshop(DD1WorkshopSnapshot.ready(workshopSubscriptions,
                DD1Workshop.scan(getFilesDir())));
        }
        catch (Throwable error) {
            publishWorkshop(DD1WorkshopSnapshot.error(reason(error)));
        }
        finally {
            downloading = false;
            stopForeground(true);
            stopSelf();
        }
    }

    private void publishWorkshop(DD1WorkshopSnapshot value) {
        workshopSnapshot = value;
        main.post(() -> {
            WorkshopListener current = workshopListener;
            if (current != null) current.onSnapshot(workshopSnapshot);
        });
    }

    private static String reason(Throwable error) {
        while (error.getCause() != null) error = error.getCause();
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getSimpleName() : message;
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

    private void runDownload(java.util.List<Integer> depots,
            java.util.Map<Integer, String> dlcVersions) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        completion = new CountDownLatch(1);
        File staging = DD1Installer.beginDownload(getFilesDir());
        try {
            downloader = new DepotDownloader(steam.client(), steam.licenses(), false,
                false, 2, 1, 1, true);
            downloader.addListener(new ProgressListener(failure));
            // The second-to-last flag is verify: with it set the downloader only
            // checks the files it allocated and never fetches their contents.
            downloader.add(new AppItem(DD1SteamEvents.APP_ID, false, staging.getAbsolutePath(),
                "public", "", false, "windows", false, "64", false, "english",
                false, depots, Collections.emptyList(), false, false));
            downloader.finishAdding();
            awaitCompletion();
            if (cancelled) return;
            if (failure.get() != null) throw failure.get();

            publish(new DD1InstallSnapshot(DD1InstallPhase.VERIFYING, 0, 0, 0,
                getString(R.string.dd1_state_verifying), null, null, log.visibleLines()));
            DD1Installer.Result result;
            if (dlcVersions == null) {
                DD1Installer.markDownloadComplete(getFilesDir());
                DlcInstallFilter.apply(staging, dlcSelection().selected());
                result = DD1Installer.activate(getFilesDir());
            }
            else result = DD1Installer.merge(getFilesDir(), dlcVersions);
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
            downloading = false;
            if (downloader != null) downloader.close();
            downloader = null;
            completion = null;
            stopForeground(true);
            stopSelf();
        }
    }

    // Steam hands out a list of content servers and some of them accept the
    // connection and then never answer. The downloader has no read timeout, so a
    // manifest request that goes quiet leaves the whole job waiting forever and
    // the screen saying it is still preparing. A retry draws different servers,
    // so the useful thing to do is give up and say so.
    private static final long STALL_MILLIS = 3 * 60 * 1000L;

    private void awaitCompletion() throws InterruptedException {
        lastHeard = System.currentTimeMillis();
        while (!completion.await(20, java.util.concurrent.TimeUnit.SECONDS)) {
            if (cancelled) return;
            if (System.currentTimeMillis() - lastHeard > STALL_MILLIS)
                throw new IllegalStateException(getString(R.string.dd1_state_stalled));
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

    // Steam resends its license list whenever it pleases, and the ownership sweep
    // that follows ends on READY_TO_INSTALL. Taken at face value during a download
    // that counts as idle, so publish() stopped the service, onDestroy() called
    // cancel(), and the download died at whatever byte it had reached with the
    // screen back on the download button and nothing said about it. Only news
    // that actually ends the session gets through while bytes are moving.
    private void publishFromSteam(DD1InstallSnapshot value) {
        if (downloading && !value.phase.interruptsDownload()) return;
        publish(value);
        if (value.phase == DD1InstallPhase.READY_TO_INSTALL
                && workshopListener != null
                && workshopSnapshot.phase == DD1WorkshopSnapshot.Phase.LOADING)
            refreshWorkshop();
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
        updateNotification(value);
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
        return notification(text, NO_PROGRESS);
    }

    // A download that runs for an hour is watched from the shade more than from
    // the screen, so it carries the same bar.
    private Notification notification(String text, int percent) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            // One UI folds a title that repeats the app label into the header and
            // drops the text line with it, so the game's name goes here instead.
            .setContentTitle(getString(R.string.dd1_home_title))
            .setContentText(text)
            .setContentIntent(PendingIntent.getActivity(this, 0,
                new Intent(this, DD1Activity.class), PendingIntent.FLAG_UPDATE_CURRENT))
            .setOngoing(true);
        if (percent != NO_PROGRESS) builder.setProgress(100, Math.max(0, percent), percent < 0);
        return builder.build();
    }

    // Redrawing the shade for every chunk is as wasteful as redrawing the
    // screen, so it only happens when the figure people read actually moves.
    private void updateNotification(DD1InstallSnapshot value) {
        if (value.phase != DD1InstallPhase.DOWNLOADING
                && value.phase != DD1InstallPhase.VERIFYING) return;
        int percent = value.totalBytes > 0
            ? (int)(value.downloadedBytes * 100 / value.totalBytes) : -1;
        // The bar alone leaves no way to tell a slow download from a stuck one,
        // and the allocation stage has no figure at all, so the shade carries the
        // same words as the screen.
        String text = value.message;
        if (value.phase == DD1InstallPhase.DOWNLOADING) {
            String detail = DD1HomeFragment.progressSummary(value);
            text += " · " + (detail != null ? detail : getString(R.string.dd1_state_preparing));
        }
        if (text.equals(lastNotification)) return;
        lastNotification = text;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(1, notification(text, percent));
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
            lastHeard = System.currentTimeMillis();
            log.append(message);
            progress.onStatus(message);
            publish(new DD1InstallSnapshot(DD1InstallPhase.DOWNLOADING,
                snapshot.downloadedBytes, snapshot.totalBytes, 0,
                describe(), snapshot.currentFile, null, log.visibleLines()));
        }

        @Override
        public void onFileCompleted(int depotId, String fileName, float percent) {
            lastHeard = System.currentTimeMillis();
            log.append(fileName);
            progress.onDepotSeen(depotId);
            publish(downloadSnapshot(describe(), fileName));
        }

        @Override
        public void onChunkCompleted(int depotId, float percent, long bytes, long uncompressed) {
            lastHeard = System.currentTimeMillis();
            progress.onDepotProgress(depotId, percent, bytes);
            publish(downloadSnapshot(describe(), snapshot.currentFile));
        }

        @Override
        public void onDepotCompleted(int depotId, long bytes, long uncompressed) {
            log.append("Depot " + depotId + " completed");
            progress.onDepotFinished(depotId, bytes);
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

    private final class WorkshopProgressListener implements IDownloadListener {
        private final DD1WorkshopSnapshot base;
        private final ModSyncPlan.Subscribed item;
        private final int done;
        private final int total;
        private final AtomicReference<Throwable> failure;

        WorkshopProgressListener(DD1WorkshopSnapshot base, ModSyncPlan.Subscribed item,
                int done, int total, AtomicReference<Throwable> failure) {
            this.base = base;
            this.item = item;
            this.done = done;
            this.total = total;
            this.failure = failure;
        }

        @Override
        public void onStatusUpdate(String message) {
            lastHeard = System.currentTimeMillis();
            workshopLog.append(message);
            publishWorkshop(base.syncing(item.title, done * 100 / total,
                workshopLog.visibleLines()));
        }

        @Override
        public void onChunkCompleted(int depotId, float percent, long bytes, long uncompressed) {
            lastHeard = System.currentTimeMillis();
            int overall = (int)((done + Math.max(0, Math.min(100, percent)) / 100f)
                * 100 / total);
            publishWorkshop(base.syncing(item.title, overall, workshopLog.visibleLines()));
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
