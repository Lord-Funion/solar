package com.solar.launcher;

/**
 * Intents and permissions for the system-wide Solar context overlay (Xposed bridge → Solar).
 */
public final class OverlayTriggers {

    /** Only system/signature callers may trigger overlay broadcasts. */
    public static final String PERMISSION = "com.solar.launcher.permission.OVERLAY_TRIGGER";

    /** Y2 power-hold / Y1 BACK-long in stock apps — open quick modal at power tier. */
    public static final String ACTION_SHOW_OVERLAY_POWER =
            "com.solar.launcher.action.SHOW_OVERLAY_POWER";

    /** App context menu redecoration — plain text rows only (no parcelables). */
    public static final String ACTION_SHOW_OVERLAY_APP_MENU =
            "com.solar.launcher.action.SHOW_OVERLAY_APP_MENU";

    /** Selection/cancel from overlay — delivered to the hooked app process (primitives only). */
    public static final String ACTION_APP_MENU_RESULT =
            "com.solar.launcher.action.APP_MENU_RESULT";

    /** Stock AlertDialog replacement — scrollable message + action rows. */
    public static final String ACTION_SHOW_OVERLAY_NATIVE_DIALOG =
            "com.solar.launcher.action.SHOW_OVERLAY_NATIVE_DIALOG";

    /** AlertDialog button pick / Back cancel — delivered to hooked app process. */
    public static final String ACTION_DIALOG_RESULT =
            "com.solar.launcher.action.DIALOG_RESULT";

    /** Multiline dialog body (Details metadata, confirm message, etc.). */
    public static final String EXTRA_DIALOG_MESSAGE = "dialog_message";

    /** Action button labels in order (OK, Cancel, …). */
    public static final String EXTRA_DIALOG_BUTTONS = "dialog_buttons";

    /** Passive volume HUD — slider only, no key gate (Rockbox keeps foreground focus). */
    public static final String ACTION_SHOW_OVERLAY_VOLUME =
            "com.solar.launcher.action.SHOW_OVERLAY_VOLUME";

    /** Passive toast hint — compact themed message, no key gate (like volume HUD). */
    public static final String ACTION_SHOW_OVERLAY_TOAST =
            "com.solar.launcher.action.SHOW_OVERLAY_TOAST";

    public static final String EXTRA_TOAST_TEXT = "toast_text";

    /** Duration ms — maps from Toast.LENGTH_SHORT / LENGTH_LONG. */
    public static final String EXTRA_TOAST_DURATION_MS = "toast_duration_ms";

    /** Replace stock HOME resolver (Always / Just once) — wheel-friendly launcher picker. */
    public static final String ACTION_SHOW_OVERLAY_LAUNCHER_PICKER =
            "com.solar.launcher.action.SHOW_OVERLAY_LAUNCHER_PICKER";

    /** Repeated HOME launcher crash — fallback picker with keep-Solar option. */
    public static final String ACTION_SHOW_OVERLAY_LAUNCHER_RECOVERY =
            "com.solar.launcher.action.SHOW_OVERLAY_LAUNCHER_RECOVERY";

    public static final String EXTRA_RECOVERY_PROCESS = "recovery_process";

    /** Warm {@code :overlay} process — no UI; GlobalActions can show modal while Rockbox is HOME. */
    public static final String ACTION_OVERLAY_KEEPALIVE =
            "com.solar.launcher.action.OVERLAY_KEEPALIVE";

    /** Main process theme change — reload overlay theme cache without tearing down UI. */
    public static final String ACTION_OVERLAY_THEME_RELOAD =
            "com.solar.launcher.action.OVERLAY_THEME_RELOAD";

    /** String[] menu item titles from the hooked app menu. */
    public static final String EXTRA_MENU_TITLES = "menu_titles";

    /** Opaque session id — hook maps this back to Menu.performItemAction in the caller app. */
    public static final String EXTRA_MENU_SESSION_ID = "menu_session_id";

    /** Parallel to menu titles — true when row opens a submenu instead of firing immediately. */
    public static final String EXTRA_MENU_HAS_SUBMENU = "menu_has_submenu";

    /** Package that registered the pending menu (result broadcast target). */
    public static final String EXTRA_MENU_CALLER_PACKAGE = "menu_caller_package";

    /** Selected row index, or -1 when the user dismisses without choosing. */
    public static final String EXTRA_SELECTED_INDEX = "menu_selected_index";

    /** system_server → Solar while overlay is active (primitive keyCode only). */
    public static final String ACTION_OVERLAY_KEY =
            "com.solar.launcher.action.OVERLAY_KEY";

    public static final String EXTRA_KEY_CODE = "overlay_key_code";

    /** Optional scancode — A5 face mid (158) vs side power (116) when remapping in IME. */
    public static final String EXTRA_SCAN_CODE = "overlay_scan_code";

    /** {@link android.view.KeyEvent#ACTION_DOWN} or {@link android.view.KeyEvent#ACTION_UP}. */
    public static final String EXTRA_KEY_ACTION = "overlay_key_action";

    /** Optional dialog title for app-menu overlay. */
    public static final String EXTRA_MENU_TITLE = "menu_title";

    /** Launch MainActivity context menu at a quick-bar chip (overlay handoff). */
    public static final String ACTION_OPEN_CONTEXT_MENU =
            "com.solar.launcher.action.OPEN_CONTEXT_MENU";

    public static final String EXTRA_CONTEXT_QUICK_INDEX = "context_quick_index";

    /** Y2 power-hold in Solar — open in-app quick bar at power tier (skip screen-specific rows). */
    public static final String EXTRA_CONTEXT_POWER_HOLD = "context_power_hold";

    /**
     * 2026-07-20 — Xposed/root → MainActivity: Power DOWN while Solar fg — arm Options hold spinner.
     * Layman: same status spinner as hold-Back, as soon as you press Power for the menu.
     * Reversal: drop receiver; spinner only arms in handleContextPowerHoldIntent after hold fires.
     */
    public static final String ACTION_CONTEXT_HOLD_ARM =
            "com.solar.launcher.action.CONTEXT_HOLD_ARM";

    /**
     * 2026-07-20 — Xposed/root → MainActivity: Power UP before menu opened — clear hold spinner.
     * Layman: let go early and the spinner goes away like a cancelled Back-hold.
     */
    public static final String ACTION_CONTEXT_HOLD_CANCEL =
            "com.solar.launcher.action.CONTEXT_HOLD_CANCEL";

    /** SystemUI USB enable prompt — global overlay over any foreground app. */
    public static final String ACTION_SHOW_OVERLAY_USB_STORAGE =
            "com.solar.launcher.action.SHOW_OVERLAY_USB_STORAGE";

    /**
     * 2026-07-08 — UMS already exported — fullscreen lock modal (companion primary).
     * Layman: “disk mode on” screen over any app; no Solar Home launch.
     * Was: EXTRA_USB_OVERLAY_LOCK → MainActivity.STATE_USB_STORAGE.
     */
    public static final String ACTION_SHOW_OVERLAY_USB_STORAGE_LOCK =
            "com.solar.launcher.action.SHOW_OVERLAY_USB_STORAGE_LOCK";

    /** :overlay / companion → main: UMS lock painted — pause players if process alive. */
    public static final String ACTION_USB_STORAGE_LOCKED =
            "com.solar.launcher.action.USB_STORAGE_LOCKED";

    /** Lock ended (Turn Off or cable unplug) — clear main-process lock flags if alive. */
    public static final String ACTION_USB_STORAGE_UNLOCKED =
            "com.solar.launcher.action.USB_STORAGE_UNLOCKED";

    /**
     * 2026-07-08 — User forced Turn Off while PC cable still connected.
     * Marks host session dismissed until next unplug (not set on cable-unplug alone).
     */
    public static final String EXTRA_USB_FORCE_OFF = "usb_force_off";

    /** Bluetooth pairing PIN / passkey / consent — global overlay over any foreground app. */
    public static final String ACTION_SHOW_OVERLAY_BT_PAIRING =
            "com.solar.launcher.action.SHOW_OVERLAY_BT_PAIRING";

    /** {@link BluetoothPairingCoordinator#MODE_PIN} etc. */
    public static final String EXTRA_BT_PAIRING_MODE = "bt_pairing_mode";

    public static final String EXTRA_BT_PAIRING_ADDRESS = "bt_pairing_address";

    public static final String EXTRA_BT_PAIRING_NAME = "bt_pairing_name";

    public static final String EXTRA_BT_PAIRING_PASSKEY = "bt_pairing_passkey";

    /** Optional seed for PIN keyboard (saved/default PIN). */
    public static final String EXTRA_BT_PAIRING_PIN_PREFILL = "bt_pairing_pin_prefill";

    /** True when UMS is already exported — jump to Solar USB lock screen, not enable prompt. */
    public static final String EXTRA_USB_STORAGE_LOCK = "usb_storage_lock";

    /** Tear down any global overlay tier without restoring Solar HOME. */
    public static final String ACTION_DISMISS_OVERLAY =
            "com.solar.launcher.action.DISMISS_OVERLAY";

    /** :overlay → main process — re-arm wheel inject after global modal closes. */
    public static final String ACTION_OVERLAY_DISMISSED =
            "com.solar.launcher.action.OVERLAY_DISMISSED";

    /** :overlay → main process — pause stock-app inject while modal is up. */
    public static final String ACTION_OVERLAY_ARMED =
            "com.solar.launcher.action.OVERLAY_ARMED";

    /** Ultra-long BACK (6s+) — force-stop Solar and relaunch HOME from any foreground app. */
    public static final String ACTION_RESTART_SOLAR =
            "com.solar.launcher.action.RESTART_SOLAR";

    /** system_server / root daemon → Solar IME tray while sys.solar.ime.active=1. */
    public static final String ACTION_IME_KEY =
            "com.solar.launcher.action.IME_KEY";

    /** Tier-2 Xposed session commit — text or delete fallback. */
    public static final String ACTION_IME_COMMIT =
            "com.solar.launcher.action.IME_COMMIT";

    /** Short BACK while IME up — dismiss keyboard without injecting BACK to foreground app. */
    public static final String ACTION_IME_DISMISS =
            "com.solar.launcher.action.IME_DISMISS";

    /** Keep {@link SolarRescueHoldService} (:hold) polling — survives main/overlay death. */
    public static final String ACTION_RESCUE_HOLD_KEEPALIVE =
            "com.solar.launcher.action.RESCUE_HOLD_KEEPALIVE";

    /** Hold just armed — refresh countdown HUD immediately. */
    public static final String ACTION_RESCUE_HOLD_TICK =
            "com.solar.launcher.action.RESCUE_HOLD_TICK";

    public static final String EXTRA_IME_TEXT = "ime_text";

    public static final String EXTRA_IME_DELETE = "ime_delete";

    private OverlayTriggers() {}
}
