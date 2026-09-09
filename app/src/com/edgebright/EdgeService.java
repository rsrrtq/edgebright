package com.edgebright;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * 边缘手势前台服务。
 *
 * 悬浮结构 (全部 TYPE_APPLICATION_OVERLAY, Magisk 模块已授予 SYSTEM_ALERT_WINDOW):
 *  - 左/右边缘透明触摸条 (对应原版 pb() 的 40dp 边缘带), 拦截条内触摸,
 *    用 EdgeGestureDetector (原版 N6/he 逻辑) 计算亮度变化
 *  - 全屏 FLAG_NOT_TOUCHABLE 遮罩 (对应原版 shadeView), 亮度低于系统下限时压暗
 */
public class EdgeService extends Service {

    public static final String CHANNEL_ID = "edgebright";
    public static final String PREFS = "edgebright";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_EDGE_LEFT = "edge_left";
    public static final String KEY_EDGE_RIGHT = "edge_right";
    public static final String KEY_BAND_DP = "band_dp";
    /** 无障碍服务就绪/失效时请求重建遮罩 (切换层级) */
    public static final String ACTION_REBUILD_OVERLAY = "com.edgebright.REBUILD_OVERLAY";

    private WindowManager windowManager;
    private View leftStrip, rightStrip, dimOverlay;
    /** 添加 dimOverlay 用的 WindowManager (可能与 windowManager 不同上下文) */
    private WindowManager dimWm;
    private EdgeGestureDetector gestureDetector;
    private BrightnessController controller;
    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createChannel();

        controller = new BrightnessController(this, percent ->
                notificationManager.notify(1, buildNotification(percent)));
        // 恢复基准: 优先用上次保存的百分比, 其次系统亮度
        // (部分 ROM 如 ColorOS int 范围 0..2047 且 float 读不出, 直接换算会失真)
        int saved = getSharedPreferences(PREFS, MODE_PRIVATE).getInt("percent", -1);
        if (saved >= 1 && saved <= 100) {
            controller.initPercent(saved);
        } else {
            controller.restoreFromSystem();
        }

        gestureDetector = new EdgeGestureDetector(this, percent -> {
            controller.setPercent(percent);
            savePercent(percent);
        });
        // 关键: 手势基准值必须与恢复出的系统亮度同步, 否则首滑会从 0 跳变
        gestureDetector.setCurrentPercent(controller.getPercent());

        addDimOverlay();
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_EDGE_LEFT, true)) {
            addEdgeStrip(true);
        }
        if (prefs.getBoolean(KEY_EDGE_RIGHT, false)) {
            addEdgeStrip(false);
        }
        startForeground(1, buildNotification(controller.getPercent()));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_REBUILD_OVERLAY.equals(intent.getAction())) {
            // 无障碍通道状态变化: 拆掉旧遮罩, 按当前可用层级重建
            removeDimOverlay();
            addDimOverlay();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        gestureDetector.release();
        removeView(leftStrip);
        removeView(rightStrip);
        removeDimOverlay();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void removeView(View v) {
        if (v != null) {
            try {
                windowManager.removeView(v);
            } catch (Exception ignored) {
            }
        }
    }

    private void removeDimOverlay() {
        if (dimOverlay != null && dimWm != null) {
            try {
                dimWm.removeView(dimOverlay);
            } catch (Exception ignored) {
            }
        }
        dimOverlay = null;
    }

    /** 全屏压暗遮罩: 完全透明、不可触摸, 覆盖含状态栏/手势条的整屏 */
    private void addDimOverlay() {
        int type = pickOverlayType();
        // TYPE_ACCESSIBILITY_OVERLAY 必须用无障碍服务自身的 Context 添加
        // (它带有系统签发的窗口 token, EdgeService 自己的 Context 会 BadTokenException)
        android.content.Context ctx = DimAccessibilityService.instance;
        WindowManager wm;
        if (type == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY && ctx != null) {
            wm = (WindowManager) ctx.getSystemService(WINDOW_SERVICE);
        } else {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            ctx = this;
            wm = windowManager;
        }
        dimWm = wm;
        dimOverlay = new View(ctx);
        dimOverlay.setBackgroundColor(Color.TRANSPARENT);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        spanFullScreen(lp);
        android.util.Log.i("EdgeBright", "addDimOverlay type=" + lp.type
                + " accEnabled=" + isAccessibilityChannelEnabled()
                + " accCtx=" + (DimAccessibilityService.instance != null));
        try {
            wm.addView(dimOverlay, lp);
            controller.attachDimOverlay(dimOverlay);
        } catch (Exception e) {
            android.util.Log.e("EdgeBright", "addView 失败 type=" + lp.type, e);
            // 极端情况 (无障碍刚被关、层级被系统拒绝): 降级重试一次
            if (lp.type != WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) {
                removeView(dimOverlay);
                lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                try {
                    windowManager.addView(dimOverlay, lp);
                    dimWm = windowManager;
                    controller.attachDimOverlay(dimOverlay);
                } catch (Exception ignored2) {
                }
            }
        }
    }

    /**
     * 遮罩窗口层级: 无障碍服务已启用时用 TYPE_ACCESSIBILITY_OVERLAY
     * (在状态栏/通知栏/导航栏之上, 系统只校验"当前绑定的无障碍服务包名 ==
     * 窗口包名", 同包的 EdgeService 也能添加); 否则退回普通悬浮层。
     */
    private int pickOverlayType() {
        if (Build.VERSION.SDK_INT >= 28 && isAccessibilityChannelEnabled()) {
            return WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
    }

    private boolean isAccessibilityChannelEnabled() {
        try {
            String enabled = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabled != null
                    && enabled.contains(new android.content.ComponentName(
                            this, DimAccessibilityService.class).flattenToString());
        } catch (Exception e) {
            return false;
        }
    }

    /** 去掉系统栏避让 (fitInsetsTypes 默认会把窗口挤进 content 区域) */
    private void spanFullScreen(WindowManager.LayoutParams lp) {
        if (Build.VERSION.SDK_INT >= 30) {
            lp.setFitInsetsTypes(0);
        }
        lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
    }

    /** 边缘触摸条: 透明但可接收触摸 (与原版 40dp 边缘带对应) */
    private void addEdgeStrip(final boolean left) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int bandPx = Math.round(prefs.getInt(KEY_BAND_DP, 40)
                * getResources().getDisplayMetrics().density);

        View strip = new View(this) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                return true;
            }
        };
        strip.setBackgroundColor(Color.TRANSPARENT);
        strip.setClickable(true);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                bandPx,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = left ? Gravity.START : Gravity.END;
        spanFullScreen(lp);
        try {
            windowManager.addView(strip, lp);
            if (left) {
                leftStrip = strip;
            } else {
                rightStrip = strip;
            }
        } catch (Exception ignored) {
        }
    }

    private void savePercent(int percent) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putInt("percent", percent).apply();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;  // NotificationChannel 仅 API 26+
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        notificationManager.createNotificationChannel(channel);
    }

    private Notification buildNotification(int percent) {
        String text = getString(R.string.notif_text, percent);
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        return b.build();
    }
}
