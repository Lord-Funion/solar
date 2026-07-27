#!/system/bin/sh
# Solar USB mass storage enable — setprop + vdc + LUN bind fallback (2026-07-05).
# Layman: switch USB to disk mode, share the right SD volume(s), wait until the PC can see a drive.
# Tech: MountService/app_process is flaky on Y1 API17; Y2 uses UmsEnabler when APK present, then vdc.
# Y1: /storage/sdcard0 only. Y2: /storage/sdcard0 (internal) + /storage/sdcard1 (MicroSD slot).
# Reversal: restore UmsEnabler-only path in UsbMassStorageController if shell enable is retired.

MODEL="$(getprop ro.product.model 2>/dev/null)"
# 2026-07-16 — Y1 dual-volume (sdcard0+sdcard1) like Y2; single-volume devices share what exists.
# Layman: export every user-visible card so the PC sees the same drives as the player.
# Was: Y1 only /storage/sdcard0 — second card never bound when present.
VOLS="/storage/sdcard0"
case "$MODEL" in
  Y2|*Y2*|Y1|*Y1*|A5|*A5*)
    VOLS=""
    for p in /storage/sdcard0 /storage/sdcard1; do
      [ -e "$p" ] && VOLS="$VOLS $p"
    done
    VOLS="${VOLS# }"
    [ -z "$VOLS" ] && VOLS="/storage/sdcard0"
    ;;
esac

# Session 705932 — append one NDJSON line for debug-mode analysis.
dbg_705932() {
  loc="$1"
  msg="$2"
  hyp="$3"
  extra="$4"
  ts="$(date +%s)"
  ts="${ts}000"
  usb="$(getprop sys.usb.config 2>/dev/null)"
  kern="$(cat /sys/class/android_usb/android0/functions 2>/dev/null)"
  line="{\"sessionId\":\"705932\",\"timestamp\":${ts},\"location\":\"${loc}\",\"message\":\"${msg}\",\"hypothesisId\":\"${hyp}\",\"data\":{\"usbConfig\":\"${usb}\",\"kernelFn\":\"${kern}\",${extra}}}"
  echo "$line" >>/data/local/tmp/debug-705932.log 2>/dev/null
  mkdir -p /storage/sdcard0/.solar 2>/dev/null
  echo "$line" >>/storage/sdcard0/.solar/debug-705932.log 2>/dev/null
}

# Session a3510d — append one NDJSON line for debug-mode analysis.
dbg_a3510d() {
  loc="$1"
  msg="$2"
  hyp="$3"
  extra="$4"
  ts="$(date +%s)"
  ts="${ts}000"
  line="{\"sessionId\":\"a3510d\",\"timestamp\":${ts},\"location\":\"${loc}\",\"message\":\"${msg}\",\"hypothesisId\":\"${hyp}\",\"data\":{${extra}}}"
  echo "$line" >>/data/local/tmp/debug-a3510d.log 2>/dev/null
  mkdir -p /storage/sdcard1/.solar 2>/dev/null
  echo "$line" >>/storage/sdcard1/.solar/debug-a3510d.log 2>/dev/null
}

# Read non-empty mass-storage backing path from dumpsys usb.
ums_backing_path() {
  line="$(dumpsys usb 2>/dev/null | grep 'Mass storage backing file:')"
  path="${line#*:}"
  path="${path#"${path%%[![:space:]]*}"}"
  echo "$path"
}

# True when any lun/file sysfs node lists a backing block device.
lun_bound() {
  for f in \
      /sys/class/android_usb/android0/f_mass_storage/lun/file \
      /sys/class/android_usb/android0/f_mass_storage/lun0/file \
      /sys/class/android_usb/android0/f_mass_storage/lun1/file; do
    if [ -r "$f" ]; then
      line="$(cat "$f" 2>/dev/null)"
      if [ -n "$line" ]; then
        return 0
      fi
    fi
  done
  return 1
}

# Wait until kernel USB functions include mass_storage — fast poll first (2026-07-05).
wait_kernel_ums() {
  i=0
  while [ "$i" -lt 24 ]; do
    KERN="$(cat /sys/class/android_usb/android0/functions 2>/dev/null)"
    echo "$KERN" | grep -q mass_storage && return 0
    if [ "$i" -lt 8 ]; then
      sleep 0.25
    else
      sleep 0.5
    fi
    i=$((i + 1))
  done
  return 1
}

# Recover JVM/kernel desync: UsbDeviceManager thinks UMS but kernel stayed on mtp.
recover_desync() {
  KERN="$(cat /sys/class/android_usb/android0/functions 2>/dev/null)"
  PROP="$(getprop sys.usb.config 2>/dev/null)"
  case "$KERN" in
    *mtp*) case "$PROP" in
      *mass_storage*)
        setprop sys.usb.config mtp,adb
        sleep 1
        i=0
        while [ "$i" -lt 8 ]; do
          KERN="$(cat /sys/class/android_usb/android0/functions 2>/dev/null)"
          echo "$KERN" | grep -q mtp && break
          sleep 0.5
          i=$((i + 1))
        done
        ;;
    esac ;;
  esac
}

# Push mass_storage,adb through setprop and optional sysfs write when setprop stalls.
# 2026-07-16 — Keep persist ON mass_storage for the armed session (re-enum must not snap back to adb).
# Layman: once you turn disk mode on, the PC keeps seeing the disk until you turn it off or unplug.
# Was: pin persist→adb immediately after enable — UsbDeviceManager re-applied adb after re-enum and
# killed mass_storage before the host could mount (LUN often left stale, UI then unlocked).
# Tech: MTK mirrors sys→persist and reloads Default Functions from persist on CONFIGURED.
# Boot + disable still clear sticky mass_storage (ensureNoStickyAutoUmsOnBoot / solar-disable-ums).
# Reversal: restore mid-enable persist pin only if a ROM reboots into auto-export without boot clear.
apply_kernel_ums() {
  setprop sys.usb.config mass_storage,adb
  # Match persist so re-enum / UsbDeviceManager Default Functions stay on disk mode.
  setprop persist.sys.usb.config mass_storage,adb
  if [ -d /data/property ]; then
    echo -n "mass_storage,adb" > /data/property/persist.sys.usb.config 2>/dev/null
    chmod 600 /data/property/persist.sys.usb.config 2>/dev/null
  fi
  if ! wait_kernel_ums; then
    if [ -w /sys/class/android_usb/android0/functions ]; then
      echo mass_storage,adb >/sys/class/android_usb/android0/functions 2>/dev/null
    fi
  fi
  wait_kernel_ums
}

# Resolve block node for one volume path via mounts, vdc list, or vold nodes.
block_for_volume() {
  vol="$1"
  blk=""
  while read -r dev mnt _rest; do
    if [ "$mnt" = "$vol" ]; then
      blk="$dev"
      break
    fi
  done < /proc/mounts 2>/dev/null
  # Y2 MicroSD is FUSE at /storage/sdcard1 — use media_rw block node (2026-07-05).
  if [ "$blk" = "/dev/fuse" ] || [ -z "$blk" ]; then
    case "$vol" in
      /storage/sdcard1)
        while read -r dev mnt _rest; do
          if [ "$mnt" = "/mnt/media_rw/sdcard1" ]; then
            blk="$dev"
            break
          fi
        done < /proc/mounts 2>/dev/null
        ;;
      /storage/sdcard0)
        while read -r dev mnt _rest; do
          if [ "$mnt" = "/mnt/media_rw/sdcard0" ]; then
            blk="$dev"
            break
          fi
        done < /proc/mounts 2>/dev/null
        ;;
    esac
  fi
  if [ -n "$blk" ] && [ -e "$blk" ]; then
    echo "$blk"
    return 0
  fi
  list="$(vdc volume list 2>/dev/null)"
  line="$(echo "$list" | grep "$vol" 2>/dev/null)"
  line="${line%%$'\n'*}"
  disk="$(echo "$line" | sed -n 's/.*disk:\([0-9]*,[0-9]*\).*/\1/p')"
  disk="${disk%%$'\n'*}"
  if [ -z "$disk" ]; then
    majmin="$(echo "$line" | sed -n 's/.*\/dev\/block\/vold\/\([0-9]*:[0-9]*\).*/\1/p')"
    majmin="${majmin%%$'\n'*}"
    if [ -n "$majmin" ]; then
      node="/dev/block/vold/$majmin"
      [ -e "$node" ] && echo "$node" && return 0
    fi
  fi
  if [ -n "$disk" ]; then
    node="/dev/block/vold/${disk/,/:}"
    [ -e "$node" ] && echo "$node" && return 0
    node="/dev/block/${disk/,/:}"
    [ -e "$node" ] && echo "$node" && return 0
  fi
  return 1
}

# Bind a block device to the first free mass-storage LUN sysfs node.
bind_block_to_lun() {
  blk="$1"
  for lun in \
      /sys/class/android_usb/android0/f_mass_storage/lun/file \
      /sys/class/android_usb/android0/f_mass_storage/lun0/file \
      /sys/class/android_usb/android0/f_mass_storage/lun1/file; do
    if [ -w "$lun" ]; then
      cur="$(cat "$lun" 2>/dev/null)"
      if [ -n "$cur" ]; then
        continue
      fi
      echo "$blk" >"$lun" 2>/dev/null && return 0
    fi
  done
  return 1
}

# Share every export volume through vold once kernel is on mass_storage.
# 2026-07-16 — Snapshot block nodes, unmount all, share all, then sysfs-bind any missing LUNs.
share_all_volumes() {
  blks=""
  for vol in $VOLS; do
    b="$(block_for_volume "$vol")"
    [ -n "$b" ] && blks="$blks $b"
  done
  blks="${blks# }"
  for vol in $VOLS; do
    vdc volume unmount "$vol" force 2>/dev/null
  done
  sleep 0.5
  for vol in $VOLS; do
    vdc volume share "$vol" ums 2>/dev/null
    sleep 0.5
  done
  # vdc share often binds only one LUN on dual-card Y1 — fill free LUNs from snapshot.
  for blk in $blks; do
    if [ -n "$blk" ] && [ -e "$blk" ]; then
      # Skip if already bound on some lun
      already=0
      for f in \
          /sys/class/android_usb/android0/f_mass_storage/lun/file \
          /sys/class/android_usb/android0/f_mass_storage/lun0/file \
          /sys/class/android_usb/android0/f_mass_storage/lun1/file; do
        cur="$(cat "$f" 2>/dev/null)"
        [ "$cur" = "$blk" ] && already=1
      done
      [ "$already" = "1" ] && continue
      bind_block_to_lun "$blk"
    fi
  done
  sleep 0.5
}

# Poll until dumpsys or lun sysfs shows a backing block device — fast early ticks (2026-07-05).
wait_lun_bound() {
  i=0
  while [ "$i" -lt 24 ]; do
    backing="$(ums_backing_path)"
    if [ -n "$backing" ] || lun_bound; then
      [ -n "$backing" ] && echo "UMS enabled lun=$backing"
      [ -z "$backing" ] && echo "UMS enabled lun=sysfs"
      return 0
    fi
    if [ "$i" -lt 6 ]; then
      sleep 0.25
    else
      sleep 0.5
    fi
    i=$((i + 1))
  done
  return 1
}

# Direct LUN bind when vdc share never populated lun/file (Y1 API17 fallback).
# 2026-07-16 — Snapshot block nodes while mounted, unmount, then bind (mounted bind often no-ops).
bind_fallback() {
  # Capture block paths before unmount empties /proc/mounts.
  blks=""
  for vol in $VOLS; do
    b="$(block_for_volume "$vol")"
    [ -n "$b" ] && blks="$blks $b"
  done
  blks="${blks# }"
  for vol in $VOLS; do
    vdc volume unmount "$vol" force 2>/dev/null
  done
  sleep 0.5
  bound_any=0
  for blk in $blks; do
    if [ -n "$blk" ] && [ -e "$blk" ] && bind_block_to_lun "$blk"; then
      bound_any=1
      sleep 0.5
    fi
  done
  if [ "$bound_any" = "1" ] && wait_lun_bound; then
    return 0
  fi
  # Last resort: try resolve again (some images keep vold nodes after unmount).
  for vol in $VOLS; do
    blk="$(block_for_volume "$vol")"
    if [ -n "$blk" ] && bind_block_to_lun "$blk"; then
      bound_any=1
      sleep 0.5
    fi
  done
  if [ "$bound_any" = "1" ] && wait_lun_bound; then
    return 0
  fi
  return 1
}

# Resolve Solar APK for app_process — pm/data first; stale /system copy last (2026-07-05).
resolve_solar_apk() {
  line="$(pm path com.solar.launcher 2>/dev/null)"
  case "$line" in
    package:*) echo "${line#package:}"; return 0 ;;
  esac
  for p in \
      /data/app/com.solar.launcher-2.apk \
      /data/app/com.solar.launcher-1.apk \
      /system/app/com.solar.launcher.apk; do
    [ -f "$p" ] && echo "$p" && return 0
  done
  return 1
}

# Build export list from vdc — only share volumes vold knows about (2026-07-05).
y2_export_volumes() {
  listed=""
  vdc volume list 2>/dev/null | while read -r line; do
    case "$line" in
      *Volumes\ listed*) continue ;;
    esac
    set -- $line
    path="$3"
    case "$path" in
      /storage/*)
        case "$path" in
          *usbotg*) ;;
          /storage/emulated/0|/storage/emulated/0/*) ;;
          *) echo "$path" ;;
        esac
        ;;
    esac
  done > /data/local/tmp/solar-y2-vols.$$
  if [ -s /data/local/tmp/solar-y2-vols.$$ ]; then
    listed="$(cat /data/local/tmp/solar-y2-vols.$$)"
    rm -f /data/local/tmp/solar-y2-vols.$$
    echo "$listed"
    return 0
  fi
  rm -f /data/local/tmp/solar-y2-vols.$$
  for p in /storage/sdcard0 /storage/emulated/legacy /storage/sdcard1; do
    [ -e "$p" ] && listed="$listed $p"
  done
  echo "$listed"
}

# Share every vold-listed Y2 volume — internal + MicroSD when both mounted.
share_y2_volumes() {
  for vol in $(y2_export_volumes); do
    vdc volume share "$vol" ums 2>/dev/null
    sleep 1
  done
}

# Bind block devices for each export volume to free LUN nodes (Mac dual-disk fallback).
bind_y2_dual_lun() {
  for vol in $(y2_export_volumes); do
    blk="$(block_for_volume "$vol")"
    if [ -n "$blk" ]; then
      bind_block_to_lun "$blk"
      sleep 0.5
    fi
  done
  wait_lun_bound
}

# Y2 fallback: UmsEnabler enable only — no disable pass that forces MTP (2026-07-05).
enable_y2_ums_enabler() {
  dbg_a3510d "solar-enable-ums.enable_y2" "start" "H2" "\"model\":\"$MODEL\""
  APK="$(resolve_solar_apk)" || return 1
  PRIMARY="/storage/sdcard1"
  for vol in $(y2_export_volumes); do
    PRIMARY="$vol"
    break
  done
  export CLASSPATH="$APK"
  app_process /system/bin com.solar.launcher.UmsEnabler 1 "$PRIMARY" 2>/dev/null
  sleep 4
  share_y2_volumes
  if wait_lun_bound; then
    dbg_a3510d "solar-enable-ums.enable_y2" "ok enabler" "H2" "\"usb\":\"$(getprop sys.usb.config)\""
    return 0
  fi
  bind_y2_dual_lun && return 0
  dbg_a3510d "solar-enable-ums.enable_y2" "fail" "H2,H3,H4" "\"usb\":\"$(getprop sys.usb.config)\""
  return 1
}

# True when every export volume has a non-empty LUN (dual-card Y1 must export both).
all_luns_for_vols() {
  need=0
  for _v in $VOLS; do
    need=$((need + 1))
  done
  [ "$need" -le 0 ] && need=1
  have=0
  for f in \
      /sys/class/android_usb/android0/f_mass_storage/lun/file \
      /sys/class/android_usb/android0/f_mass_storage/lun0/file \
      /sys/class/android_usb/android0/f_mass_storage/lun1/file; do
    if [ -r "$f" ]; then
      line="$(cat "$f" 2>/dev/null)"
      [ -n "$line" ] && have=$((have + 1))
    fi
  done
  [ "$have" -ge "$need" ]
}

# Y1 / fallback: setprop sync + vdc share + sysfs bind fallback.
# 2026-07-16 — Wait for USB re-enum to finish before share/bind; re-bind if re-enum clears LUNs.
enable_setprop_vdc() {
  dbg_705932 "solar-enable-ums.enable_setprop_vdc" "start" "H3" "\"model\":\"$MODEL\""
  recover_desync
  if ! apply_kernel_ums; then
    dbg_705932 "solar-enable-ums.enable_setprop_vdc" "kernel fail" "H3" "\"model\":\"$MODEL\""
    echo "UMS enable failed: kernel never reached mass_storage ($MODEL)" >&2
    return 2
  fi
  # Re-enum after setprop mass_storage can wipe lun/file if we share too early.
  # Layman: give the cable a moment to finish “disk mode” before attaching cards.
  sleep 2
  if ! wait_kernel_ums; then
    dbg_705932 "solar-enable-ums.enable_setprop_vdc" "kernel lost after settle" "H3" "\"model\":\"$MODEL\""
    echo "UMS enable failed: mass_storage lost during re-enum ($MODEL)" >&2
    return 2
  fi
  # Snapshot blocks while still mounted, then unmount+share+sysfs bind.
  share_all_volumes
  if ! all_luns_for_vols; then
    bind_fallback
  fi
  # Second pass: re-enum sometimes clears the first LUN after the second share.
  sleep 1
  if ! all_luns_for_vols; then
    bind_fallback
  fi
  if wait_lun_bound; then
    # Prefer dual bind when two volumes exist; single LUN still counts as success.
    dbg_705932 "solar-enable-ums.enable_setprop_vdc" "ok" "H3" "\"model\":\"$MODEL\",\"dual\":\"$(all_luns_for_vols && echo 1 || echo 0)\""
    return 0
  fi
  dbg_705932 "solar-enable-ums.enable_setprop_vdc" "fail" "H3" "\"model\":\"$MODEL\""
  return 1
}

case "$MODEL" in
  Y2|*Y2*)
    recover_desync
    # Primary: UmsEnabler mass_storage path + vdc — uses pm-installed APK, syncs MountService (2026-07-05).
    if enable_y2_ums_enabler; then
      exit 0
    fi
    if enable_setprop_vdc; then
      exit 0
    fi
    ;;
  *)
    enable_setprop_vdc
    ;;
esac

if wait_lun_bound || lun_bound; then
  exit 0
fi

echo "UMS enable failed: mass-storage LUN not bound ($MODEL vols=$VOLS)" >&2
exit 2
