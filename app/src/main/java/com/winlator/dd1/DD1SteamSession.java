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
import in.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails;
import in.dragonbra.javasteam.steam.handlers.steamuser.SteamUser;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback;
import in.dragonbra.javasteam.steam.steamclient.SteamClient;
import in.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager;
import in.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback;
import in.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback;
import in.dragonbra.javasteam.types.AsyncJobMultiple;
import in.dragonbra.javasteam.types.KeyValue;

public final class DD1SteamSession implements Closeable {
    public interface Listener {
        void onSnapshot(DD1InstallSnapshot snapshot);
    }

    private final SteamClient client = new SteamClient();
    private final CallbackManager callbacks = new CallbackManager(client);
    private final SteamUser user = requireHandler(SteamUser.class);
    private final SteamApps apps = requireHandler(SteamApps.class);
    private final SteamTokenStore tokens;
    private final DD1SteamEvents events = new DD1SteamEvents();
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService operations = Executors.newSingleThreadExecutor();
    private final ExecutorService callbackLoop = Executors.newSingleThreadExecutor();
    private final List<Closeable> subscriptions = new ArrayList<>();

    private volatile List<License> licenses = Collections.emptyList();
    private volatile boolean closed;
    private volatile boolean qrRequested;
    private volatile boolean expectedDisconnect;
    private volatile boolean signingOut;
    private volatile SteamTokenStore.Session savedSession;
    private volatile int reconnects;
    private volatile String credentialAccount;
    private volatile String credentialPassword;

    public DD1SteamSession(Context context, Listener listener) {
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
        connect();
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
        tokens.clear();
        if (client.isConnected()) user.logOff();
        expectedDisconnect = true;
        client.disconnect();
        publish(events.signedOut());
    }

    public SteamClient client() {
        return client;
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
            password = null;
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
            publish(events.packagesResolved(packageApps));
        }
        catch (Exception error) {
            fail(error);
        }
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
        publish(events.failed(error.getClass().getSimpleName()));
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
