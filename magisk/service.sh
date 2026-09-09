#!/system/bin/sh
# EdgeBright Magisk module - service.sh (late_start, root)
# root 悬浮方案: 用户应用 + root 强制授权 + root 拉起服务
# 不修改 /system 分区, 所有操作幂等, 每次开机执行

MODDIR=${0%/*}
PKG="com.edgebright"
APK="$MODDIR/app.apk"

# 等待系统完全启动 (包管理器就绪)
until [ "$(getprop sys.boot_completed)" = "1" ]; do
  sleep 2
done
sleep 5

# 版本指纹: APK 变化才重新安装, 避免每次开机重装
MD5=$(md5sum "$APK" 2>/dev/null | cut -d' ' -f1)
INSTALLED_MD5=$(cat "$MODDIR/.installed_md5" 2>/dev/null)

if [ ! -f "$APK" ]; then
  log -t EdgeBright "缺少 app.apk, 跳过"
  exit 0
fi

# 已在运行则不重复处理
if ! pidof "$PKG" >/dev/null 2>&1; then
  if [ "$MD5" != "$INSTALLED_MD5" ]; then
    pm install -r -g "$APK" >/dev/null 2>&1
    echo "$MD5" > "$MODDIR/.installed_md5"
  fi

  # root 强制授权 (即使 ROM 重置 appops, 每次开机都会重新授予)
  appops set "$PKG" SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1
  appops set "$PKG" WRITE_SETTINGS allow >/dev/null 2>&1
  pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1

  API=$(getprop ro.build.version.sdk)
  if [ "$API" -ge 26 ]; then
    am start-foreground-service -n "$PKG/.EdgeService" >/dev/null 2>&1
  else
    am startservice -n "$PKG/.EdgeService" >/dev/null 2>&1
  fi
fi

exit 0
