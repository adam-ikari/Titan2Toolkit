package com.titan2.toolkit;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cached switch lookup for code running in other processes (launcher, system_server).
 *
 * <p>The launcher reads synchronously; system_server only reads a cached value and refreshes
 * off the input thread, so key dispatch is never blocked by the provider.
 */
public final class RemoteConfig {

    private static final long TTL_MS = 2000;
    private static final long SYSTEM_TTL_MS = 5000;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "titan2-config");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicBoolean sRefreshing = new AtomicBoolean();

    private static long sLastRead;
    private static volatile long sLastSystemRefresh;
    private static boolean sWorkspace6Columns = FeatureFlags.DEFAULT_WORKSPACE_6_COLUMNS;
    private static volatile boolean sGlobalLetterShortcuts =
            FeatureFlags.DEFAULT_GLOBAL_LETTER_SHORTCUTS;
    private static volatile boolean sExperimentalClone = FeatureFlags.DEFAULT_EXPERIMENTAL_CLONE;
    private static volatile boolean sSystemLoaded;

    public static synchronized boolean workspace6Columns(Context context) {
        long now = SystemClock.elapsedRealtime();
        if (context == null || now - sLastRead < TTL_MS) {
            return sWorkspace6Columns;
        }
        sLastRead = now;
        read(context);
        return sWorkspace6Columns;
    }

    /** Cached value; stays off until the provider has been read successfully. */
    public static boolean globalLetterShortcuts() {
        long now = SystemClock.elapsedRealtime();
        if (now - sLastSystemRefresh >= SYSTEM_TTL_MS) {
            sLastSystemRefresh = now;
            warmUp(SystemServer.context());
        }
        return sSystemLoaded && sGlobalLetterShortcuts;
    }

    /** Experimental clone allowlist switch; stays off until the provider has been read. */
    public static boolean experimentalAppClone() {
        long now = SystemClock.elapsedRealtime();
        if (now - sLastSystemRefresh >= SYSTEM_TTL_MS) {
            sLastSystemRefresh = now;
            warmUp(SystemServer.context());
        }
        return sSystemLoaded && sExperimentalClone;
    }

    public static void warmUp(Context context) {
        if (context == null || !sRefreshing.compareAndSet(false, true)) {
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                synchronized (RemoteConfig.class) {
                    read(context);
                }
            } finally {
                sRefreshing.set(false);
            }
        });
    }

    private static void read(Context context) {
        try {
            Bundle result = context.getContentResolver()
                    .call(FeatureFlags.URI, FeatureFlags.METHOD_GET, null, null);
            if (result == null) {
                return;
            }
            sWorkspace6Columns = result.getBoolean(
                    FeatureFlags.KEY_WORKSPACE_6_COLUMNS, sWorkspace6Columns);
            sGlobalLetterShortcuts = result.getBoolean(
                    FeatureFlags.KEY_GLOBAL_LETTER_SHORTCUTS, sGlobalLetterShortcuts);
            sExperimentalClone = result.getBoolean(
                    FeatureFlags.KEY_EXPERIMENTAL_CLONE, sExperimentalClone);
            sSystemLoaded = true;
        } catch (Throwable t) {
            // Provider not reachable from this process; keep the last known values.
            Log.w(ModuleMain.TAG, "config provider unavailable", t);
        }
    }

    private RemoteConfig() {
    }
}
