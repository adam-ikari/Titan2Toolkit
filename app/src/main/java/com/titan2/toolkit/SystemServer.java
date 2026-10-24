package com.titan2.toolkit;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import java.lang.reflect.Method;

/** Access to system_server internals used by the shortcut hook. */
final class SystemServer {

    private static Context sContext;
    private static Object sWmInternal;
    private static Method sImeHeight;
    private static boolean sImeLookupFailed;

    static synchronized Context context() {
        if (sContext != null) {
            return sContext;
        }
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("currentActivityThread").invoke(null);
            if (thread != null) {
                sContext = (Context) at.getMethod("getSystemContext").invoke(thread);
            }
        } catch (Throwable t) {
            Log.w(ModuleMain.TAG, "system context lookup failed", t);
        }
        return sContext;
    }

    static boolean isScreenOn() {
        Context context = context();
        if (context == null) {
            return false;
        }
        PowerManager pm = context.getSystemService(PowerManager.class);
        return pm != null && pm.isInteractive();
    }

    /** True when the software keyboard is on screen; failures are treated as visible. */
    static synchronized boolean isImeVisible() {
        if (sImeLookupFailed) {
            return true;
        }
        try {
            if (sImeHeight == null) {
                Class<?> localServices = Class.forName("com.android.server.LocalServices");
                Class<?> wmi = Class.forName("com.android.server.wm.WindowManagerInternal");
                Object service = localServices.getMethod("getService", Class.class)
                        .invoke(null, wmi);
                if (service == null) {
                    throw new IllegalStateException("WindowManagerInternal unavailable");
                }
                sImeHeight = wmi.getMethod("getInputMethodWindowVisibleHeight", int.class);
                sWmInternal = service;
            }
            return (int) sImeHeight.invoke(sWmInternal, 0) > 0;
        } catch (Throwable t) {
            sImeLookupFailed = true;
            Log.w(ModuleMain.TAG, "IME visibility lookup failed; shortcuts stay stock", t);
            return true;
        }
    }

    private SystemServer() {
    }
}
