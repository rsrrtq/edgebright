#!/system/bin/sh
# EdgeBright Magisk module - service.sh (late_start, root)
# root 悬浮方案: 用户应用 + root 强制授权 + root 拉起服务
# 不修改 /system 分区, 所有操作幂等, 每次开机执行

MODDIR=${0%/*}
PKG="com.edgebright"
# Magisk 会提供 $MODPATH; APatch 不提供 (为空时 "$MODPATH/app.apk" 会变成 /app.apk),
# 所以默认用模块自身目录, 只在 $MODPATH 下确实存在时才优先用它
APK="$MODDIR/app.apk"
if [ -n "$MODPATH" ] && [ -f "$MODPATH/app.apk" ]; then
  APK="$MODPATH/app.apk"
fi

exec > "$MODDIR/service.log" 2>&1
echo "=== EdgeBright service.sh $(date) ==="

# 等待系统完全启动 (包管理器就绪)
until [ "$(getprop sys.boot_completed)" = "1" ]; do
  sleep 2
done
sleep 5

if [ ! -f "$APK" ]; then
  echo "缺少 app.apk ($APK), 跳过"
  exit 0
fi

# 版本指纹: APK 变化才重新安装, 避免每次开机重装
MD5=$(md5sum "$APK" 2>/dev/null | cut -d' ' -f1)
INSTALLED_MD5=$(cat "$MODDIR/.installed_md5" 2>/dev/null)

if [ "$MD5" != "$INSTALLED_MD5" ]; then
  echo "安装/更新 APK (md5=$MD5)"
  pm install -r -g "$APK" && echo "$MD5" > "$MODDIR/.installed_md5"
fi

# 启用无障碍通道 (root 自动开启): 压暗遮罩由此获得 ACCESSIBILITY_OVERLAY 层级,
# 才能盖过状态栏/通知栏/手势条。保留用户已启用的其他无障碍服务。
ACC_SVC="$PKG/com.edgebright.DimAccessibilityService"
CUR=$(settings get secure enabled_accessibility_services)
case ";$CUR;" in
  *";$ACC_SVC;"*) echo "无障碍通道已启用" ;;
  *)
    if [ -z "$CUR" ] || [ "$CUR" = "null" ]; then
      NEW="$ACC_SVC"
    else
      NEW="$CUR:$ACC_SVC"
    fi
    settings put secure enabled_accessibility_services "$NEW"
    echo "已追加无障碍通道: $NEW"
    ;;
esac
settings put secure accessibility_enabled 1

# 已在运行则不重复处理
if pidof "$PKG" >/dev/null 2>&1; then
  echo "已在运行, 跳过"
  exit 0
fi

# root 强制授权 (即使 ROM 重置 appops, 每次开机都会重新授予)
appops set "$PKG" SYSTEM_ALERT_WINDOW allow
appops set "$PKG" WRITE_SETTINGS allow
pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null
# 无障碍通道自愈所需: 通道被系统关闭时应用可自行恢复
pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS 2>/dev/null

start_svc() {
  if [ "$(getprop ro.build.version.sdk)" -ge 26 ]; then
    am start-foreground-service -n "$PKG/.EdgeService"
  else
    am startservice -n "$PKG/.EdgeService"
  fi
}

start_svc
sleep 4

# 首次启动可能因包管理器未就绪失败, 重试一次
if ! pidof "$PKG" >/dev/null 2>&1; then
  echo "首次启动失败, 重试"
  start_svc
  sleep 3
fi

if pidof "$PKG" >/dev/null 2>&1; then
  echo "服务已启动 pid=$(pidof "$PKG")"
else
  echo "警告: 服务未能启动, 可手动打开 EdgeBright 应用点启动"
fi

exit 0
