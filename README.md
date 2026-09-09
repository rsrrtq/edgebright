# 超低亮度调节 · EdgeBright 全局边缘滑动调亮度

> Magisk 模块（root 悬浮方案）· 支持低于系统最低亮度的夜间超暗模式

把「搜书大师」阅读页的边缘滑动调亮度手势做成系统级全局功能。
**root 悬浮方案**：APK 作为普通用户应用安装（root 脚本自动完成），
悬浮窗与写设置权限由 root 通过 appops 强制授予，完全不修改 /system 分区。

## 原理 (逆向自搜书大师 v23.15, ActivityTxt)

| 环节 | 原版实现 | 本模块实现 |
|---|---|---|
| 手势捕获 | 阅读页 onTouch 事件 | 左/右边缘透明悬浮条 (SYSTEM_ALERT_WINDOW) |
| 边缘带 | 40dp 宽命中带, 起点终点都在带内 | 同样 40dp (App 内可调 20~60dp) |
| 去抖 | 位移 > 20dp 才生效 | 相同 |
| 步进 | (速度÷400~500 + 1) × \|位移\| ÷ 20dp | 相同 (VelocityTracker, 上限 10000px/s) |
| 亮度写入 | 本窗口 screenBrightness | 全局 Settings.System.SCREEN_BRIGHTNESS (自动关自动亮度) |
| 低于下限 | shadeView 遮罩继续压暗 | 全屏 FLAG_NOT_TOUCHABLE 遮罩, 同思路 |
| 反馈 | Toast "N%" | 常驻通知显示当前百分比 |

亮度范围 -100% ~ 100%: 1~100 写系统亮度; ≤0 时系统亮度压到下限、
遮罩逐级加深至约 92% 黑度, 远低于系统最低亮度 (深夜全黑房间可用,
OLED 屏效果尤佳)。

## 安装

1. Magisk → 模块 → 从本地安装 → 选择 `edgebright-v1.1.2.zip`
2. 重启。每次开机 root 脚本自动:
   - `pm install` 安装/更新 EdgeBright (用户应用, 不碰 /system)
   - `appops` 强制授予悬浮窗 (SYSTEM_ALERT_WINDOW) 与写设置 (WRITE_SETTINGS)
   - root 拉动手势前台服务 (即使在后台限制下)

APK 指纹 (md5) 未变化时跳过重装, 开机开销可忽略。

## 使用

- 在屏幕左边缘(默认, 可开右边缘)按住并**上下滑动**: 上滑变亮, 下滑变暗
- 快滑一次多级变化, 慢滑精确微调 (速度加权)
- 通知栏常驻显示当前亮度百分比
- 打开 "EdgeBright" App 可调: 左/右边缘开关、边缘带宽度 (20~60dp)

## 已知限制

- 边缘带内的触摸会被手势条捕获, 该窄条区域的点击/滑动不会传给底层应用。
  如需在边缘操作, 把带宽调到 20dp 或关闭该侧手势
- **状态栏最顶部一条 (~120px, 时间/WiFi/电量所在) 无法捕获触摸** —— 那是 StatusBar
  系统窗口的保留区, 任何应用悬浮窗都在其下层, 属 Android 平台规则
  (压暗遮罩仍会覆盖该区域, 视觉上不会漏亮)
- 首次滑动会自动关闭系统自动亮度
- 需要 Android 7.0+；悬浮窗/写设置由 root 每次开机强制授予,
  ROM 即使重置 appops 也会在下一次开机恢复, 无需手动授权

## 更新日志

### v1.1.4 (隐私清理 + 重签名)
- **重签 APK**: 旧 `signing.keystore` 不慎被提交到公开仓库历史, 已作废;
  重新生成新密钥签发 v1.1.4, 旧版本号无法直接覆盖安装, 需**卸载旧版再装新版**
- **隐私清理**: git 历史已用 filter-repo 重写, 删除了 keystore + 匿名化所有 commit
  作者/邮箱; module.prop author 改为项目名
- build.sh 移除绝对路径, 基于脚本自身位置自动定位; 改用通用 shell 兼容
- 删除 v1.1.0/v1.1.1/v1.1.2/v1.1.3 所有 GitHub Release (它们都包含泄露密钥签的 APK)

### v1.1.3 (系统栏同步压暗, 已在 OnePlus/ColorOS Android 15 验证)
- 修复 **状态栏/通知栏/底部手势条不变暗**: 压暗遮罩升级为
  `TYPE_ACCESSIBILITY_OVERLAY` 层级 (位于所有系统 UI 窗口之上),
  全屏均匀压暗无漏亮
- 新增 `DimAccessibilityService` 无障碍通道 (只提供窗口层级, 不读取任何屏幕内容);
  该层级窗口必须用无障碍服务自身 Context 添加, 否则 BadTokenException
- service.sh 开机用 root 自动启用无障碍服务 (追加, 不影响用户已启用的其他无障碍服务)
- 无障碍被手动关闭时自动降级回普通悬浮层, 应用区压暗不丢

### v1.1.2 (超暗加深)
- 超暗区间从 -50% 扩展到 **-100%**, 遮罩最大黑度从 ~72% 加深到 **~92%**
- 继续在边缘下滑即可一路压到全黑边缘

### v1.1.1 (真机修复, 已在 OnePlus/ColorOS Android 15 验证)
- 修复 **Android 14/15 服务启动即崩溃**: specialUse 前台服务需 `FOREGROUND_SERVICE_SPECIAL_USE` 权限
- 悬浮条与压暗遮罩改为全屏铺满, 覆盖状态栏与底部手势条区域 (此前上下各被裁掉约 85px)
- 手势基准值与系统亮度同步; 重启后优先用保存的百分比恢复
- 适配厂商扩展亮度范围 (如 ColorOS 0~2047, 不再被 255 封顶)
- service.sh 增加运行日志与启动重试; 构建脚本编译失败立即终止

### v1.1.0
- root 悬浮方案: 用户应用 + appops 强授悬浮窗/写设置, 不修改 /system

### v1.0.0
- 首个版本, 手势算法移植自搜书大师 v23.15

## 卸载

Magisk 中移除模块会同时卸载 EdgeBright 应用并停止服务。

## 目录

```
app/    Android 应用源码 (纯 android.* API, 零依赖)
magisk/ Magisk 模块脚本 (module.prop / customize.sh / service.sh / ...)
build.sh  APK 构建脚本 (aapt2 + javac + d8 + apksigner, 无需 gradle)
```

手势算法细节与逆向分析见对话记录; 核心移植文件:
`app/src/com/edgebright/EdgeGestureDetector.java` (对应 ActivityTxt.N6/he/pb/E7)
