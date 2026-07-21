#!/usr/bin/env bash
# 2026-07-19 — Shared bluetooth pairing conf checks for verify-*-rom-contents.sh (debugfs).
# Expects: $sys = system.img path, fail() function, debugfs_cat helper.
# Soft-skips when /etc/bluetooth is missing.
verify_bluetooth_pairing_conf_debugfs() {
    local sys_img="${1:-$sys}"
    if ! debugfs -R "stat /etc/bluetooth" "$sys_img" 2>/dev/null | grep -q 'Type: directory'; then
        echo "  bluetooth pairing conf: soft-skip (no /etc/bluetooth)"
        return 0
    fi
    local audio
    audio="$(debugfs_cat /etc/bluetooth/audio.conf)"
    echo "$audio" | grep -q '^Enable=Source,Control,Target' \
        || fail "audio.conf missing Enable=Source,Control,Target"
    echo "$audio" | grep -q '^Master=true' \
        || fail "audio.conf missing Master=true"
    local ap
    ap="$(debugfs_cat /etc/bluetooth/auto_pairing.conf)"
    if [ -n "$ap" ]; then
        echo "$ap" | grep -q '^AddressBlacklist=$' \
            || fail "auto_pairing.conf AddressBlacklist not cleared"
    fi
    local bl
    bl="$(debugfs_cat /etc/bluetooth/blacklist.conf)"
    if echo "$bl" | grep -q '^scoSocket'; then
        fail "blacklist.conf still has scoSocket lines"
    fi
    local prop
    prop="$(debugfs_cat /build.prop)"
    echo "$prop" | grep -q '^ro\.bluetooth\.class=10486812' \
        || fail "build.prop missing ro.bluetooth.class=10486812"
    echo "$prop" | grep -q '^ro\.bluetooth\.profiles\.a2dp\.source\.enabled=true' \
        || fail "build.prop missing a2dp.source.enabled"
    echo "$prop" | grep -q '^ro\.bluetooth\.profiles\.avrcp\.target\.enabled=true' \
        || fail "build.prop missing avrcp.target.enabled"
    if echo "$prop" | grep -q '^persist\.bluetooth\.avrcpversion='; then
        fail "build.prop must not set persist.bluetooth.avrcpversion"
    fi
    echo "  bluetooth pairing conf: OK"
}
