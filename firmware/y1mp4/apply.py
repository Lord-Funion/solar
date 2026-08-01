#!/usr/bin/env python3
"""Add the Y1 MP4 viewer plugin and Android video bridge to a patched Rockbox tree.

Run this after firmware/apply_native.py. The resulting y1mp4.rock remains a
normal Rockbox viewer plugin; decoding is delegated to Android's installed
video activity so common H.264/AAC MP4 files can use the Y1's platform decoder.
"""
from __future__ import annotations

import shutil
import sys
from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"{label}: insertion point not found in {path}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def require(path: Path, needles: list[str]) -> None:
    text = path.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            raise SystemExit(f"missing {needle!r} in {path}")


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: apply.py ROCKBOX_SOURCE_ROOT")

    root = Path(sys.argv[1]).resolve()
    here = Path(__file__).resolve().parent

    plugin = root / "apps/plugins/y1mp4.c"
    sources = root / "apps/plugins/SOURCES"
    categories = root / "apps/plugins/CATEGORIES"
    viewers = root / "apps/plugins/viewers.config"
    plugin_h = root / "apps/plugin.h"
    plugin_c = root / "apps/plugin.c"

    plugin.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(here / "y1mp4.c", plugin)

    replace_once(
        sources,
        "#if defined(INNIOASIS_Y1)\n"
        "podcast_downloader.c\n"
        "solar.c\n"
        "speaker_toggle.c\n"
        "#endif",
        "#if defined(INNIOASIS_Y1)\n"
        "podcast_downloader.c\n"
        "solar.c\n"
        "speaker_toggle.c\n"
        "y1mp4.c\n"
        "#endif",
        "Y1 MP4 plugin source",
    )

    replace_once(
        categories,
        "solar,apps\nsokoban,games",
        "solar,apps\ny1mp4,apps\nsokoban,games",
        "Y1 MP4 plugin category",
    )

    replace_once(
        viewers,
        "mpg,viewers/mpegplayer,4\n"
        "mpeg,viewers/mpegplayer,4",
        "mp4,apps/y1mp4,4\n"
        "m4v,apps/y1mp4,4\n"
        "mov,apps/y1mp4,4\n"
        "mpg,viewers/mpegplayer,4\n"
        "mpeg,viewers/mpegplayer,4",
        "MP4 viewer associations",
    )

    replace_once(
        plugin_h,
        "#define PLUGIN_API_VERSION 285",
        "#define PLUGIN_API_VERSION 286",
        "plugin ABI version",
    )

    replace_once(
        plugin_h,
        "    int (*solar_run_helper)(void);\n"
        "#endif",
        "    int (*solar_run_helper)(void);\n"
        "    int (*y1_video_play)(const char *path);\n"
        "#endif",
        "Y1 video plugin API declaration",
    )

    bridge = r'''

/* Launch a local video through Android's registered MP4 activity. The plugin
 * API does not expose fork/exec, so this tiny hosted-target bridge is kept in
 * core. The path is single-quoted and apostrophes/control characters are
 * rejected before invoking the shell. */
static int y1_video_play(const char *path)
{
    char command[MAX_PATH * 2 + 256];
    const char *p;
    const char *ext;
    int length;

    if (path == NULL ||
        (strncmp(path, "/sdcard/", 8) != 0 &&
         strncmp(path, "/mnt/sdcard/", 12) != 0 &&
         strncmp(path, "/storage/", 9) != 0))
        return -1;

    for (p = path; *p != '\0'; ++p)
    {
        if (*p == '\'' || *p == '\n' || *p == '\r')
            return -2;
    }

    ext = strrchr(path, '.');
    if (ext == NULL ||
        (strcasecmp(ext, ".mp4") != 0 &&
         strcasecmp(ext, ".m4v") != 0 &&
         strcasecmp(ext, ".mov") != 0))
        return -3;

    length = snprintf(command, sizeof(command),
        "/system/bin/am start -W -a android.intent.action.VIEW "
        "-d 'file://%s' -t video/mp4 >/dev/null 2>&1",
        path);
    if (length < 0 || (size_t)length >= sizeof(command))
        return -4;

    return system(command);
}
'''

    replace_once(
        plugin_c,
        "static int solar_run_helper(void)\n"
        "{\n"
        "    return system(\"/data/solarctl /sdcard/.rockbox/solar/request.txt \"\n"
        "                  \"/sdcard/.rockbox/solar/response.txt\");\n"
        "}\n"
        "#endif",
        "static int solar_run_helper(void)\n"
        "{\n"
        "    return system(\"/data/solarctl /sdcard/.rockbox/solar/request.txt \"\n"
        "                  \"/sdcard/.rockbox/solar/response.txt\");\n"
        "}\n" + bridge + "\n#endif",
        "Y1 Android video bridge",
    )

    replace_once(
        plugin_c,
        "    .free_array = free_array,\n"
        "    .solar_run_helper = solar_run_helper,\n"
        "#endif",
        "    .free_array = free_array,\n"
        "    .solar_run_helper = solar_run_helper,\n"
        "    .y1_video_play = y1_video_play,\n"
        "#endif",
        "Y1 video plugin API initializer",
    )

    require(plugin, [
        '#include "plugin.h"',
        "rb->y1_video_play(path)",
        "Open an MP4 file from Rockbox Files",
    ])
    require(sources, ["y1mp4.c"])
    require(categories, ["y1mp4,apps"])
    require(viewers, ["mp4,apps/y1mp4,4", "m4v,apps/y1mp4,4", "mov,apps/y1mp4,4"])
    require(plugin_h, ["PLUGIN_API_VERSION 286", "int (*y1_video_play)(const char *path);"])
    require(plugin_c, ["static int y1_video_play(const char *path)", ".y1_video_play = y1_video_play"])

    print("Applied Y1 MP4 viewer integration:")
    for path in (plugin, sources, categories, viewers, plugin_h, plugin_c):
        print(path.relative_to(root))


if __name__ == "__main__":
    main()
