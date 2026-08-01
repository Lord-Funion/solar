#!/usr/bin/env bash
set -euo pipefail

ROOT="${GITHUB_WORKSPACE:-$(pwd)}"
ROCKBOX_REF="${ROCKBOX_REF:-ec32a663dfb8396080227de03846c30f07dc258d}"
ROCKBOX_RELEASE_TAG="${ROCKBOX_RELEASE_TAG:-stable-v0.5}"
LOGS="$ROOT/logs"
BUILD="$ROOT/firmware-build"
INPUTS="$ROOT/firmware-inputs"
DIST="$ROOT/dist/type-a"
SOURCE_DIST="$ROOT/dist/source"

mkdir -p "$LOGS" "$BUILD" "$INPUTS" "$DIST" "$SOURCE_DIST"

log_run() {
    local logfile="$1"
    shift
    "$@" 2>&1 | tee "$LOGS/$logfile"
}

python3 "$ROOT/firmware/apply_native.py" "$ROOT/rockbox-y1" \
    2>&1 | tee "$LOGS/01-native-integration.log"
git -C "$ROOT/rockbox-y1" diff --stat | tee "$LOGS/02-rockbox-diff-stat.log"
git -C "$ROOT/rockbox-y1" diff --check | tee "$LOGS/03-rockbox-diff-check.log"
git -C "$ROOT/rockbox-y1" diff > "$SOURCE_DIST/rockbox-solar-native.patch"
cp "$ROOT/firmware/apply_native.py" "$SOURCE_DIST/"
cp "$ROOT/firmware/native/solar.c" "$SOURCE_DIST/"
cp "$ROOT/firmware/native/solar.cfg" "$SOURCE_DIST/"
cp -r "$ROOT/firmware/native/solarctl" "$SOURCE_DIST/"
if grep -R -n -E 'dev\.lordfunion\.rockboxsolar|am start|android\.app\.Activity' \
    "$ROOT/rockbox-y1/apps/plugins/solar.c" "$ROOT/firmware/native/solarctl"; then
    echo 'Android launcher/activity reference found in native source' >&2
    exit 1
fi

pushd "$ROOT/firmware/native/solarctl" >/dev/null
gofmt -w .
go mod tidy 2>&1 | tee "$LOGS/04-go-mod-tidy.log"
go mod verify 2>&1 | tee "$LOGS/05-go-mod-verify.log"
go test ./... 2>&1 | tee "$LOGS/06-go-test.log"
go vet ./... 2>&1 | tee "$LOGS/07-go-vet.log"
CGO_ENABLED=0 GOOS=linux GOARCH=arm GOARM=7 \
    go build -trimpath -buildvcs=false -ldflags='-s -w -buildid=' \
    -o "$BUILD/solarctl" . 2>&1 | tee "$LOGS/08-go-arm-build.log"
popd >/dev/null
chmod 0755 "$BUILD/solarctl"
file "$BUILD/solarctl" | tee "$LOGS/09-solarctl-file.log"
readelf -h "$BUILD/solarctl" | tee "$LOGS/10-solarctl-readelf.log"
if readelf -d "$BUILD/solarctl" 2>&1 | grep -q NEEDED; then
    echo 'solarctl unexpectedly has dynamic dependencies' >&2
    exit 1
fi
for feature in piped_search deezer_search slskd_search ssh_exec scp_get scp_put stem_split; do
    strings "$BUILD/solarctl" | grep -F "$feature" >/dev/null
done

pushd "$ROOT/rockbox-y1/android/y1" >/dev/null
set +o pipefail
yes | ./installToolchain.sh 2>&1 | tee "$LOGS/11-toolchain.log"
toolchain_status=${PIPESTATUS[1]}
set -o pipefail
if [ "$toolchain_status" -ne 0 ]; then
    exit "$toolchain_status"
fi
rm -rf build
mkdir build
pushd build >/dev/null
../../../tools/configure --target=310 --lcdwidth=480 --lcdheight=360 --type=n \
    2>&1 | tee "$LOGS/12-configure.log"
make clean 2>&1 | tee "$LOGS/13-clean.log"
make -j2 2>&1 | tee "$LOGS/14-build.log"
make zip 2>&1 | tee "$LOGS/15-rockbox-zip.log"

test -f librockbox.so
test -f rockbox.zip
unzip -tq rockbox.zip | tee "$LOGS/16-rockbox-zip-test.log"
unzip -l rockbox.zip | tee "$LOGS/17-rockbox-zip-list.log"
unzip -l rockbox.zip | grep -F 'sdcard/.rockbox/rocks/apps/solar.rock' >/dev/null
rm -rf inspect-rockbox
mkdir inspect-rockbox
unzip -q rockbox.zip -d inspect-rockbox
PLUGIN_BUILD="inspect-rockbox/sdcard/.rockbox/rocks/apps/solar.rock"
test -f "$PLUGIN_BUILD"
file librockbox.so "$PLUGIN_BUILD" | tee "$LOGS/18-rockbox-binaries.log"
readelf -h librockbox.so | tee "$LOGS/19-librockbox-readelf.log"
readelf -d librockbox.so | tee "$LOGS/20-librockbox-dynamic.log"
strings librockbox.so | grep -F '/data/solarctl' | tee "$LOGS/21-helper-runner-string.log"
strings "$PLUGIN_BUILD" | grep -F 'Piped / YouTube' | tee "$LOGS/22-plugin-feature-string.log"
strings "$PLUGIN_BUILD" | grep -F 'SSH / SCP' >/dev/null
strings "$PLUGIN_BUILD" | grep -F 'Stem separation' >/dev/null

rm -rf "$BUILD/rockbox-tree"
mkdir -p "$BUILD/rockbox-tree"
unzip -q rockbox.zip -d "$BUILD/rockbox-tree"
test -f "$BUILD/rockbox-tree/sdcard/.rockbox/rocks/apps/solar.rock"
cp librockbox.so "$BUILD/librockbox.so"
cp rockbox.zip "$BUILD/rockbox.zip"
# On the Y1 port libmisc.so is a ZIP payload whose root is sdcard/.rockbox.
cp rockbox.zip "$BUILD/libmisc.so"
unzip -tq "$BUILD/libmisc.so" | tee "$LOGS/23-libmisc-test.log"
popd >/dev/null
popd >/dev/null

python3 - <<'PY' 2>&1 | tee "$LOGS/24-release-assets.log"
import json
import os
import pathlib
import urllib.request

root = pathlib.Path(os.environ.get('GITHUB_WORKSPACE', '.')).resolve()
tag = os.environ.get('ROCKBOX_RELEASE_TAG', 'stable-v0.5')
api = f'https://api.github.com/repos/rockbox-y1/rockbox/releases/tags/{tag}'
request = urllib.request.Request(api, headers={
    'Accept': 'application/vnd.github+json',
    'User-Agent': 'Rockbox-Solar-Type-A-builder',
})
with urllib.request.urlopen(request, timeout=60) as response:
    release = json.load(response)
assets = release.get('assets', [])
for asset in assets:
    print(f"asset={asset['name']} bytes={asset['size']} url={asset['browser_download_url']}")
exact = [a for a in assets if a['name'].lower() == 'rom.zip']
fallback = [a for a in assets
            if a['name'].lower().endswith('.zip')
            and 'rom' in a['name'].lower()
            and ('type-a' in a['name'].lower() or 'type_a' in a['name'].lower())]
candidates = exact or fallback
if len(candidates) != 1:
    raise SystemExit(f'Expected one official Type A ROM asset; found {[a["name"] for a in candidates]}')
chosen = candidates[0]
inputs = root / 'firmware-inputs'
inputs.mkdir(parents=True, exist_ok=True)
(inputs / 'type-a-url.txt').write_text(chosen['browser_download_url'] + '\n', encoding='utf-8')
(inputs / 'type-a-name.txt').write_text(chosen['name'] + '\n', encoding='utf-8')
(inputs / 'release-tag.txt').write_text(release['tag_name'] + '\n', encoding='utf-8')
PY

TYPE_A_URL=$(cat "$INPUTS/type-a-url.txt")
TYPE_A_NAME=$(cat "$INPUTS/type-a-name.txt")
BASE_RELEASE=$(cat "$INPUTS/release-tag.txt")
curl --fail --location --retry 3 --output "$INPUTS/type-a-base-rom.zip" "$TYPE_A_URL" \
    2>&1 | tee "$LOGS/25-type-a-download.log"
curl --fail --location --retry 3 --output "$INPUTS/solar-cacert.pem" \
    https://curl.se/ca/cacert.pem 2>&1 | tee "$LOGS/26-ca-download.log"
unzip -tq "$INPUTS/type-a-base-rom.zip" | tee "$LOGS/27-type-a-base-test.log"
unzip -l "$INPUTS/type-a-base-rom.zip" | tee "$LOGS/28-type-a-base-list.log"
unzip -l "$INPUTS/type-a-base-rom.zip" | grep -q 'system.img'
unzip -l "$INPUTS/type-a-base-rom.zip" | grep -q 'userdata.img'
unzip -l "$INPUTS/type-a-base-rom.zip" | grep -Eiq 'scatter'
unzip -l "$INPUTS/type-a-base-rom.zip" | grep -Eiq 'DA.*\.bin|download.*agent'
sha256sum "$INPUTS/type-a-base-rom.zip" "$INPUTS/solar-cacert.pem" \
    | tee "$LOGS/29-input-checksums.log"

work="$ROOT/firmware-work-type-a"
output="$DIST/rom.zip"
rm -rf "$work" "$DIST"
mkdir -p "$work/mount_sys" "$work/mount_user" "$DIST"
unzip -q "$INPUTS/type-a-base-rom.zip" -d "$work"

cleanup_mounts() {
    mountpoint -q "$work/mount_sys" && sudo umount "$work/mount_sys" || true
    mountpoint -q "$work/mount_user" && sudo umount "$work/mount_user" || true
}
trap cleanup_mounts EXIT

sudo mount -t ext4 -o loop,rw "$work/system.img" "$work/mount_sys"
sudo mount -t ext4 -o loop,rw "$work/userdata.img" "$work/mount_user"
sudo find "$work/mount_sys" "$work/mount_user" -maxdepth 8 \
    \( -name librockbox.so -o -name libmisc.so -o -name .rockbox \) -print \
    | tee "$LOGS/30-base-rockbox-layout.log"

sudo test -f "$work/mount_sys/lib/librockbox.so"
sudo install -m 0755 "$BUILD/librockbox.so" "$work/mount_sys/lib/librockbox.so"
while IFS= read -r existing; do
    sudo install -m 0755 "$BUILD/librockbox.so" "$existing"
done < <(sudo find "$work/mount_user" -type f -name librockbox.so -print)

mapfile -t misc_targets < <(sudo find "$work/mount_sys" "$work/mount_user" \
    -type f -name libmisc.so -print)
if [ "${#misc_targets[@]}" -eq 0 ]; then
    sudo mkdir -p "$work/mount_user/data/org.rockbox/lib"
    misc_targets+=("$work/mount_user/data/org.rockbox/lib/libmisc.so")
fi
for existing in "${misc_targets[@]}"; do
    sudo install -m 0755 "$BUILD/libmisc.so" "$existing"
done

rockbox_target="$work/mount_user/data/org.rockbox/app_rockbox/.rockbox"
sudo rm -rf "$rockbox_target"
sudo mkdir -p "$rockbox_target"
sudo cp -a "$BUILD/rockbox-tree/sdcard/.rockbox/." "$rockbox_target/"
sudo install -m 0600 "$ROOT/firmware/native/solar.cfg" "$rockbox_target/solar.cfg"
sudo install -m 0755 "$BUILD/solarctl" "$work/mount_user/solarctl"
sudo install -m 0644 "$INPUTS/solar-cacert.pem" "$work/mount_user/solar-cacert.pem"

sudo test -f "$work/mount_sys/lib/librockbox.so"
sudo test -x "$work/mount_user/solarctl"
sudo test -f "$work/mount_user/solar-cacert.pem"
sudo test -f "$rockbox_target/rocks/apps/solar.rock"
sudo test -f "$rockbox_target/solar.cfg"
sudo strings "$work/mount_sys/lib/librockbox.so" | grep -F '/data/solarctl'
sudo strings "$rockbox_target/rocks/apps/solar.rock" | grep -F 'Piped / YouTube'
sudo strings "$work/mount_user/solarctl" | grep -F 'slskd_search'
df -h "$work/mount_sys" "$work/mount_user" | tee "$LOGS/31-image-free-space.log"
sync
sudo umount "$work/mount_sys"
sudo umount "$work/mount_user"
trap - EXIT

rm -rf "$work/mount_sys" "$work/mount_user"
set +e
e2fsck -fn "$work/system.img" 2>&1 | tee "$LOGS/32-system-e2fsck.log"
system_fsck=${PIPESTATUS[0]}
e2fsck -fn "$work/userdata.img" 2>&1 | tee "$LOGS/33-userdata-e2fsck.log"
userdata_fsck=${PIPESTATUS[0]}
set -e
if [ "$system_fsck" -gt 1 ] || [ "$userdata_fsck" -gt 1 ]; then
    echo "Filesystem verification failed: system=$system_fsck userdata=$userdata_fsck" >&2
    exit 1
fi

(cd "$work" && zip -q -9 -r "$output" .)
unzip -tq "$output" | tee "$LOGS/34-type-a-rom-test.log"
rom_size=$(stat -c %s "$output")
echo "type-a-rom-bytes=$rom_size" | tee "$LOGS/35-type-a-rom-size.log"
if [ "$rom_size" -lt 50000000 ]; then
    echo 'Packaged ROM is implausibly small' >&2
    exit 1
fi

verify="$ROOT/verify-type-a"
rm -rf "$verify"
mkdir -p "$verify/mount_sys" "$verify/mount_user"
unzip -q "$output" -d "$verify"
unzip -l "$output" | tee "$LOGS/36-final-rom-list.log"
unzip -l "$output" | grep -q 'system.img'
unzip -l "$output" | grep -q 'userdata.img'
unzip -l "$output" | grep -Eiq 'scatter'
unzip -l "$output" | grep -Eiq 'DA.*\.bin|download.*agent'
sudo mount -t ext4 -o loop,ro "$verify/system.img" "$verify/mount_sys"
sudo mount -t ext4 -o loop,ro "$verify/userdata.img" "$verify/mount_user"
trap 'mountpoint -q "$verify/mount_sys" && sudo umount "$verify/mount_sys" || true; mountpoint -q "$verify/mount_user" && sudo umount "$verify/mount_user" || true' EXIT
plugin="$verify/mount_user/data/org.rockbox/app_rockbox/.rockbox/rocks/apps/solar.rock"
config="$verify/mount_user/data/org.rockbox/app_rockbox/.rockbox/solar.cfg"
sudo test -f "$verify/mount_sys/lib/librockbox.so"
sudo test -x "$verify/mount_user/solarctl"
sudo test -f "$verify/mount_user/solar-cacert.pem"
sudo test -f "$plugin"
sudo test -f "$config"
sudo cmp "$BUILD/librockbox.so" "$verify/mount_sys/lib/librockbox.so"
sudo cmp "$BUILD/solarctl" "$verify/mount_user/solarctl"
sudo strings "$verify/mount_sys/lib/librockbox.so" | grep -F '/data/solarctl'
sudo strings "$plugin" | grep -F 'SSH / SCP'
sudo strings "$verify/mount_user/solarctl" | grep -F 'piped_search'
sudo strings "$verify/mount_user/solarctl" | grep -F 'deezer_search'
sudo strings "$verify/mount_user/solarctl" | grep -F 'stem_split'
sudo file "$verify/mount_sys/lib/librockbox.so" "$verify/mount_user/solarctl" "$plugin" \
    | tee "$LOGS/37-final-binary-types.log"
sudo stat -c '%n bytes=%s mode=%a' \
    "$verify/mount_sys/lib/librockbox.so" "$verify/mount_user/solarctl" "$plugin" "$config" \
    | tee "$LOGS/38-final-file-stats.log"
sudo umount "$verify/mount_sys"
sudo umount "$verify/mount_user"
trap - EXIT

sha256sum "$output" | tee "$DIST/SHA256SUMS.txt"
cat > "$DIST/BUILD-INFO.txt" <<EOF
Rockbox Solar native firmware for Innioasis Y1 Type A
Integration commit: ${GITHUB_SHA:-unknown}
Rockbox source commit: $ROCKBOX_REF
Official base release: $BASE_RELEASE
Official base asset: $TYPE_A_NAME
Built UTC: $(date -u +%Y-%m-%dT%H:%M:%SZ)
Physical Y1 validation: NOT PERFORMED
EOF
cat > "$DIST/INSTALL-RECOVERY.txt" <<'EOF'
INSTALL — TYPE A ONLY
=====================
1. Charge the Innioasis Y1 and use a known-good USB data cable.
2. Keep an official stock or official Rockbox Type A rom.zip available.
3. Open Innioasis Updater on Windows.
4. Select Install from .zip and choose this rom.zip without extracting it.
5. Follow the updater's power-off and USB prompts exactly. Do not disconnect
   the player while any partition is being written.
6. The first boot can take longer than normal. Rockbox should remain the UI.
7. Open the native Solar entry from Rockbox and configure Wi-Fi/services.

RECOVERY
========
- If the wheel mapping is wrong, stop testing and restore the official matching
  Type A ROM with Innioasis Updater.
- If Solar fails but Rockbox boots, inspect /sdcard/.rockbox/solar/response.txt
  and /sdcard/.rockbox/solar.cfg over USB or ADB.
- If the device does not boot, use Innioasis Updater's full restore flow with
  an official Type A ROM. Do not manually flash individual boot-critical images.
- This image was compiled, packaged, mounted, and statically inspected in CI.
  It has not been booted or hardware-tested on a physical Y1.
EOF

echo "Type A ROM complete: $output"
