package com.edgebright;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private SharedPreferences prefs;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(EdgeService.PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(20));
        root.setBackgroundColor(0xFFF5F5F5);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(20);
        title.setTextColor(0xFF212121);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText(R.string.app_desc);
        desc.setTextSize(13);
        desc.setTextColor(0xFF616161);
        desc.setPadding(0, dp(6), 0, dp(16));
        root.addView(desc);

        statusText = new TextView(this);
        statusText.setTextSize(14);
        statusText.setTextColor(0xFF212121);
        statusText.setPadding(0, 0, 0, dp(16));
        root.addView(statusText);

        CheckBox left = check(R.string.edge_left, prefs.getBoolean(EdgeService.KEY_EDGE_LEFT, true));
        CheckBox right = check(R.string.edge_right, prefs.getBoolean(EdgeService.KEY_EDGE_RIGHT, false));
        root.addView(left);
        root.addView(right);

        TextView bandLabel = new TextView(this);
        bandLabel.setText(R.string.band_width);
        bandLabel.setTextSize(13);
        bandLabel.setTextColor(0xFF616161);
        bandLabel.setPadding(0, dp(16), 0, dp(4));
        root.addView(bandLabel);

        LinearLayout bands = new LinearLayout(this);
        bands.setOrientation(LinearLayout.HORIZONTAL);
        int bandDp = prefs.getInt(EdgeService.KEY_BAND_DP, 40);
        for (int dp : new int[]{20, 30, 40, 50, 60}) {
            Button b = new Button(this);
            String label = dp + "dp";
            b.setText(label);
            b.setTextSize(12);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(0, 0, dp(6), 0);
            b.setLayoutParams(lp);
            b.setEnabled(dp != bandDp);
            b.setOnClickListener(v -> {
                prefs.edit().putInt(EdgeService.KEY_BAND_DP, dp).apply();
                restartService();
                Toast.makeText(this, R.string.reboot_apply, Toast.LENGTH_SHORT).show();
                recreate();
            });
            bands.addView(b);
        }
        root.addView(bands);

        Button start = new Button(this);
        start.setText(R.string.start_service);
        start.setOnClickListener(v -> {
            prefs.edit().putBoolean(EdgeService.KEY_ENABLED, true).apply();
            startService();
            updateStatus();
        });
        root.addView(start, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Button stop = new Button(this);
        stop.setText(R.string.stop_service);
        stop.setOnClickListener(v -> {
            prefs.edit().putBoolean(EdgeService.KEY_ENABLED, false).apply();
            stopService(new Intent(this, EdgeService.class));
            updateStatus();
        });
        root.addView(stop, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        updateStatus();
    }

    private CheckBox check(int labelRes, boolean checked) {
        CheckBox cb = new CheckBox(this);
        cb.setText(labelRes);
        cb.setTextSize(14);
        cb.setTextColor(0xFF212121);
        cb.setChecked(checked);
        cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                prefs.edit().putBoolean(
                        labelRes == R.string.edge_left ? EdgeService.KEY_EDGE_LEFT : EdgeService.KEY_EDGE_RIGHT,
                        isChecked).apply();
                restartService();
            }
        });
        return cb;
    }

    private void startService() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                Toast.makeText(this, R.string.need_overlay_perm, Toast.LENGTH_LONG).show();
            }
            return;
        }
        Intent service = new Intent(this, EdgeService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(service);
        } else {
            startService(service);
        }
    }

    private void restartService() {
        stopService(new Intent(this, EdgeService.class));
        if (prefs.getBoolean(EdgeService.KEY_ENABLED, true)) {
            startService();
        }
    }

    private void updateStatus() {
        boolean enabled = prefs.getBoolean(EdgeService.KEY_ENABLED, true);
        int percent = prefs.getInt("percent", -1);
        String status;
        if (!enabled) {
            status = getString(R.string.status_stopped);
        } else if (percent >= 0) {
            status = getString(R.string.status_running, percent);
        } else {
            status = getString(R.string.status_running_default);
        }
        statusText.setText(status);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
