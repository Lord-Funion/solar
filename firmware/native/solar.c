/***************************************************************************
 * Native Rockbox Solar integration for the Innioasis Y1.
 *
 * Rockbox remains the UI and plugin runtime. Network and SSH work is delegated
 * to the statically linked /data/solarctl ARM helper through fixed request and
 * response files. No Android Activity or separate Solar APK is used.
 ***************************************************************************/
#include "plugin.h"

PLUGIN_HEADER

#define SOLAR_DIR       ROCKBOX_DIR "/solar"
#define REQUEST_PATH    SOLAR_DIR "/request.txt"
#define RESPONSE_PATH   SOLAR_DIR "/response.txt"
#define CONFIG_PATH     ROCKBOX_DIR "/solar.cfg"
#define WIFI_CONFIG     ROCKBOX_DIR "/wifi.cfg"
#define MAX_ITEMS       60
#define TITLE_LEN       180
#define SUBTITLE_LEN    160
#define VALUE_LEN       512
#define LINE_LEN        2048

struct solar_item
{
    char id[32];
    char kind[24];
    char title[TITLE_LEN];
    char subtitle[SUBTITLE_LEN];
    char value[VALUE_LEN];
};

static struct solar_item items[MAX_ITEMS];
static int item_count;
static char last_message[LINE_LEN];
static char last_file[MAX_PATH];

static void safe_copy(char *dst, size_t size, const char *src)
{
    size_t i = 0;
    if (size == 0)
        return;
    if (src == NULL)
        src = "";
    while (i + 1 < size && src[i] != '\0')
    {
        char c = src[i];
        dst[i] = (c == '\r' || c == '\n' || c == '\t') ? ' ' : c;
        i++;
    }
    dst[i] = '\0';
}

static void ensure_state_dir(void)
{
    rb->mkdir(SOLAR_DIR);
}

static int write_request(const char *command,
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

static char *next_field(char **cursor)
{
    char *start = *cursor;
    char *tab;
    if (start == NULL)
        return NULL;
    tab = rb->strchr(start, '\t');
    if (tab != NULL)
    {
        *tab = '\0';
        *cursor = tab + 1;
    }
    else
        *cursor = NULL;
    return start;
}

static int parse_response(void)
{
    int fd;
    char line[LINE_LEN];
    item_count = 0;
    last_message[0] = '\0';
    last_file[0] = '\0';

    fd = rb->open(RESPONSE_PATH, O_RDONLY);
    if (fd < 0)
    {
        safe_copy(last_message, sizeof(last_message), "Solar helper produced no response");
        return -1;
    }

    while (rb->read_line(fd, line, sizeof(line)) > 0)
    {
        char *cursor = line;
        char *type = next_field(&cursor);
        if (type == NULL)
            continue;
        if (!rb->strcmp(type, "ERROR"))
        {
            safe_copy(last_message, sizeof(last_message), cursor);
            rb->close(fd);
            return -1;
        }
        if (!rb->strcmp(type, "MESSAGE"))
        {
            safe_copy(last_message, sizeof(last_message), cursor);
            continue;
        }
        if (!rb->strcmp(type, "FILE"))
        {
            safe_copy(last_file, sizeof(last_file), cursor);
            continue;
        }
        if (!rb->strcmp(type, "ITEM") && item_count < MAX_ITEMS)
        {
            struct solar_item *item = &items[item_count];
            safe_copy(item->id, sizeof(item->id), next_field(&cursor));
            safe_copy(item->kind, sizeof(item->kind), next_field(&cursor));
            safe_copy(item->title, sizeof(item->title), next_field(&cursor));
            safe_copy(item->subtitle, sizeof(item->subtitle), next_field(&cursor));
            safe_copy(item->value, sizeof(item->value), next_field(&cursor));
            item_count++;
        }
    }
    rb->close(fd);
    return 0;
}

static int run_request(const char *command,
                       const char *key1, const char *value1,
                       const char *key2, const char *value2,
                       const char *key3, const char *value3,
                       const char *key4, const char *value4)
{
    int rc;
    if (write_request(command, key1, value1, key2, value2,
                      key3, value3, key4, value4) < 0)
    {
        rb->splash(HZ * 2, "Cannot write Solar request");
        return -1;
    }
    rb->remove(RESPONSE_PATH);
    rc = rb->solar_run_helper();
    if (parse_response() < 0)
    {
        rb->splash(HZ * 3, "%s", last_message);
        return -1;
    }
    if (rc != 0 && last_message[0] == '\0')
    {
        rb->splash(HZ * 2, "solarctl failed: %d", rc);
        return -1;
    }
    return 0;
}

static bool prompt(const char *title, char *value, size_t size)
{
    rb->splash(HZ, "%s", title);
    return rb->kbd_input(value, (int)size, NULL) >= 0;
}

static void show_last_message(void)
{
    if (last_message[0] != '\0')
        rb->splash(HZ * 3, "%s", last_message);
}

static const char *list_name(int selected, void *data,
                             char *buffer, size_t buffer_len)
{
    (void)data;
    if (selected < 0 || selected >= item_count)
        return "";
    if (items[selected].subtitle[0] != '\0')
        rb->snprintf(buffer, buffer_len, "%s — %s",
                     items[selected].title, items[selected].subtitle);
    else
        rb->snprintf(buffer, buffer_len, "%s", items[selected].title);
    return buffer;
}

static int choose_result(const char *title)
{
    struct gui_synclist list;
    int action;
    int selected = 0;
    if (item_count <= 0)
    {
        show_last_message();
        return -1;
    }

    rb->gui_synclist_init(&list, list_name, NULL, false, 1, NULL);
    rb->gui_synclist_set_nb_items(&list, item_count);
    rb->gui_synclist_set_title(&list, title, Icon_Plugin);
    rb->gui_synclist_select_item(&list, 0);
    rb->gui_synclist_draw(&list);

    while (true)
    {
        action = rb->get_action(CONTEXT_LIST, HZ / 10);
        if (rb->gui_synclist_do_button(&list, &action))
            continue;
        switch (action)
        {
            case ACTION_STD_OK:
                selected = rb->gui_synclist_get_sel_pos(&list);
                return selected;
            case ACTION_STD_CANCEL:
            case ACTION_STD_MENU:
                return -1;
            default:
                if (rb->default_event_handler(action) == SYS_USB_CONNECTED)
                    return -2;
                break;
        }
    }
}

static void play_path(const char *path)
{
    if (path == NULL || path[0] == '\0')
    {
        rb->splash(HZ * 2, "No playable file was returned");
        return;
    }
    rb->playlist_create(NULL, NULL);
    rb->playlist_insert_track(NULL, path, 0, false, true);
    rb->playlist_start(0, 0, 0);
    rb->splash(HZ, "Playing %s", path);
}

static void download_selected(const char *command, int selected,
                              bool play_after)
{
    char index[16];
    rb->snprintf(index, sizeof(index), "%d", selected);
    rb->splash(0, "Downloading…");
    if (run_request(command, "index", index, NULL, NULL,
                    NULL, NULL, NULL, NULL) == 0)
    {
        show_last_message();
        if (play_after)
            play_path(last_file);
    }
}

static void search_piped(void)
{
    char query[192] = "";
    int selected;
    int action = 0;
    if (!prompt("Piped / YouTube search", query, sizeof(query)))
        return;
    rb->splash(0, "Searching Piped…");
    if (run_request("piped_search", "query", query, NULL, NULL,
                    NULL, NULL, NULL, NULL) < 0)
        return;
    selected = choose_result("Piped results");
    if (selected < 0)
        return;
    MENUITEM_STRINGLIST(menu, "Piped audio", NULL,
                        "Download best audio",
                        "Download and play");
    action = rb->do_menu(&menu, &action, NULL, false);
    if (action == 0)
        download_selected("piped_download", selected, false);
    else if (action == 1)
        download_selected("piped_download", selected, true);
}

static void search_deezer(void)
{
    char query[192] = "";
    int selected;
    int action = 0;
    if (!prompt("Deezer catalog search", query, sizeof(query)))
        return;
    rb->splash(0, "Searching Deezer…");
    if (run_request("deezer_search", "query", query, NULL, NULL,
                    NULL, NULL, NULL, NULL) < 0)
        return;
    selected = choose_result("Deezer previews");
    if (selected < 0)
        return;
    MENUITEM_STRINGLIST(menu, "Official preview", NULL,
                        "Download preview",
                        "Download and play preview");
    action = rb->do_menu(&menu, &action, NULL, false);
    if (action == 0)
        download_selected("deezer_download", selected, false);
    else if (action == 1)
        download_selected("deezer_download", selected, true);
}

static void search_slskd(void)
{
    char query[192] = "";
    char index[16];
    int selected;
    if (!prompt("Soulseek search through slskd", query, sizeof(query)))
        return;
    rb->splash(0, "Searching slskd for about 16 seconds…");
    if (run_request("slskd_search", "query", query, NULL, NULL,
                    NULL, NULL, NULL, NULL) < 0)
        return;
    selected = choose_result("Soulseek / slskd");
    if (selected < 0)
        return;
    rb->snprintf(index, sizeof(index), "%d", selected);
    rb->splash(0, "Queueing in slskd…");
    if (run_request("slskd_queue", "index", index, NULL, NULL,
                    NULL, NULL, NULL, NULL) == 0)
        show_last_message();
}

static void downloads(void)
{
    int selected;
    int action = 0;
    if (run_request("list_downloads", NULL, NULL, NULL, NULL,
                    NULL, NULL, NULL, NULL) < 0)
        return;
    selected = choose_result("Solar downloads");
    if (selected < 0)
        return;
    MENUITEM_STRINGLIST(menu, "Downloaded file", NULL,
                        "Play",
                        "Delete");
    action = rb->do_menu(&menu, &action, NULL, false);
    if (action == 0)
        play_path(items[selected].value);
    else if (action == 1)
    {
        int confirm = 0;
        MENUITEM_STRINGLIST(confirm_menu, "Delete this file?", NULL,
                            "Cancel", "Delete");
        confirm = rb->do_menu(&confirm_menu, &confirm, NULL, false);
        if (confirm == 1 &&
            run_request("delete_download", "path", items[selected].value,
                        NULL, NULL, NULL, NULL, NULL, NULL) == 0)
            show_last_message();
    }
}

static void ssh_menu(void)
{
    int selected = 0;
    MENUITEM_STRINGLIST(menu, "SSH / secure copy", NULL,
                        "Run SSH command",
                        "Download remote file",
                        "Upload local file");
    selected = rb->do_menu(&menu, &selected, NULL, false);
    if (selected == 0)
    {
        char command[VALUE_LEN] = "";
        if (prompt("SSH command", command, sizeof(command)))
        {
            rb->splash(0, "Running SSH command…");
            if (run_request("ssh_exec", "value", command, NULL, NULL,
                            NULL, NULL, NULL, NULL) == 0)
            {
                if (last_message[0] != '\0')
                    rb->splash(HZ * 4, "%s", last_message);
            }
        }
    }
    else if (selected == 1)
    {
        char remote[VALUE_LEN] = "";
        char local[MAX_PATH] = "";
        if (!prompt("Remote file path", remote, sizeof(remote)))
            return;
        if (!prompt("Local path (blank = Downloads/SSH)", local, sizeof(local)))
            return;
        rb->splash(0, "Downloading over SSH…");
        if (run_request("scp_get", "remote", remote, "local", local,
                        NULL, NULL, NULL, NULL) == 0)
            show_last_message();
    }
    else if (selected == 2)
    {
        char local[MAX_PATH] = "/sdcard/Music/";
        char remote[VALUE_LEN] = "";
        if (!prompt("Local file path", local, sizeof(local)))
            return;
        if (!prompt("Remote destination path", remote, sizeof(remote)))
            return;
        rb->splash(0, "Uploading over SSH…");
        if (run_request("scp_put", "local", local, "remote", remote,
                        NULL, NULL, NULL, NULL) == 0)
            show_last_message();
    }
}

static const char *stem_names[] = {
    "vocals", "drum", "bass", "piano", "electric_guitar",
    "acoustic_guitar", "synthesizer", "strings", "wind"
};

static void stems_menu(void)
{
    int selected = 0;
    MENUITEM_STRINGLIST(menu, "LALAL.AI stems", NULL,
                        "Check processing minutes",
                        "Separate local audio");
    selected = rb->do_menu(&menu, &selected, NULL, false);
    if (selected == 0)
    {
        rb->splash(0, "Checking LALAL.AI minutes…");
        if (run_request("stem_minutes", NULL, NULL, NULL, NULL,
                        NULL, NULL, NULL, NULL) == 0)
            show_last_message();
    }
    else if (selected == 1)
    {
        char source[MAX_PATH] = "/sdcard/Music/";
        const char *stem;
        const char *extraction;
        int stem_index = 0;
        int extraction_index = 0;
        int confirm = 0;
        MENUITEM_STRINGLIST(stem_menu, "Stem to isolate", NULL,
                            "Vocals", "Drums", "Bass", "Piano",
                            "Electric guitar", "Acoustic guitar",
                            "Synthesizer", "Strings", "Wind");
        MENUITEM_STRINGLIST(extraction_menu, "Extraction", NULL,
                            "Deep extraction", "Clear cut");
        MENUITEM_STRINGLIST(confirm_menu, "May use paid minutes", NULL,
                            "Cancel", "Upload and separate");
        if (!prompt("Local audio source", source, sizeof(source)))
            return;
        stem_index = rb->do_menu(&stem_menu, &stem_index, NULL, false);
        if (stem_index < 0 || stem_index >= (int)(sizeof(stem_names) / sizeof(stem_names[0])))
            return;
        extraction_index = rb->do_menu(&extraction_menu, &extraction_index, NULL, false);
        if (extraction_index < 0)
            return;
        confirm = rb->do_menu(&confirm_menu, &confirm, NULL, false);
        if (confirm != 1)
            return;
        stem = stem_names[stem_index];
        extraction = extraction_index == 0 ? "deep_extraction" : "clear_cut";
        rb->splash(0, "Uploading and processing stems…");
        if (run_request("stem_split", "source", source, "stem", stem,
                        "extraction", extraction, NULL, NULL) == 0)
            show_last_message();
    }
}

static void set_config_value(const char *key, const char *label,
                             const char *initial)
{
    char value[VALUE_LEN];
    safe_copy(value, sizeof(value), initial);
    if (!prompt(label, value, sizeof(value)))
        return;
    if (run_request("config_set", "key", key, "value", value,
                    NULL, NULL, NULL, NULL) == 0)
        show_last_message();
}

static void configure_wifi(void)
{
    char ssid[128] = "";
    char password[128] = "";
    if (!prompt("Wi-Fi SSID", ssid, sizeof(ssid)))
        return;
    if (!prompt("Wi-Fi password", password, sizeof(password)))
        return;
    if (run_request("wifi_set", "ssid", ssid, "password", password,
                    NULL, NULL, NULL, NULL) == 0)
        rb->splash(HZ * 2, "Saved to %s", WIFI_CONFIG);
}

static void config_menu(void)
{
    int selected = 0;
    MENUITEM_STRINGLIST(menu, "Solar configuration", NULL,
                        "Wi-Fi credentials",
                        "Piped API instance",
                        "Download directory",
                        "slskd URL",
                        "slskd API key",
                        "SSH host",
                        "SSH port",
                        "SSH user",
                        "SSH password",
                        "SSH private-key path",
                        "SSH known_hosts path",
                        "Accept SSH host key insecurely",
                        "LALAL.AI license key");
    selected = rb->do_menu(&menu, &selected, NULL, false);
    switch (selected)
    {
        case 0: configure_wifi(); break;
        case 1: set_config_value("piped-instance", "Piped API URL", "https://pipedapi.kavin.rocks"); break;
        case 2: set_config_value("download-dir", "Download directory", "/sdcard/Music/RockboxSolar"); break;
        case 3: set_config_value("slskd-url", "slskd URL", "http://192.168.1.2:5030"); break;
        case 4: set_config_value("slskd-api-key", "slskd API key", ""); break;
        case 5: set_config_value("ssh-host", "SSH host", ""); break;
        case 6: set_config_value("ssh-port", "SSH port", "22"); break;
        case 7: set_config_value("ssh-user", "SSH user", ""); break;
        case 8: set_config_value("ssh-password", "SSH password", ""); break;
        case 9: set_config_value("ssh-key", "Private-key path", "/sdcard/.ssh/id_ed25519"); break;
        case 10: set_config_value("ssh-known-hosts", "known_hosts path", "/sdcard/.ssh/known_hosts"); break;
        case 11: set_config_value("ssh-insecure-accept-host-key", "true or false", "false"); break;
        case 12: set_config_value("lalal-key", "LALAL.AI license key", ""); break;
        default: break;
    }
}

static void status_screen(void)
{
    int selected;
    if (run_request("config_status", NULL, NULL, NULL, NULL,
                    NULL, NULL, NULL, NULL) < 0)
        return;
    selected = choose_result("Solar status");
    (void)selected;
}

static void connect_wifi(void)
{
    const char *result = rb->android_podcast_connect_wifi();
    if (result == NULL)
        rb->splash(HZ * 2, "Wi-Fi returned no status");
    else
        rb->splash(HZ * 2, "%s", result);
}

static void disconnect_wifi(void)
{
    int result = rb->android_podcast_disconnect_wifi();
    if (result == 0)
        rb->splash(HZ * 2, "Wi-Fi disconnected");
    else
        rb->splash(HZ * 2, "Wi-Fi disconnect error: %d", result);
}

enum plugin_status plugin_start(const void *parameter)
{
    int selected = 0;
    (void)parameter;
    ensure_state_dir();

    while (true)
    {
        MENUITEM_STRINGLIST(menu, "Solar", NULL,
                            "Connect Wi-Fi",
                            "Disconnect Wi-Fi",
                            "Piped / YouTube",
                            "Deezer catalog and previews",
                            "Reach / Soulseek through slskd",
                            "Downloads",
                            "SSH / SCP",
                            "Stem separation",
                            "Configure Solar",
                            "Solar status");
        selected = rb->do_menu(&menu, &selected, NULL, false);
        switch (selected)
        {
            case 0: connect_wifi(); break;
            case 1: disconnect_wifi(); break;
            case 2: search_piped(); break;
            case 3: search_deezer(); break;
            case 4: search_slskd(); break;
            case 5: downloads(); break;
            case 6: ssh_menu(); break;
            case 7: stems_menu(); break;
            case 8: config_menu(); break;
            case 9: status_screen(); break;
            default: return PLUGIN_OK;
        }
    }
}
