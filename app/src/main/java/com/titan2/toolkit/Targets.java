package com.titan2.toolkit;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Packages this module targets. Kept in sync with META-INF/xposed/scope.list. */
public final class Targets {

    /**
     * Stock launcher of Titan 2 international firmware (TEE/EEA, Android 16).
     * Verified from firmware V01.00.14: system_ext/priv-app/SearchLauncherQuickStep,
     * package com.android.launcher3, versionName 16.
     */
    public static final String LAUNCHER = "com.android.launcher3";

    public static final List<String> LAUNCHERS = Collections.singletonList(LAUNCHER);

    /** system_server, where the OEM AGUI keyboard shortcut logic runs. */
    public static final String SYSTEM = "android";

    public static final List<String> ALL =
            Collections.unmodifiableList(Arrays.asList(LAUNCHER, SYSTEM));

    private Targets() {
    }
}
