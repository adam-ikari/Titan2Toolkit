package com.titan2.toolkit;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;


import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

/**
 * Entry point of the module.
 *
 * <p>Feature 1: force the stock launcher workspace to a 6-column grid.
 */
public final class ModuleMain extends XposedModule {

    static final String TAG = "Titan2Toolkit";
    static final int COLUMNS = 6;

    private String mHookedPackage = null;

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String pkg = param.getPackageName();
        if (!Targets.LAUNCHERS.contains(pkg)) {
            return;
        }
        if (pkg.equals(mHookedPackage)) {
            return;
        }
        try {
            installLauncherGrid(param.getClassLoader(), pkg);
            mHookedPackage = pkg;
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "failed to install hooks for " + pkg, t);
        }
    }

    private void installLauncherGrid(ClassLoader cl, String pkg) throws Throwable {
        Class<?> idp = findClass(cl, pkg + ".InvariantDeviceProfile",
                "com.android.launcher3.InvariantDeviceProfile");
        if (idp == null) {
            log(Log.WARN, TAG, "no InvariantDeviceProfile in " + pkg + ", grid hook skipped");
            return;
        }

        Field numColumns = findField(idp, "numColumns");
        if (numColumns == null) {
            log(Log.WARN, TAG, "InvariantDeviceProfile.numColumns not found");
            return;
        }
        numColumns.setAccessible(true);

        boolean installed = false;

        // Android 16: called before every DeviceProfile is built.
        Class<?> info = findClass(cl, pkg + ".Info", "com.android.launcher3.Info");
        Method newDpBuilder = info == null ? null
                : findMethod(idp, "newDPBuilder", Context.class, info);
        if (newDpBuilder == null) {
            // Parameter types differ between releases; fall back to the name.
            newDpBuilder = findMethodByName(idp, "newDPBuilder");
        }

        // Android 14/15: called at the end of grid init, before profiles are built.
        Method partnerOverrides = findMethod(idp, "applyPartnerDeviceProfileOverrides",
                Context.class, DisplayMetrics.class);

        if (partnerOverrides != null) {
            hook(partnerOverrides).intercept(chain -> {
                Object result = chain.proceed();
                forceColumns(numColumns, chain.getThisObject());
                return result;
            });
            installed = true;
            log(Log.INFO, TAG, "hooked applyPartnerDeviceProfileOverrides");
        }

        if (newDpBuilder != null) {
            hook(newDpBuilder).intercept(chain -> {
                forceColumns(numColumns, chain.getThisObject());
                return chain.proceed();
            });
            installed = true;
            log(Log.INFO, TAG, "hooked newDPBuilder");
        }

        if (!installed) {
            // Fallback: constructor. Device profiles may already be built at this point,
            // so this only helps launchers that re-read the grid lazily.
            for (Constructor<?> ctor : idp.getDeclaredConstructors()) {
                hook(ctor).intercept(chain -> {
                    Object result = chain.proceed();
                    forceColumns(numColumns, chain.getThisObject());
                    return result;
                });
            }
            log(Log.WARN, TAG, "no grid anchor found, used constructor fallback");
        }

        log(Log.INFO, TAG, "launcher grid hook installed for " + pkg + " (columns=" + COLUMNS + ")");
    }

    private void forceColumns(Field field, Object profile) {
        if (profile == null) {
            return;
        }
        try {
            int old = field.getInt(profile);
            if (old != COLUMNS) {
                field.setInt(profile, COLUMNS);
                log(Log.INFO, TAG, "workspace columns " + old + " -> " + COLUMNS);
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "failed to set columns", t);
        }
    }

    private static Class<?> findClass(ClassLoader cl, String... names) {
        for (String name : names) {
            try {
                return Class.forName(name, false, cl);
            } catch (ClassNotFoundException ignored) {
                // try next
            }
        }
        return null;
    }

    private static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep walking
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> cls, String name, Class<?>... params) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                // keep walking
            }
        }
        return null;
    }

    private static Method findMethodByName(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 2) {
                    return m;
                }
            }
        }
        return null;
    }
}
