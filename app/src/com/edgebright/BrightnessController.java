package com.edgebright;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;

/**
 * 全局亮度控制器 —— 对应原版 ActivityTxt.bf() / jg() / e9()
 *
 * 与原版差异: 原版只写自己窗口的 WindowManager.LayoutParams.screenBrightness;
 * 全局版直接写 Settings.System.SCREEN_BRIGHTNESS (Magisk 模块已通过 appops
 * 授予 WRITE_SETTINGS), 并在亮度低于系统下限时用全屏不可触摸遮罩继续压暗
 * (对应原版 shadeView / jg() 思路)。
 */
public class BrightnessController {

    public static final int MIN_PERCENT = -100;
    public static final int MAX_PERCENT = 100;
    /** 系统亮度下限, 0 在部分面板上会直接熄屏, 2 更安全 */
    private static final int MIN_SYS_BRIGHTNESS = 2;
    private static final int MAX_SYS_BRIGHTNESS = 255;
    /** 低于系统下限时遮罩最大不透明度 (0..255), -100% 时约 92% 黑 */
    private static final int MAX_DIM_ALPHA = 235;

    public interface Listener {
        void onPercentChanged(int percent);
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private View dimOverlay;
    private int percent = 50;

    public BrightnessController(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    /** 注册全屏压暗遮罩 (由服务创建, FLAG_NOT_TOUCHABLE 不挡操作) */
    public void attachDimOverlay(View overlay) {
        this.dimOverlay = overlay;
        applyOverlay();
    }

    /** 从系统当前亮度恢复百分比 (对应原版 e9()) */
    public int restoreFromSystem() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            // API 29+: 优先浮点亮度 (0..1), 自动适配厂商扩展范围 (如 ColorOS 0..4095)
            try {
                float f = Settings.System.getFloat(context.getContentResolver(),
                        "screen_brightness_float");
                percent = Math.round(f * 100f);
            } catch (Exception e) {
                percent = restoreFromInt();
            }
        } else {
            percent = restoreFromInt();
        }
        if (percent > MAX_PERCENT) percent = MAX_PERCENT;
        if (percent < 1) percent = 1;
        return percent;
    }

    private int restoreFromInt() {
        try {
            int sys = Settings.System.getInt(context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS);
            return Math.round(sys * 100f / 255f);
        } catch (Settings.SettingNotFoundException e) {
            return 50;
        }
    }

    public int getPercent() {
        return percent;
    }

    /** 直接初始化百分比 (来自本应用持久化值), 不写回系统 */
    public void initPercent(int p) {
        if (p > MAX_PERCENT) p = MAX_PERCENT;
        if (p < 1) p = 1;
        percent = p;
    }

    /** 设置亮度百分比 ∈ [-50, 100] (对应原版 bf) */
    public void setPercent(int p) {
        if (p > MAX_PERCENT) p = MAX_PERCENT;
        if (p < MIN_PERCENT) p = MIN_PERCENT;
        percent = p;

        ContentResolver cr = context.getContentResolver();
        try {
            // 关闭自动亮度, 否则手动值会被覆盖
            if (Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 1) != 0) {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
            }
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                // 浮点亮度: 0..1, 系统自动映射厂商真实范围 (含 >255 的扩展范围)
                float f = (p >= 1 ? p : 1) / 100f;
                Settings.System.putFloat(cr, "screen_brightness_float", f);
            } else {
                int sys;
                if (p >= 1) {
                    sys = Math.round(p * 255f / 100f);
                } else {
                    sys = MIN_SYS_BRIGHTNESS;  // 低于下限, 交给遮罩
                }
                if (sys < MIN_SYS_BRIGHTNESS) sys = MIN_SYS_BRIGHTNESS;
                if (sys > MAX_SYS_BRIGHTNESS) sys = MAX_SYS_BRIGHTNESS;
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, sys);
            }
        } catch (Exception e) {
            // WRITE_SETTINGS 未授权时静默失败, 服务里会提前检查
        }
        applyOverlay();
        if (listener != null) {
            listener.onPercentChanged(p);
        }
    }

    /** 对应原版 jg(): 亮度 <= 0 时显示暗色蒙层 */
    private void applyOverlay() {
        if (dimOverlay == null) {
            return;
        }
        mainHandler.post(() -> {
            float alpha = 0f;
            if (percent <= 0) {
                // -100% → MAX_DIM_ALPHA
                alpha = Math.min(-percent, -MIN_PERCENT) / (float) (-MIN_PERCENT)
                        * (MAX_DIM_ALPHA / 255f);
            }
            dimOverlay.setBackgroundColor(((int) (alpha * 255f) << 24) & 0xFF000000);
            dimOverlay.setVisibility(percent <= 0 && alpha > 0f ? View.VISIBLE : View.GONE);
        });
    }
}
