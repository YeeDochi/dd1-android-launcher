package com.winlator.dd1;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;

import in.dragonbra.javasteam.enums.EDepotFileFlag;
import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.steam.cdn.Client;
import in.dragonbra.javasteam.steam.cdn.Server;
import in.dragonbra.javasteam.steam.handlers.steamapps.SteamApps;
import in.dragonbra.javasteam.steam.handlers.steamapps.callback.DepotKeyCallback;
import in.dragonbra.javasteam.steam.handlers.steamcontent.SteamContent;
import in.dragonbra.javasteam.steam.steamclient.SteamClient;
import in.dragonbra.javasteam.types.ChunkData;
import in.dragonbra.javasteam.types.DepotManifest;
import in.dragonbra.javasteam.types.FileData;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.future.FutureKt;

final class DD1WorkshopCdn {
    interface Progress {
        void update(int percent, String fileName);
    }

    private static final int APP_ID = DD1SteamEvents.APP_ID;
    private static final int RETRIES = 12;

    private DD1WorkshopCdn() {}

    static void download(SteamClient steam, long manifestId, File target,
            Progress progress, BooleanSupplier cancelled) throws Exception {
        CoroutineScope scope = steam.getDefaultScope$javasteam();
        SteamApps apps = require(steam, SteamApps.class);
        SteamContent content = require(steam, SteamContent.class);
        DepotKeyCallback key = apps.getDepotDecryptionKey(APP_ID, APP_ID).runBlock();
        if (key.getResult() != EResult.OK)
            throw new IllegalStateException("Steam depot key: " + key.getResult());

        List<Server> all = FutureKt.asCompletableFuture(content.getServersForSteamPipe(
            steam.getCellID(), 20, scope)).get();
        Server proxy = null;
        List<Server> servers = new ArrayList<>();
        for (Server server : all) {
            if (server.getUseAsProxy()) proxy = server;
            if ((server.getType().equals("CDN") || server.getType().equals("SteamCache"))
                    && allows(server, APP_ID)) servers.add(server);
        }
        servers.sort(Comparator.comparingDouble(Server::getWeightedLoad));
        if (servers.isEmpty()) throw new IllegalStateException("No Steam CDN server");
        final Server proxyServer = proxy;

        long requestCode = FutureKt.asCompletableFuture(content.getManifestRequestCode(
            APP_ID, APP_ID, manifestId, "public", null, scope)).get();
        if (requestCode == 0) throw new IllegalStateException("Steam manifest access denied");

        Client cdn = new Client(steam);
        DepotManifest manifest = retry(servers, server -> cdn.downloadManifestFuture(APP_ID,
            manifestId, requestCode, server, key.getDepotKey(), proxyServer,
            null).get());
        target.mkdirs();
        long total = 0;
        for (FileData file : manifest.getFiles())
            if (!file.getFlags().contains(EDepotFileFlag.Directory)) total += file.getTotalSize();
        long downloaded = 0;
        progress.update(0, "");

        for (FileData file : manifest.getFiles()) {
            if (file.getFlags().contains(EDepotFileFlag.Directory)) continue;
            if (cancelled.getAsBoolean()) throw new InterruptedException("Cancelled");
            File output = safeTarget(target, file.getFileName()).toFile();
            File parent = output.getParentFile();
            if (parent != null) parent.mkdirs();
            List<ChunkData> chunks = new ArrayList<>(file.getChunks());
            chunks.sort(Comparator.comparingLong(ChunkData::getOffset));
            try (RandomAccessFile stream = new RandomAccessFile(output, "rw")) {
                stream.setLength(file.getTotalSize());
                for (ChunkData chunk : chunks) {
                    if (cancelled.getAsBoolean()) throw new InterruptedException("Cancelled");
                    byte[] bytes = new byte[bufferSize(chunk.getUncompressedLength(),
                        chunk.getCompressedLength())];
                    int count = retry(servers, server -> cdn.downloadDepotChunkFuture(APP_ID,
                        chunk, server, bytes, key.getDepotKey(), proxyServer,
                        null).get());
                    stream.seek(chunk.getOffset());
                    stream.write(bytes, 0, count);
                    downloaded += count;
                    progress.update(total == 0 ? 100 : (int)(downloaded * 100 / total),
                        file.getFileName());
                }
            }
        }
    }

    static Path safeTarget(File root, String name) {
        Path base = root.toPath().toAbsolutePath().normalize();
        Path target = base.resolve(name.replace('\\', '/')).normalize();
        if (!target.startsWith(base)) throw new IllegalArgumentException("Unsafe Workshop path");
        return target;
    }

    static int bufferSize(int uncompressed, int compressed) {
        return Math.max(uncompressed, compressed);
    }

    private static boolean allows(Server server, int appId) {
        int[] allowed = server.getAllowedAppIds();
        if (allowed.length == 0) return true;
        for (int id : allowed) if (id == appId) return true;
        return false;
    }

    private interface Attempt<T> { T run(Server server) throws Exception; }

    private static <T> T retry(List<Server> servers, Attempt<T> attempt) throws Exception {
        Exception failure = null;
        for (int i = 0; i < RETRIES; i++) {
            try {
                return attempt.run(servers.get(i % servers.size()));
            }
            catch (Exception error) {
                Throwable cause = error instanceof ExecutionException && error.getCause() != null
                    ? error.getCause() : error;
                failure = cause instanceof Exception ? (Exception)cause : error;
                if (i + 1 < RETRIES) Thread.sleep(retryDelay(i));
            }
        }
        throw failure;
    }

    static long retryDelay(int attempt) {
        return Math.min(2000, 250L * (attempt + 1));
    }

    private static <T> T require(SteamClient client, Class<T> type) {
        T handler = type.cast(client.getHandler(
            (Class<? extends in.dragonbra.javasteam.steam.handlers.ClientMsgHandler>)type));
        if (handler == null) throw new IllegalStateException("Missing Steam handler " + type);
        return handler;
    }
}
