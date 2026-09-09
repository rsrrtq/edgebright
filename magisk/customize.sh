#!/system/bin/sh
# EdgeBright Magisk module - customize.sh
# root 悬浮方案: 不写 /system, APK 保留在模块目录,
# 由 service.sh 在每次开机时 root 安装 + 强制授权 + 拉起

SKIPUNZIP=0

ui_print "******************************"
ui_print " EdgeBright 全局边缘滑动调亮度"
ui_print "   (root 悬浮方案 v1.1.0)"
ui_print "******************************"
ui_print ""
ui_print " 手势算法移植自 搜书大师 v23.15"
ui_print "  - 40dp 边缘带命中 (App 内可调 20~60dp)"
ui_print "  - 20dp 位移阈值去抖"
ui_print "  - 速度加权步进 (快滑多调, 慢滑微调)"
ui_print "  - 低于系统下限自动遮罩压暗 (夜间更暗)"
ui_print ""
ui_print " 方案: 普通应用 + root 强制授权悬浮窗/写设置"
ui_print "       不修改 /system 分区"
ui_print ""

if [ "$API" -lt 24 ]; then
  abort "! 需要 Android 7.0 (API 24) 及以上"
fi

if [ ! -f "$MODPATH/app.apk" ]; then
  abort "! 模块包缺少 app.apk, 包损坏"
fi

chmod 755 "$MODPATH/service.sh" "$MODPATH/post-fs-data.sh" "$MODPATH/uninstall.sh"

# 在 Magisk Manager 里安装时 (已开机), 立即安装并授权;
# Recovery 安装时 pm 不可用, 留给 service.sh 开机处理
if command -v pm >/dev/null 2>&1; then
  ui_print "- 正在安装应用..."
  pm install -r -g "$MODPATH/app.apk" >/dev/null 2>&1
  appops set com.edgebright SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1
  appops set com.edgebright WRITE_SETTINGS allow >/dev/null 2>&1
  pm grant com.edgebright android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
  md5sum "$MODPATH/app.apk" | cut -d' ' -f1 > "$MODPATH/.installed_md5"
  ui_print "- 已安装并授权, 重启后服务自启"
else
  ui_print "- Recovery 环境, 重启后自动安装并授权"
fi

ui_print ""
ui_print "- 注意: 边缘带内的触摸会被手势捕获,"
ui_print "  若影响边缘操作, 打开 App 把带宽调小或关闭某侧"
ui_print ""
ui_print " 安装完成, 请重启"
