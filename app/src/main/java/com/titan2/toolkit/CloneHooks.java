package com.titan2.toolkit;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.libxposed.api.XposedModule;

/**
 * Experimental: let the stock Settings "Cloned apps" page offer any third-party app.
 *
 * <p>The AOSP clone page filters its list through the framework allowlist
 * {@code com.android.internal.R.array.cloneable_apps} (read via
 * {@link Resources#getStringArray(int)}). The activity that performs the clone,
 * {@code CloneBackend.installCloneApp}, does not consult that allowlist, so widening the array is
 * enough to expose the clone action for every installed third-party package.
 *
 * <p>Only the allowlist lookup is touched; when the switch is off every call passes through.
 */
final class CloneHooks {

    private static final String TAG = ModuleMain.TAG;

    /** Resolved framework array id; 0 = not present, -1 = not resolved yet. */
    private static volatile int sCloneableArrayId = -1;

    private CloneHooks() {
    }

    static void install(XposedModule module, String pkg) {
        Method getStringArray;
        try {
            getStringArray = Resources.class.getDeclaredMethod("getStringArray", int.class);
        } catch (NoSuchMethodException e) {
            module.log(Log.WARN, TAG, "Resources.getStringArray missing; clone hook skipped");
            return;
        }

        module.hook(getStringArray).intercept(chain -> {
            Object receiver = chain.getThisObject();
            Object arg = chain.getArgs().isEmpty() ? null : chain.getArgs().get(0);
            if (!(receiver instanceof Resources) || !(arg instanceof Integer)) {
                return chain.proceed();
            }
            Resources res = (Resources) receiver;
            int id = (Integer) arg;
            int cloneableId = cloneableArrayId(res);
            if (cloneableId == 0) {
                return chain.proceed();
            }
            if (id != cloneableId || !RemoteConfig.experimentalAppClone()) {
                return chain.proceed();
            }

            Context context = currentContext();
            if (context == null) {
                return chain.proceed();
            }
            String[] extra = thirdPartyPackages(context);
            if (extra.length == 0) {
                return chain.proceed();
            }

            Object original = chain.proceed();
            String[] base = original instanceof String[] ? (String[]) original : new String[0];
            Set<String> merged = new LinkedHashSet<>(Arrays.asList(base));
            merged.addAll(Arrays.asList(extra));
            module.log(Log.INFO, TAG,
                    "clone allowlist " + base.length + " -> " + merged.size() + " apps");
            return merged.toArray(new String[0]);
        });

        module.log(Log.INFO, TAG, "clone allowlist hook installed for " + pkg);
    }

    private static int cloneableArrayId(Resources res) {
        int id = sCloneableArrayId;
        if (id < 0) {
            try {
                id = res.getIdentifier("cloneable_apps", "array", "android");
            } catch (Throwable t) {
                id = 0;
            }
            sCloneableArrayId = id;
        }
        return id;
    }

    private static Context currentContext() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object application = activityThread.getMethod("currentApplication").invoke(null);
            if (application instanceof Context) {
                return (Context) application;
            }
        } catch (Throwable t) {
            Log.w(TAG, "application context lookup failed", t);
        }
        return null;
    }

    private static String[] thirdPartyPackages(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            List<String> result = new ArrayList<>(apps.size());
            for (ApplicationInfo app : apps) {
                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    continue;
                }
                if ((app.flags & ApplicationInfo.FLAG_INSTALLED) == 0) {
                    continue;
                }
                result.add(app.packageName);
            }
            return result.toArray(new String[0]);
        } catch (Throwable t) {
            Log.w(TAG, "installed package lookup failed", t);
            return new String[0];
        }
    }
}
