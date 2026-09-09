package com.edgebright;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

/**
 * 无障碍通道服务 (不做任何无障碍操作, 仅用于获得 ACCESSIBILITY_OVERLAY 窗口层级)。
 *
 * TYPE_ACCESSIBILITY_OVERLAY 窗口必须用无障碍服务自身的 Context 添加
 * (该 Context 带有系统签发的窗口 token, 普通 Service 会抛 BadTokenException),
 * 所以本服务把自身实例暴露给 EdgeService, 由后者在其 Context 上创建压暗遮罩。
 * 由 Magisk service.sh 用 root 自动启用, 无需用户手动开启。
 */
public class DimAccessibilityService extends AccessibilityService {

    /** 当前已连接的无障碍通道实例 (供 EdgeService 取 Context 创建遮罩) */
    static volatile DimAccessibilityService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // 通道就绪后通知 EdgeService 重建遮罩 (升级到 ACCESSIBILITY_OVERLAY 层)
        sendRebuild();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // 用户在系统设置里关掉本服务时回调: 通知降级回普通悬浮层, 保住应用区压暗
        instance = null;
        sendRebuild();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    private void sendRebuild() {
        Intent i = new Intent(this, EdgeService.class);
        i.setAction(EdgeService.ACTION_REBUILD_OVERLAY);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(i);
            } else {
                startService(i);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
