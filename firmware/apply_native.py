#!/usr/bin/env python3
"""Apply the native Rockbox Solar integration to a clean Rockbox-Y1 checkout."""
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
        raise SystemExit("usage: apply_native.py ROCKBOX_SOURCE_ROOT")
    root = Path(sys.argv[1]).resolve()
    native = Path(__file__).resolve().parent / "native"

    plugin = root / "apps/plugins/solar.c"
    sources = root / "apps/plugins/SOURCES"
    categories = root / "apps/plugins/CATEGORIES"
    plugin_h = root / "apps/plugin.h"
    plugin_c = root / "apps/plugin.c"
    main_menu = root / "apps/menus/main_menu.c"
    root_menu = root / "apps/root_menu.c"

    plugin.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(native / "solar.c", plugin)

    # The Y1 Rockbox plugin API exposes write(), not fdprintf(). Keep the
    # source readable while replacing the request writer with checked native
    # writes in the generated Rockbox tree.
    plugin_text = plugin.read_text(encoding="utf-8")
    old_writer = r'''static int write_request(const char *command,
                         const char *key1, const char *value1,
                         const char *key2, const char *value2,
                         const char *key3, const char *value3,
                         const char *key4, const char *value4)
{
    int fd;
    char clean[VALUE_LEN];
    ensure_state_dir();
    fd = rb->open(REQUEST_PATH, O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (fd < 0)
        return -1;

    safe_copy(clean, sizeof(clean), command);
    rb->fdprintf(fd, "command=%s\n", clean);
#define WRITE_PAIR(k, v) do { \
    if ((k) != NULL && (v) != NULL) { \
        safe_copy(clean, sizeof(clean), (v)); \
        rb->fdprintf(fd, "%s=%s\n", (k), clean); \
    } \
} while (0)
    WRITE_PAIR(key1, value1);
    WRITE_PAIR(key2, value2);
    WRITE_PAIR(key3, value3);
    WRITE_PAIR(key4, value4);
#undef WRITE_PAIR
    rb->close(fd);
    return 0;
}
'''
    new_writer = r'''static int write_all(int fd, const char *text)
{
    size_t total = 0;
    size_t length = rb->strlen(text);

    while (total < length)
    {
        ssize_t written = rb->write(fd, text + total, length - total);
        if (written <= 0)
            return -1;
        total += (size_t)written;
    }
    return 0;
}

static int write_pair(int fd, const char *key, const char *value)
{
    char clean[VALUE_LEN];
    char line[VALUE_LEN + 80];
    int length;

    safe_copy(clean, sizeof(clean), value);
    length = rb->snprintf(line, sizeof(line), "%s=%s\n", key, clean);
    if (length < 0 || (size_t)length >= sizeof(line))
        return -1;
    return write_all(fd, line);
}

static int write_request(const char *command,
                         const char *key1, const char *value1,
                         const char *key2, const char *value2,
                         const char *key3, const char *value3,
                         const char *key4, const char *value4)
{
    int fd;
    int result = 0;

    ensure_state_dir();
    fd = rb->open(REQUEST_PATH, O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (fd < 0)
        return -1;

    if (write_pair(fd, "command", command) < 0 ||
        (key1 != NULL && value1 != NULL && write_pair(fd, key1, value1) < 0) ||
        (key2 != NULL && value2 != NULL && write_pair(fd, key2, value2) < 0) ||
        (key3 != NULL && value3 != NULL && write_pair(fd, key3, value3) < 0) ||
        (key4 != NULL && value4 != NULL && write_pair(fd, key4, value4) < 0))
        result = -1;

    if (rb->close(fd) < 0)
        result = -1;
    return result;
}
'''
    if old_writer not in plugin_text:
        raise SystemExit("solar.c request writer insertion point not found")
    plugin.write_text(plugin_text.replace(old_writer, new_writer, 1), encoding="utf-8")

    replace_once(
        sources,
        "#if defined(INNIOASIS_Y1)\npodcast_downloader.c\nspeaker_toggle.c\n#endif",
        "#if defined(INNIOASIS_Y1)\npodcast_downloader.c\nsolar.c\nspeaker_toggle.c\n#endif",
        "plugin SOURCES",
    )
    replace_once(
        categories,
        "snow,demos\nsokoban,games",
        "snow,demos\nsolar,apps\nsokoban,games",
        "plugin CATEGORIES",
    )

    replace_once(
        plugin_h,
        "#define PLUGIN_API_VERSION 284",
        "#define PLUGIN_API_VERSION 285",
        "plugin ABI version",
    )
    replace_once(
        plugin_h,
        "    int (*android_podcast_disconnect_wifi)(void);\n    void (*free_array)(char** array);\n#endif",
        "    int (*android_podcast_disconnect_wifi)(void);\n"
        "    void (*free_array)(char** array);\n"
        "    int (*solar_run_helper)(void);\n"
        "#endif",
        "Solar plugin API declaration",
    )

    helper_function = r'''
#ifdef INNIOASIS_Y1
/* Run only the fixed native helper and fixed request/response paths. User
 * values never enter this shell command, which prevents command injection. */
static int solar_run_helper(void)
{
    return system("/data/solarctl /sdcard/.rockbox/solar/request.txt "
                  "/sdcard/.rockbox/solar/response.txt");
}
#endif
'''
    replace_once(
        plugin_c,
        "extern struct battery_tables_t device_battery_tables; /* powermgmt.c */\n",
        "extern struct battery_tables_t device_battery_tables; /* powermgmt.c */\n" + helper_function,
        "Solar helper runner",
    )
    replace_once(
        plugin_c,
        "    .android_podcast_disconnect_wifi = android_podcast_disconnect_wifi,\n"
        "    .free_array = free_array,\n"
        "#endif",
        "    .android_podcast_disconnect_wifi = android_podcast_disconnect_wifi,\n"
        "    .free_array = free_array,\n"
        "    .solar_run_helper = solar_run_helper,\n"
        "#endif",
        "Solar plugin API initializer",
    )

    menu_marker = '''/*    BlUETOOTH SETTINGS MENU     */
/**********************************/
#endif

/***********************************/
/*      INFO MENU                  */
'''
    menu_replacement = '''/*    BlUETOOTH SETTINGS MENU     */
/**********************************/

static int solar_native_func(void)
{
    int result = plugin_load(PLUGIN_APPS_DIR "/solar.rock", NULL);
    if (result != PLUGIN_OK)
        splash(HZ * 2, "Unable to load native Solar plugin");
    return 0;
}

MENUITEM_FUNCTION(solar_native_item, 0, "Solar",
                  solar_native_func, NULL, Icon_Plugin);
#endif

/***********************************/
/*      INFO MENU                  */
'''
    replace_once(main_menu, menu_marker, menu_replacement, "native Solar menu")

    replace_once(
        root_menu,
        "#ifdef INNIOASIS_Y1\nextern struct menu_item_ex fm_radio_app_item;\n#endif\n",
        "#ifdef INNIOASIS_Y1\n"
        "extern struct menu_item_ex fm_radio_app_item;\n"
        "extern struct menu_item_ex solar_native_item;\n"
        "#endif\n",
        "root menu extern",
    )
    replace_once(
        root_menu,
        "#ifdef INNIOASIS_Y1\n"
        "    { \"fm_radio_app\", &fm_radio_app_item },\n"
        "#endif\n"
        "    { \"plugins\", &rocks_browser },\n",
        "#ifdef INNIOASIS_Y1\n"
        "    { \"fm_radio_app\", &fm_radio_app_item },\n"
        "    { \"solar\", &solar_native_item },\n"
        "#endif\n"
        "    { \"plugins\", &rocks_browser },\n",
        "root menu table",
    )

    require(plugin, [
        "PLUGIN_HEADER",
        "solar_run_helper",
        "piped_search",
        "deezer_search",
        "slskd_search",
        "ssh_exec",
        "stem_split",
    ])
    require(plugin_h, ["PLUGIN_API_VERSION 285", "int (*solar_run_helper)(void);"])
    require(plugin_c, ["static int solar_run_helper(void)", ".solar_run_helper = solar_run_helper"])
    require(main_menu, ["PLUGIN_APPS_DIR \"/solar.rock\"", "solar_native_item"])
    require(root_menu, ["extern struct menu_item_ex solar_native_item", "{ \"solar\", &solar_native_item }"])

    for path in (plugin, main_menu, root_menu):
        text = path.read_text(encoding="utf-8")
        if "dev.lordfunion.rockboxsolar" in text:
            raise SystemExit(f"Android Solar package reference leaked into {path}")
    if "am start" in plugin.read_text(encoding="utf-8"):
        raise SystemExit("Android Activity launch leaked into native Solar plugin")

    print("Applied native Rockbox Solar files:")
    for path in (plugin, sources, categories, plugin_h, plugin_c, main_menu, root_menu):
        print(path.relative_to(root))


if __name__ == "__main__":
    main()
