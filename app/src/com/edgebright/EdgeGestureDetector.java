package com.edgebright;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;

/**
 * 边缘滑动亮度手势识别器 —— 算法忠实移植自 搜书大师 v23.15
 * ActivityTxt.N6() / he() / pb() / E7() (workspace zkuwj3cp)
 *
 * 原版逻辑:
 *  1. 边缘带宽 B8() = 40dp (默认)
 *  2. 有效位移阈值 20dp, 低于此忽略 (去抖)
 *  3. 步进 = (|速度|/rate + 1) * |位移| / 20dp
 *     增亮 rate=500, 减暗 rate=400 (慢速滑动时速度快慢影响更明显)
 *  4. 速度上限 10000 px/s
 *  5. 亮度百分比范围: -50..100, 1..100 为真实亮度,
 *     <=0 时窗口/系统亮度压到下限并叠加遮罩继续压暗
 */
public class EdgeGestureDetector {

    public interface Callback {
        /** 亮度百分比变化, percent ∈ [-50, 100] */
        void onBrightnessChanged(int percent);
    }

    private static final float VELOCITY_CAP = 10000f;
    private static final float RATE_INCREASE = 500f;  // 上滑增亮
    private static final float RATE_DECREASE = 400f;  // 下滑减暗
    private static final int MIN_PERCENT = -150;
    private static final int MAX_PERCENT = 100;

    private final Callback callback;
    private final float density;

    private VelocityTracker velocityTracker;
    /** 上次采样的滑动轴坐标 (对应原版 S3 字段, -1 表示未初始化) */
    private float lastAxisPos = -1f;
    private int percent;

    public EdgeGestureDetector(Context context, Callback callback) {
        this.callback = callback;
        this.density = context.getResources().getDisplayMetrics().density;
    }

    /** 由服务持有, 记录当前亮度百分比 (例如从系统设置恢复) */
    public void setCurrentPercent(int percent) {
        this.percent = percent;
    }

    public int getCurrentPercent() {
        return percent;
    }

    /** 20dp 阈值 (原版: 20.0f dp 转 px) */
    private float moveThreshold() {
        return 20f * density;
    }

    public void onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastAxisPos = -1f;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                }
                velocityTracker = VelocityTracker.obtain();
                velocityTracker.addMovement(event);
                break;

            case MotionEvent.ACTION_MOVE:
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }
                velocityTracker.addMovement(event);
                velocityTracker.computeCurrentVelocity(1000);
                handleMove(event.getY(), Math.abs(velocityTracker.getYVelocity()));
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                lastAxisPos = -1f;
                break;
        }
    }

    private void handleMove(float axisPos, float velocity) {
        // 原版: S3 == -1 时记录初始位置, 不调节
        if (lastAxisPos == -1f) {
            lastAxisPos = axisPos;
            return;
        }

        // 原版: delta = 上次位置 - 当前位置; 上滑(值减小) delta > 0 → 增亮
        float delta = lastAxisPos - axisPos;
        float absDelta = Math.abs(delta);
        float threshold = moveThreshold();
        if (absDelta <= threshold) {
            return;
        }

        if (velocity > VELOCITY_CAP) {
            velocity = VELOCITY_CAP;
        }

        float rate = delta > 0 ? RATE_INCREASE : RATE_DECREASE;
        // 原版步进公式: (速度/rate + 1) * |位移| / 20dp
        int step = (int) ((velocity / rate + 1f) * absDelta / threshold);
        if (step < 1) {
            step = 1;
        }

        int old = percent;
        int next = old + (delta > 0 ? step : -step);

        // 原版 N6 的阶梯钳制: 单次不低于 2/1, 防止跳变
        if (old > 2 && next < 2) next = 2;
        else if (old > 1 && next < 1) next = 1;
        if (next > MAX_PERCENT) next = MAX_PERCENT;
        if (next < MIN_PERCENT) next = MIN_PERCENT;

        lastAxisPos = axisPos;
        if (next != old) {
            percent = next;
            callback.onBrightnessChanged(percent);
        }
    }

    public void release() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }
}
