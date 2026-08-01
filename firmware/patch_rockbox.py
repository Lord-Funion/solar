#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: patch_rockbox.py <rockbox-source-root>')
root = Path(sys.argv[1])
main_menu = root / 'apps/menus/main_menu.c'
root_menu = root / 'apps/root_menu.c'

main_text = main_menu.read_text(encoding='utf-8')
if 'MAKE_MENU(solar_menu' not in main_text:
    marker = '''/*    BlUETOOTH SETTINGS MENU     */
/**********************************/
#endif

/***********************************/
/*      INFO MENU                  */
'''
    solar_block = '''/*    BlUETOOTH SETTINGS MENU     */
/**********************************/

/***********************************/
/*      SOLAR SERVICES MENU        */
static int solar_home_func(void)
{
    system("am start -n dev.lordfunion.rockboxsolar/.LauncherActivity");
    return 0;
}

static int solar_youtube_func(void)
{
    system("am start -n dev.lordfunion.rockboxsolar/.YouTubeActivity");
    return 0;
}

static int solar_reach_func(void)
{
    system("am start -n dev.lordfunion.rockboxsolar/.ReachActivity");
    return 0;
}

static int solar_deezer_func(void)
{
    system("am start -n dev.lordfunion.rockboxsolar/.DeezerActivity");
    return 0;
}

static int solar_ssh_func(void)
{
    system("am start -n dev.lordfunion.rockboxsolar/.SshActivity");
    return 0;
}

static int solar_stems_func(void)
{
    system("am start -n dev.lordfunion.rockboxsolar/.StemActivity");
    return 0;
}

static int solar_wifi_func(void)
{
    system("am start -n dev.lordfunion.rockboxsolar/.WifiActivity");
    return 0;
}

static int solar_bluetooth_func(void)
{
    system("am start -n dev.lordfunion.rockboxsolar/.BluetoothActivity");
    return 0;
}

static int solar_audio_tools_func(void)
{
    system("am start -n dev.lordfunion.rockboxsolar/.AudioToolsActivity");
    return 0;
}

static int solar_themes_func(void)
{
    system("am start -n dev.lordfunion.rockboxsolar/.ThemeImportActivity");
    return 0;
}

static int solar_updates_func(void)
{
    system("am start -n dev.lordfunion.rockboxsolar/.UpdateActivity");
    return 0;
}

static int solar_hardware_test_func(void)
{
    system("am start -n dev.lordfunion.rockboxsolar/.HardwareTestActivity");
    return 0;
}

MENUITEM_FUNCTION(solar_home_item, 0, "Solar Home",
                  solar_home_func, NULL, Icon_Plugin);
MENUITEM_FUNCTION(solar_youtube_item, 0, "YouTube Search",
                  solar_youtube_func, NULL, Icon_Plugin);
MENUITEM_FUNCTION(solar_reach_item, 0, "Reach / Soulseek",
                  solar_reach_func, NULL, Icon_Plugin);
MENUITEM_FUNCTION(solar_deezer_item, 0, "Deezer",
                  solar_deezer_func, NULL, Icon_Plugin);
MENUITEM_FUNCTION(solar_ssh_item, 0, "Remote SSH / SCP",
                  solar_ssh_func, NULL, Icon_Plugin);
MENUITEM_FUNCTION(solar_stems_item, 0, "Stem Player",
                  solar_stems_func, NULL, Icon_Plugin);
MENUITEM_FUNCTION(solar_wifi_item, 0, "Wi-Fi",
                  solar_wifi_func, NULL, Icon_Config);
MENUITEM_FUNCTION(solar_bluetooth_item, 0, "Bluetooth",
                  solar_bluetooth_func, NULL, Icon_Config);
MENUITEM_FUNCTION(solar_audio_tools_item, 0, "Audio Tools",
                  solar_audio_tools_func, NULL, Icon_Audio);
MENUITEM_FUNCTION(solar_themes_item, 0, "Y1 Theme Import",
                  solar_themes_func, NULL, Icon_Theme);
MENUITEM_FUNCTION(solar_updates_item, 0, "Solar Updates / ROMs",
                  solar_updates_func, NULL, Icon_System_menu);
MENUITEM_FUNCTION(solar_hardware_test_item, 0, "Y1 Hardware Test",
                  solar_hardware_test_func, NULL, Icon_System_menu);

MAKE_MENU(solar_menu, "Solar", NULL, Icon_Plugin,
          &solar_home_item,
          &solar_youtube_item,
          &solar_reach_item,
          &solar_deezer_item,
          &solar_ssh_item,
          &solar_stems_item,
          &solar_wifi_item,
          &solar_bluetooth_item,
          &solar_audio_tools_item,
          &solar_themes_item,
          &solar_updates_item,
          &solar_hardware_test_item);
/*      SOLAR SERVICES MENU        */
/**********************************/
#endif

/***********************************/
/*      INFO MENU                  */
'''
    if marker not in main_text:
        raise SystemExit('main_menu.c insertion point not found; upstream changed')
    main_text = main_text.replace(marker, solar_block, 1)
    main_menu.write_text(main_text, encoding='utf-8')

root_text = root_menu.read_text(encoding='utf-8')
old_extern = '''#ifdef INNIOASIS_Y1
extern struct menu_item_ex fm_radio_app_item;
#endif
'''
new_extern = '''#ifdef INNIOASIS_Y1
extern struct menu_item_ex fm_radio_app_item;
extern struct menu_item_ex solar_menu;
#endif
'''
if 'extern struct menu_item_ex solar_menu;' not in root_text:
    if old_extern not in root_text:
        raise SystemExit('root_menu.c extern insertion point not found')
    root_text = root_text.replace(old_extern, new_extern, 1)

old_table = '''#ifdef INNIOASIS_Y1
    { "fm_radio_app", &fm_radio_app_item },
#endif
    { "plugins", &rocks_browser },
'''
new_table = '''#ifdef INNIOASIS_Y1
    { "fm_radio_app", &fm_radio_app_item },
    { "solar", &solar_menu },
#endif
    { "plugins", &rocks_browser },
'''
if '{ "solar", &solar_menu },' not in root_text:
    if old_table not in root_text:
        raise SystemExit('root_menu.c menu table insertion point not found')
    root_text = root_text.replace(old_table, new_table, 1)

root_menu.write_text(root_text, encoding='utf-8')
