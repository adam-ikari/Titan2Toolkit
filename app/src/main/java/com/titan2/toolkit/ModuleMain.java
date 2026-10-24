package com.titan2.toolkit;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;


import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

/**
 * Entry point of the module.
 *
 * <p>Feature 1: force the stock launcher workspace, Hotseat and All Apps to a 6-column grid.
 * <p>Feature 2: make OEM letter-key shortcuts work from any foreground app.
 */
public final class ModuleMain extends XposedModule {

    static final String TAG = "Titan2Toolkit";
    static final int COLUMNS = 6;

    /** All InvariantDeviceProfile fields DeviceProfile copies the column counts from. */
    private static final String[] GRID_FIELDS = {
            "numColumns",
            "numShownHotseatIcons",
            "numDatabaseHotseatIcons",
            "numAllAppsColumns",
            "numDatabaseAllAppsColumns",
    };

    private String mHookedPackage = null;

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String pkg = param.getPackageName();
        boolean launcher = Targets.LAUNCHERS.contains(pkg);
        boolean system = Targets.SYSTEM.equals(pkg);
        if (!launcher && !system) {
            return;
        }
        if (pkg.equals(mHookedPackage)) {
            return;
        }
        try {
            if (launcher) {
                installLauncherGrid(param.getClassLoader(), pkg);
            } else {
                RemoteConfig.warmUp(SystemServer.context());
                ShortcutHooks.install(this, param.getClassLoader());
            }
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

        LayoutGuard.install(this, cl, idp);

        List<Field> gridFields = new ArrayList<>();
        for (String name : GRID_FIELDS) {
            Field field = findField(idp, name);
            if (field == null) {
                log(Log.WARN, TAG, "InvariantDeviceProfile." + name + " not found");
                continue;
            }
            field.setAccessible(true);
            gridFields.add(field);
        }
        if (gridFields.isEmpty()) {
            log(Log.WARN, TAG, "no grid fields in " + pkg + ", grid hook skipped");
            return;
        }

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
                LayoutGuard.beforeGridOverride(chain.getThisObject(), firstArg(chain), pkg);
                forceColumnsIfEnabled(gridFields, chain.getThisObject(), firstArg(chain));
                return result;
            });
            installed = true;
            log(Log.INFO, TAG, "hooked applyPartnerDeviceProfileOverrides");
        }

        if (newDpBuilder != null) {
            hook(newDpBuilder).intercept(chain -> {
                LayoutGuard.beforeGridOverride(chain.getThisObject(), firstArg(chain), pkg);
                forceColumnsIfEnabled(gridFields, chain.getThisObject(), firstArg(chain));
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
                    LayoutGuard.beforeGridOverride(chain.getThisObject(), firstArg(chain), pkg);
                    forceColumnsIfEnabled(gridFields, chain.getThisObject(), firstArg(chain));
                    return result;
                });
            }
            log(Log.WARN, TAG, "no grid anchor found, used constructor fallback");
        }

        log(Log.INFO, TAG, "launcher grid hook installed for " + pkg + " (columns=" + COLUMNS + ")");
    }

    private void forceColumnsIfEnabled(List<Field> fields, Object profile, Object contextArg) {
        Context context = contextArg instanceof Context ? (Context) contextArg : null;
        if (!RemoteConfig.workspace6Columns(context) || profile == null) {
            return;
        }
        for (Field field : fields) {
            try {
                int old = field.getInt(profile);
                if (old != COLUMNS) {
                    field.setInt(profile, COLUMNS);
                    log(Log.INFO, TAG, field.getName() + " " + old + " -> " + COLUMNS);
                }
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "failed to set " + field.getName(), t);
            }
        }
    }

    private static Object firstArg(Chain chain) {
        List<Object> args = chain.getArgs();
        return args.isEmpty() ? null : args.get(0);
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
