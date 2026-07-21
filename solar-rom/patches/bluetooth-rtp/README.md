# AirPods RTP silence fix (Y1)

2026-07-19 — Vendored from [Semy0nBu/y1-airpods-rtpfix](https://github.com/Semy0nBu/y1-airpods-rtpfix)
(GPL-3.0). Solar builds `prebuilt/libbluetoothdrv.so` and bakes it only when stock
`/system/lib/libbluetoothdrv.so` MD5 matches official Y1 3.0.7:

`32f1af87e46acaf1efa3f083340495cb`

## Layout after install

| Path | Role |
|------|------|
| `/system/lib/libbluetoothdrv.so` | RTP timestamp normalize proxy |
| `/system/lib/libbluetoothdrv_real.so` | Untouched stock driver |
| `/system/lib/libmtkbtextadpa2dp.so` | Untouched (never patch) |

## Build

```bash
./solar-rom/patches/bluetooth-rtp/build-airpods-rtp-proxy.sh
# or: ANDROID_NDK_ROOT=/path/to/ndk ./build-airpods-rtp-proxy.sh
```

## ROM bake

`build-rom.sh` calls `install-airpods-rtp-proxy.sh` for **Y1 type a/b only**.
Y2/A5 soft-skip on MD5 mismatch.

## Reversal

```bash
mv /system/lib/libbluetoothdrv_real.so /system/lib/libbluetoothdrv.so
# reboot
```
