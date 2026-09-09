#!/system/bin/sh
# EdgeBright Magisk module - uninstall.sh
# 模块移除时: 停服务 + 卸载应用 (用户应用, 可直接 pm uninstall)

PKG="com.edgebright"

am force-stop "$PKG" >/dev/null 2>&1

if command -v pm >/dev/null 2>&1; then
  pm uninstall --user 0 "$PKG" >/dev/null 2>&1
fi

exit 0
