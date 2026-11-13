package com.titan2.toolkit;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Minimal module UI: feature switches and environment info. */
public final class MainActivity extends Activity {

    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrefs = getSharedPreferences(FeatureFlags.PREFS, MODE_PRIVATE);

        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(24);
        title.setGravity(Gravity.START);
        root.addView(title);

        TextView status = new TextView(this);
        status.setText(R.string.main_status);
        status.setTextSize(16);
        status.setPadding(0, pad / 2, 0, pad);
        root.addView(status);

        TextView features = new TextView(this);
        features.setText(R.string.features_title);
        features.setTextSize(18);
        features.setPadding(0, 0, 0, pad / 4);
        root.addView(features);

        addSwitch(root, FeatureFlags.KEY_WORKSPACE_6_COLUMNS,
                FeatureFlags.DEFAULT_WORKSPACE_6_COLUMNS,
                R.string.feature_workspace_6_columns,
                R.string.feature_workspace_6_columns_hint);

        addSwitch(root, FeatureFlags.KEY_GLOBAL_LETTER_SHORTCUTS,
                FeatureFlags.DEFAULT_GLOBAL_LETTER_SHORTCUTS,
                R.string.feature_global_letter_shortcuts,
                R.string.feature_global_letter_shortcuts_hint);

        addSwitch(root, FeatureFlags.KEY_EXPERIMENTAL_CLONE,
                FeatureFlags.DEFAULT_EXPERIMENTAL_CLONE,
                R.string.feature_experimental_clone,
                R.string.feature_experimental_clone_hint);

        addInfo(root, getString(R.string.main_columns, ModuleMain.COLUMNS));
        addInfo(root, getString(R.string.main_scope,
                TextUtils.join(", ", Targets.ALL)));
        addInfo(root, Build.MANUFACTURER + " " + Build.MODEL
                + " · Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void addSwitch(LinearLayout parent, String key, boolean defaultValue,
                           int titleRes, int hintRes) {
        Switch toggle = new Switch(this);
        toggle.setText(titleRes);
        toggle.setTextSize(16);
        toggle.setChecked(mPrefs.getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPrefs.edit().putBoolean(key, isChecked).apply();
            getContentResolver().notifyChange(FeatureFlags.URI, null);
        });
        parent.addView(toggle);

        TextView hint = new TextView(this);
        hint.setText(hintRes);
        hint.setTextSize(13);
        hint.setTextColor(Color.GRAY);
        hint.setPadding(0, 0, 0, Math.round(12 * getResources().getDisplayMetrics().density));
        parent.addView(hint);
    }

    private void addInfo(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(Color.GRAY);
        tv.setPadding(0, 4, 0, 4);
        parent.addView(tv);
    }
}
