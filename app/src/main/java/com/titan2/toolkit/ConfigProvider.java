package com.titan2.toolkit;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * Read-only view of the feature switches for hooked processes.
 *
 * <p>The launcher and system_server cannot read this app's private preferences directly, so
 * they query this provider instead. It exposes only feature booleans.
 */
public final class ConfigProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!FeatureFlags.METHOD_GET.equals(method)) {
            return null;
        }
        Context context = getContext();
        if (context == null) {
            return null;
        }
        SharedPreferences prefs = context.getSharedPreferences(
                FeatureFlags.PREFS, Context.MODE_PRIVATE);
        Bundle result = new Bundle();
        result.putBoolean(FeatureFlags.KEY_WORKSPACE_6_COLUMNS,
                prefs.getBoolean(FeatureFlags.KEY_WORKSPACE_6_COLUMNS,
                        FeatureFlags.DEFAULT_WORKSPACE_6_COLUMNS));
        result.putBoolean(FeatureFlags.KEY_GLOBAL_LETTER_SHORTCUTS,
                prefs.getBoolean(FeatureFlags.KEY_GLOBAL_LETTER_SHORTCUTS,
                        FeatureFlags.DEFAULT_GLOBAL_LETTER_SHORTCUTS));
        result.putBoolean(FeatureFlags.KEY_EXPERIMENTAL_CLONE,
                prefs.getBoolean(FeatureFlags.KEY_EXPERIMENTAL_CLONE,
                        FeatureFlags.DEFAULT_EXPERIMENTAL_CLONE));
        return result;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
