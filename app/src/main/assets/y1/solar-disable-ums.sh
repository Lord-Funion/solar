#!/system/bin/sh
# Solar USB mass storage disable (2026-07-10).
# Layman: stop sharing disks with the PC.
# Y1: unshare + USB=adb only (no MTP — PC uses disk mode when user turns UMS on).
# Y2: unshare + USB=mtp,adb (product uses MTP; UMS not supported).

MODEL="$(getprop ro.product.model 2>/dev/null)"
# 2026-07-16 — Unshare every known export path (Y1 often has both sdcard0 and sdcard1).
VOLS="/storage/sdcard0 /storage/sdcard1"
USB_AFTER="adb"
case "$MODEL" in
  Y2|*Y2*)
    USB_AFTER="mtp,adb"
    ;;
esac

# ponytail: Y2 is MTP-only, do not unshare (breaks MTK vold MTP visibility).
if [ "$USB_AFTER" != "mtp,adb" ]; then
  for vol in $VOLS; do
    vdc volume unshare "$vol" ums 2>/dev/null
  done
fi

# 2026-07-17 — Was sleep 0.5; shorter settle so unplug after disk mode feels snappy.
# LUN clear + setprop below are enough for host drop; long sleep stacked with dual teardown jank.
sleep 0.12
# Always clear LUN nodes before mode switch — stale lun/file caused false “USB storage on”.
for lun in \
    /sys/class/android_usb/android0/f_mass_storage/lun/file \
    /sys/class/android_usb/android0/f_mass_storage/lun0/file \
    /sys/class/android_usb/android0/f_mass_storage/lun1/file; do
  if [ -w "$lun" ]; then
    echo >"$lun" 2>/dev/null
  fi
done
setprop sys.usb.config "$USB_AFTER"
# 2026-07-16 — Never leave mass_storage sticky across reboot/reconnect.
# Layman: turning disk mode off also forgets “always show as a disk”.
# Tech: MediaTek UsbDeviceManager copies sys.usb.config → persist.sys.usb.config on enable.
setprop persist.sys.usb.config "$USB_AFTER"
if [ -d /data/property ]; then
  echo -n "$USB_AFTER" > /data/property/persist.sys.usb.config 2>/dev/null
  chmod 600 /data/property/persist.sys.usb.config 2>/dev/null
fi

# Kick MediaScanner for MTP so PC sees storage if we just switched to mtp,adb
if [ "$USB_AFTER" = "mtp,adb" ]; then
  # Slight delay to let MtpService start
  sleep 1
  am broadcast -a android.intent.action.MEDIA_MOUNTED -d file:///storage/sdcard0 >/dev/null 2>&1
  am broadcast -a android.intent.action.MEDIA_MOUNTED -d file:///storage/sdcard1 >/dev/null 2>&1
fi

echo "UMS disabled usb=$USB_AFTER persist=$USB_AFTER"
exit 0
