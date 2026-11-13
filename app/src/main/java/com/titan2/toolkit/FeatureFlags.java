package com.titan2.toolkit;

import android.net.Uri;

/** Feature switches shared between the module UI and hooked processes. */
public final class FeatureFlags {

    public static final String PREFS = "features";

    public static final String KEY_WORKSPACE_6_COLUMNS = "workspace_6_columns";
    public static final boolean DEFAULT_WORKSPACE_6_COLUMNS = true;

    public static final String KEY_GLOBAL_LETTER_SHORTCUTS = "global_letter_shortcuts";
    public static final boolean DEFAULT_GLOBAL_LETTER_SHORTCUTS = true;

    public static final String KEY_EXPERIMENTAL_CLONE = "experimental_clone";
    public static final boolean DEFAULT_EXPERIMENTAL_CLONE = false;

    public static final String AUTHORITY = "com.titan2.toolkit.config";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY);
    public static final String METHOD_GET = "get";

    private FeatureFlags() {
    }
}
