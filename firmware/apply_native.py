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
