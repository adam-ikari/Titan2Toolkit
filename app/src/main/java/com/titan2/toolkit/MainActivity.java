package com.titan2.toolkit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Minimal module UI: what the module does, and a shortcut to the module manager. */
public final class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        addInfo(root, getString(R.string.main_columns, ModuleMain.COLUMNS));
        addInfo(root, getString(R.string.main_scope,
                TextUtils.join(", ", Targets.LAUNCHERS)));
        addInfo(root, Build.MANUFACTURER + " " + Build.MODEL
                + " · Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");

        Button open = new Button(this);
        open.setText("打开 Vector / LSPosed 管理器");
        open.setOnClickListener(v -> openManager());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad;
        root.addView(open, lp);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void addInfo(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(Color.GRAY);
        tv.setPadding(0, 4, 0, 4);
        parent.addView(tv);
    }

    private void openManager() {
        String[] candidates = {"org.lsposed.manager", "com.vector.manager"};
        for (String pkg : candidates) {
            Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent != null) {
                startActivity(intent);
                return;
            }
        }
        Toast.makeText(this, "未找到模块管理器，请手动打开", Toast.LENGTH_SHORT).show();
    }
}
