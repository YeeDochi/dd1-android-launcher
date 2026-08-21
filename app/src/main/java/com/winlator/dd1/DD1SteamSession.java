package com.winlator.dd1;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.steam.authentication.AuthPollResult;
import in.dragonbra.javasteam.steam.authentication.AuthSessionDetails;
import in.dragonbra.javasteam.steam.authentication.CredentialsAuthSession;
import in.dragonbra.javasteam.steam.authentication.IAuthenticator;
import in.dragonbra.javasteam.steam.authentication.QrAuthSession;
import in.dragonbra.javasteam.steam.handlers.steamapps.License;
import in.dragonbra.javasteam.steam.handlers.steamapps.PICSProductInfo;
import in.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest;
import in.dragonbra.javasteam.steam.handlers.steamapps.SteamApps;
import in.dragonbra.javasteam.steam.handlers.steamapps.callback.LicenseListCallback;
import in.dragonbra.javasteam.steam.handlers.steamapps.callback.PICSProductInfoCallback;
import in.dragonbra.javasteam.steam.handlers.steamunifiedmessages.SteamUnifiedMessages;
import in.dragonbra.javasteam.steam.handlers.steamunifiedmessages.callback.ServiceMethodResponse;
import in.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails;
import in.dragonbra.javasteam.steam.handlers.steamuser.SteamUser;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback;
import in.dragonbra.javasteam.steam.steamclient.SteamClient;
import in.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager;
import in.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback;
import in.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback;
import in.dragonbra.javasteam.types.AsyncJobMultiple;
import in.dragonbra.javasteam.types.KeyValue;
import in.dragonbra.javasteam.rpc.service.PublishedFile;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_GetUserFiles_Request;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_GetUserFiles_Response;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_GetDetails_Response;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_QueryFiles_Response;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_Subscribe_Response;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_Unsubscribe_Response;

public final class DD1SteamSession implements Closeable {
    public interface Listener {
        void onSnapshot(DD1InstallSnapshot snapshot);
    }

    private final SteamClient client = new SteamClient();
    private final CallbackManager callbacks = new CallbackManager(client);
    private final SteamUser user = requireHandler(SteamUser.class);
    private final SteamApps apps = requireHandler(SteamApps.class);
    private final in.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud cloud =
        requireHandler(in.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud.class);
    private final PublishedFile publishedFiles =
        requireHandler(SteamUnifiedMessages.class).createService(PublishedFile.class);
    // SteamCloud.commitFileUpload answers a bare boolean and drops the result
    // Steam sent with it, so the commit goes through the service directly.
    private final in.dragonbra.javasteam.rpc.service.Cloud cloudService =
        requireHandler(SteamUnifiedMessages.class)
            .createService(in.dragonbra.javasteam.rpc.service.Cloud.class);
    private final SteamTokenStore tokens;
    private final DD1SteamEvents events;
    private final Listener listener;
    private volatile DD1DepotCatalog catalog = DD1DepotCatalog.empty();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService operations = Executors.newSingleThreadExecutor();
    private final ExecutorService callbackLoop = Executors.newSingleThreadExecutor();
    private final List<Closeable> subscriptions = new ArrayList<>();

    private volatile List<License> licenses = Collections.emptyList();
    private volatile boolean closed;
    private volatile boolean qrRequested;
    private volatile boolean expectedDisconnect;
    private volatile boolean signingOut;
    private volatile boolean restoring;
    private volatile SteamTokenStore.Session savedSession;
    private volatile int reconnects;
    private volatile String credentialAccount;
    private volatile String credentialPassword;

    public DD1SteamSession(Context context, Listener listener) {
        this.events = new DD1SteamEvents(context::getString);
        tokens = new SteamTokenStore(context);
        this.listener = listener;
        subscriptions.add(callbacks.subscribe(ConnectedCallback.class, ignored -> onConnected()));
        subscriptions.add(callbacks.subscribe(DisconnectedCallback.class, this::onDisconnected));
        subscriptions.add(callbacks.subscribe(LoggedOnCallback.class, this::onLoggedOn));
        subscriptions.add(callbacks.subscribe(LicenseListCallback.class, this::onLicenses));
        callbackLoop.execute(this::runCallbacks);
    }

    public void startQr() {
        signingOut = false;
        qrRequested = true;
        savedSession = null;
        reconnects = 0;
        publish(events.authStarted(null));
        connect();
    }

    public void restore() {
        qrRequested = false;
        savedSession = tokens.load();
        if (savedSession == null) {
            publish(events.signedOut());
            return;
        }
        restoring = true;
        connect();
        // A stored session that never answers would otherwise hold the launcher
        // on the checking screen forever.
        main.postDelayed(this::abandonRestore, 25_000);
    }

    private void abandonRestore() {
        if (!restoring || closed) return;
        restoring = false;
        tokens.clear();
        expectedDisconnect = true;
        client.disconnect();
        publish(events.sessionExpired());
    }

    public void startCredentials(String account, String password) {
        if (account == null || account.trim().isEmpty() || password == null || password.isEmpty()) {
            publish(events.failed("Steam account and password are required"));
            return;
        }
        signingOut = false;
        qrRequested = false;
        savedSession = null;
        credentialAccount = account.trim();
        credentialPassword = password;
        reconnects = 0;
        publish(events.authStarted(null));
        connect();
    }

    public void signOut() {
        signingOut = true;
        restoring = false;
        tokens.clear();
        expectedDisconnect = true;
        // Publish first: the user asked to stop, and disconnecting can take a
        // while when the connection is the thing that is wedged.
        publish(events.signedOut());
        if (client.isConnected()) user.logOff();
        client.disconnect();
    }

    public SteamClient client() {
        return client;
    }

    public java.util.List<Integer> ownedDlc() {
        return events.ownedDlc();
    }

    public CompletableFuture<List<ModSyncPlan.Subscribed>> workshop() {
        return CompletableFuture.supplyAsync(() -> {
                List<in.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.PublishedFileDetails>
                    details = new ArrayList<>();
                int page = 1;
                while (true) {
                    CPublishedFile_GetUserFiles_Request request = DD1WorkshopCatalog.request(
                        client.getSteamID().convertToUInt64(), page++);
                    ServiceMethodResponse<CPublishedFile_GetUserFiles_Response.Builder> response =
                        publishedFiles.getUserFiles(request).runBlock();
                    if (response.getResult() != EResult.OK)
                        throw new IllegalStateException("Steam Workshop result " + response.getResult());
                    CPublishedFile_GetUserFiles_Response.Builder body = response.getBody();
                    int received = body.getPublishedfiledetailsCount();
                    details.addAll(body.getPublishedfiledetailsList());
                    if (received == 0 || details.size() >= body.getTotal()) break;
                }
            return DD1WorkshopCatalog.fromDetails(details);
        }, operations);
    }

    public CompletableFuture<DD1WorkshopPage> browseWorkshop(String query, int sort, int page) {
        return CompletableFuture.supplyAsync(() -> {
                long directId = DD1WorkshopCatalog.directId(query);
                if (directId != 0) {
                    ServiceMethodResponse<CPublishedFile_GetDetails_Response.Builder> response =
                        publishedFiles.getDetails(DD1WorkshopCatalog.details(directId)).runBlock();
                    requireOk(response);
                    List<DD1WorkshopItem> items = DD1WorkshopCatalog.items(
                        response.getBody().getPublishedfiledetailsList());
                    return new DD1WorkshopPage(items, items.size());
                }
                else {
                    ServiceMethodResponse<CPublishedFile_QueryFiles_Response.Builder> response =
                        publishedFiles.queryFiles(DD1WorkshopCatalog.query(query, sort, page))
                            .runBlock();
                    requireOk(response);
                    return new DD1WorkshopPage(DD1WorkshopCatalog.items(
                        response.getBody().getPublishedfiledetailsList()),
                        response.getBody().getTotal());
                }
        }, operations);
    }

    public CompletableFuture<DD1WorkshopItem> workshopDetail(long publishedFileId) {
        return CompletableFuture.supplyAsync(() -> {
                ServiceMethodResponse<CPublishedFile_GetDetails_Response.Builder> response =
                    publishedFiles.getDetails(DD1WorkshopCatalog.fullDetails(publishedFileId))
                        .runBlock();
                requireOk(response);
                List<DD1WorkshopItem> items = DD1WorkshopCatalog.fullItems(
                    response.getBody().getPublishedfiledetailsList());
                if (items.isEmpty()) throw new IllegalStateException("Workshop item not found");
            return items.get(0);
        }, operations);
    }

    public CompletableFuture<Void> subscribe(long publishedFileId) {
        return CompletableFuture.runAsync(() -> {
                ServiceMethodResponse<CPublishedFile_Subscribe_Response.Builder> response =
                    publishedFiles.subscribe(DD1WorkshopCatalog.subscribe(publishedFileId))
                        .runBlock();
                requireOk(response);
        }, operations);
    }

    public CompletableFuture<Void> unsubscribe(long publishedFileId) {
        return CompletableFuture.runAsync(() -> {
                ServiceMethodResponse<CPublishedFile_Unsubscribe_Response.Builder> response =
                    publishedFiles.unsubscribe(DD1WorkshopCatalog.unsubscribe(publishedFileId))
                        .runBlock();
                requireOk(response);
        }, operations);
    }

    private static void requireOk(ServiceMethodResponse<?> response) {
        if (response.getResult() != EResult.OK)
            throw new IllegalStateException("Steam Workshop result " + response.getResult());
    }

    public List<License> licenses() {
        return licenses;
    }

    @Override
    public void close() {
        closed = true;
        expectedDisconnect = true;
        client.disconnect();
        operations.shutdownNow();
        main.removeCallbacks(this::abandonRestore);
        callbackLoop.shutdown();
        for (Closeable subscription : subscriptions) {
            try {
                subscription.close();
            }
            catch (Exception ignored) {}
        }
    }

    private void connect() {
        if (closed) return;
        expectedDisconnect = false;
        if (!client.isConnected()) client.connect();
    }

    private void runCallbacks() {
        while (!closed) callbacks.runWaitCallbacks(1000L);
    }

    private void onConnected() {
        operations.execute(qrRequested ? this::authenticateQr :
            credentialPassword != null ? this::authenticateCredentials : this::logOnSavedSession);
    }

    private void authenticateQr() {
        try {
            AuthSessionDetails details = new AuthSessionDetails();
            details.deviceFriendlyName = "DD1 Android Launcher";
            details.persistentSession = true;
            QrAuthSession session = client.getAuthentication().beginAuthSessionViaQR(details).get();
            session.setChallengeUrlChanged(updated -> publish(events.authStarted(updated.getChallengeUrl())));
            publish(events.authStarted(session.getChallengeUrl()));
            AuthPollResult result = session.pollingWaitForResult().get();
            tokens.save(result.getAccountName(), result.getRefreshToken());
            logOn(result.getAccountName(), result.getRefreshToken());
        }
        catch (Exception error) {
            fail(error);
        }
    }

    private void logOnSavedSession() {
        SteamTokenStore.Session session = savedSession;
        if (session == null) {
            publish(events.signedOut());
            return;
        }
        logOn(session.account, session.token);
    }

    private void authenticateCredentials() {
        String password = credentialPassword;
        credentialPassword = null;
        try {
            AuthSessionDetails details = new AuthSessionDetails();
            details.username = credentialAccount;
            details.password = password;
            details.deviceFriendlyName = "DD1 Android Launcher";
            details.persistentSession = true;
            details.authenticator = new MobileApprovalAuthenticator();
            CredentialsAuthSession session = client.getAuthentication()
                .beginAuthSessionViaCredentials(details).get();
            AuthPollResult result = session.pollingWaitForResult().get();
            tokens.save(result.getAccountName(), result.getRefreshToken());
            logOn(result.getAccountName(), result.getRefreshToken());
        }
        catch (Exception error) {
            fail(error);
        }
        finally {
            credentialAccount = null;
        }
    }

    private void logOn(String account, String token) {
        LogOnDetails details = new LogOnDetails();
        details.setUsername(account);
        details.setAccessToken(token);
        details.setShouldRememberPassword(true);
        details.setLoginID(149);
        user.logOn(details);
    }

    private void onLoggedOn(LoggedOnCallback callback) {
        restoring = false;
        if (callback.getResult() == EResult.OK) {
            reconnects = 0;
            publish(events.loggedOn());
        }
        else fail(new IllegalStateException("Steam logon result " + callback.getResult()));
    }

    private void onLicenses(LicenseListCallback callback) {
        if (callback.getResult() != EResult.OK) {
            fail(new IllegalStateException("Steam license result " + callback.getResult()));
            return;
        }
        licenses = Collections.unmodifiableList(new ArrayList<>(callback.getLicenseList()));
        operations.execute(this::resolvePackages);
    }

    private void resolvePackages() {
        try {
            publish(events.checkingLicenses(licenses.size()));
            long startedAt = System.currentTimeMillis();
            List<PICSRequest> requests = new ArrayList<>();
            for (License license : licenses)
                requests.add(new PICSRequest(license.getPackageID(), license.getAccessToken()));

            AsyncJobMultiple.ResultSet<PICSProductInfoCallback> result = apps
                .picsGetProductInfo(Collections.emptyList(), requests).runBlock();
            if (result.getFailed()) throw new IllegalStateException("Steam package lookup failed");

            Map<Integer, List<Integer>> packageApps = new HashMap<>();
            for (PICSProductInfoCallback page : result.getResults()) {
                for (Map.Entry<Integer, PICSProductInfo> entry : page.getPackages().entrySet()) {
                    List<Integer> appIds = new ArrayList<>();
                    KeyValue appsValue = entry.getValue().getKeyValues().get("appids");
                    for (KeyValue app : appsValue.getChildren()) appIds.add(app.asInteger());
                    packageApps.put(entry.getKey(), appIds);
                }
            }
            catalog = readCatalog();
            publish(events.packagesResolved(packageApps,
                System.currentTimeMillis() - startedAt));
        }
        catch (Exception error) {
            fail(error);
        }
    }

    // Which depot holds which DLC, and the version Steam is offering, live in the
    // game's own PICS entry rather than in the packages the account owns.
    private DD1DepotCatalog readCatalog() {
        try {
            AsyncJobMultiple.ResultSet<PICSProductInfoCallback> result = apps
                .picsGetProductInfo(Collections.singletonList(
                    new PICSRequest(DD1SteamEvents.APP_ID, 0L)), Collections.emptyList())
                .runBlock();
            if (result.getFailed()) return DD1DepotCatalog.empty();

            List<DD1DepotCatalog.Row> rows = new ArrayList<>();
            for (PICSProductInfoCallback page : result.getResults()) {
                for (Map.Entry<Integer, PICSProductInfo> entry : page.getApps().entrySet()) {
                    for (KeyValue depot : entry.getValue().getKeyValues().get("depots").getChildren()) {
                        int depotId = asId(depot.getName());
                        if (depotId <= 0) continue;
                        rows.add(new DD1DepotCatalog.Row(depotId,
                            asId(depot.get("dlcappid").asString()),
                            depot.get("config").get("oslist").asString(),
                            depot.get("manifests").get("public").get("gid").asString()));
                    }
                }
            }
            return DD1DepotCatalog.of(rows);
        }
        catch (Exception error) {
            // A catalogue that could not be read only costs the offer to add or
            // update a DLC, and the sign-in it happens during must not fail with
            // it. An empty one shows on the screen as nothing known about any
            // version, which is the truth.
            return DD1DepotCatalog.empty();
        }
    }

    // Names that are words - "branches", "workshopdepot" - are not depots.
    private static int asId(String name) {
        try {
            return Integer.parseInt(name);
        }
        catch (RuntimeException notAnId) {
            return 0;
        }
    }

    public synchronized DD1DepotCatalog catalog() {
        return catalog;
    }

    public in.dragonbra.javasteam.rpc.service.Cloud cloudService() {
        return cloudService;
    }

    public in.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud cloud() {
        return cloud;
    }

    private void onDisconnected(DisconnectedCallback callback) {
        if (closed || expectedDisconnect || callback.isUserInitiated()) return;
        if (reconnects++ < 3) {
            operations.execute(() -> {
                try {
                    Thread.sleep(1000L);
                    connect();
                }
                catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        else publish(events.failed("Steam connection closed"));
    }

    // Tearing down an in-flight sign-in raises errors on the way out; the user
    // asked for that, so it is not reported as a failure.
    private void fail(Throwable error) {
        if (signingOut || closed) return;
        publish(events.failed(reason(error)));
    }

    private static String reason(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isEmpty()
            ? error.getClass().getSimpleName() : message;
    }

    private void publish(DD1InstallSnapshot snapshot) {
        main.post(() -> listener.onSnapshot(snapshot));
    }

    private <T extends in.dragonbra.javasteam.steam.handlers.ClientMsgHandler> T requireHandler(Class<T> type) {
        T handler = client.getHandler(type);
        if (handler == null) throw new IllegalStateException("Missing Steam handler " + type.getSimpleName());
        return handler;
    }

    private static final class MobileApprovalAuthenticator implements IAuthenticator {
        @Override
        public CompletableFuture<String> getDeviceCode(boolean previousCodeWasIncorrect) {
            return failedCode();
        }

        @Override
        public CompletableFuture<String> getEmailCode(String email, boolean previousCodeWasIncorrect) {
            return failedCode();
        }

        @Override
        public CompletableFuture<Boolean> acceptDeviceConfirmation() {
            return CompletableFuture.completedFuture(true);
        }

        private static CompletableFuture<String> failedCode() {
            CompletableFuture<String> result = new CompletableFuture<>();
            result.completeExceptionally(new IllegalStateException("Steam Guard code entry is not available"));
            return result;
        }
    }
}
