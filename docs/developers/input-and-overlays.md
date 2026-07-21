# Input and overlays

Input on Solar OS is **layered, not monolithic**. A shared policy JAR defines hold timings and who may intercept keys; Xposed hooks in `system_server` are tier 1 for **Solar-only** quick menus + HOLD BACK → Solar; volume OSD and rescue remain system-wide; root evdev is tier 3 miss-gate.

**Source of truth:** `solar-rom/vendor/global-input-policy/GlobalInputPolicy.java` (`POLICY_REV` **27**) — compiled into bridge, companion, Solar, and daemon.

**2026-07-14 product contract:** context / power quick menus open **only while Solar is foreground** (in-app `ThemedContextMenu`). Outside Solar on Y2, POWER hold shows the **stock Android power menu**. HOLD BACK outside Solar **launches Solar Home** (no WM quick shell). Volume OSD + 10s rescue unchanged.

**2026-07-15 (POLICY_REV 23):** Y2 short POWER force-`goToSleep` only when the screen was **on at POWER DOWN** (`shouldForcePowerTapSleep`). UP-time `isScreenOn` is already true after a wake, so a wake tap must not call `goToSleep`. Sleep also dismisses in-app / WM overlays and treats an open USB connect prompt as dismissed.

**2026-07-20 (POLICY_REV 27):** Modal / Solar BACK / center holds **280 ms**; `POWER_TAP_MAX_MS` **220 ms** (must stay under hold). Snappier Options open while accidental-tap guard remains.

**2026-07-15 volume HUD (Y2 / A5):** On music Now Playing or video playback, hardware volume keys adjust level and pulse the transport bar only — **no** compact volume overlay. Exception: if an in-app or global context modal is open, volume **clears that modal activity** and shows the compact volume bar instead (`VolumeHudPolicy`, `SolarUiState.persist.solar.ui.now_playing`).

---

## Mutex order (highest wins)

```
Overlay active/opening  >  IME tray  >  ExternalInputHandoff  >  stock key delivery
```

Only one tier owns a concern at a time. Lower tiers arm on **miss** only.

---

## Tier ladder

| Tier | Mechanism | Blocks |
|------|-----------|--------|
| **1 — Xposed PWM** | `SystemServerHooks`, `OverlayKeyForwarder`, `ImeKeyForwarder` | Fg app keys while overlay/IME armed |
| **1b — Companion FSM** | `GlobalInputCoordinatorService` (Y2 power when companion installed) | Duplicate holds |
| **2 — WM overlay** | `GlobalContextOverlayService` (sole shell; `SolarOverlayService` only if `persist.solar.overlay.legacy_shell=1`) | User-visible modal |
| **2 — App hooks** | `ActivityOverlayKeyHooks` | Fallback when PWM misses in app process |
| **3 — Root evdev** | `GlobalOverlayTriggerMain` | BACK/POWER when Xposed misses; forwards keys while overlay up |
| **4 — Stock** | Keylayout → Activity | Default Android delivery |

---

## Hold timing constants

| Constant | ms | Use |
|----------|-----|-----|
| `POWER_TAP_MAX_MS` | 220 | Y2 short power → sleep when screen was on at DOWN |
| `GLOBAL_MODAL_HOLD_MS` | 280 | Third-party BACK → return to Solar; Solar POWER → in-app menu |
| `NAV_OWNED_LAUNCHER_MODAL_HOLD_MS` | 300 | Rockbox/JJ passthrough ceiling; JJ BACK-long modal |
| `SOLAR_BACK_CONTEXT_HOLD_MS` | 280 | Solar in-app BACK menu |
| `CENTER_MENU_HOLD_MS` | 280 | OK-long row context menu |
| `HUD_COUNTDOWN_START_MS` | 7000 | Rescue countdown 3..2..1 visible |
| `RESCUE_EXECUTE_MS` | 10000 | Continuous hold → Solar restart / `solar-rescue-exec.sh` |
| `SUB_TIER_CENTER_GRACE_MS` / `APP_MENU_CENTER_GRACE_MS` | 595 | Overlay: ignore center KEY_UP after submenu paint (Power/Wi‑Fi/APP_MENU) |

Per-package delay: `backModalHoldMsForPackage(fg)` / `powerModalHoldMsForPackage(fg)`.

### Overlay center/OK activation (2026-07-08)

Global quick modal (`OverlayModalHost`) activates chips and list rows on **KEY_UP**, not KEY_DOWN — same as the in-app context menu and APP_MENU tier. After a chip opens a sub-tier (Power focuses Restart, Wi‑Fi list, confirm sheet), `OverlayCenterActivation` blocks center UP for `SUB_TIER_CENTER_GRACE_MS` so the opening press cannot chain into Restart → reboot. WM path also drops center/play **auto-repeat** DOWNs (`KeyCapturingOverlayRoot`).

| Event | Behavior |
|-------|----------|
| Center DOWN | Swallow (queue list may arm long-press move) |
| Center UP (after grace) | `activateFocused()` — chip or row |
| Center UP within grace | Swallow |
| Center DOWN `repeatCount > 0` | Swallow (WM + Xposed) |

---

## Launcher special cases

| Foreground | BACK-long (~280/300 ms) | POWER-long (Y2) | Handoff |
|------------|-------------------------|-----------------|---------|
| `com.solar.launcher` | In-app menu (`MainActivity`) | In-app menu (suppress stock) | `MODE_OFF` |
| `org.rockbox` | **Never** (owns BACK); IME may escape → Solar | **Stock** GlobalActions | `MODE_OFF` |
| `com.themoon.y1` (JJ) | Launch Solar Home | **Stock** GlobalActions | `MODE_JJ` |
| `com.innioasis.y1` / `.y2` (stock HOME) | Launch Solar Home | **Stock** GlobalActions | `MODE_JJ` |
| Other `com.innioasis.*` (music/fm) | No (denylist) | Stock / no Solar menu | `MODE_OFF` |
| Other third-party | Launch Solar Home | **Stock** GlobalActions | `MODE_ANDROID` / `MODE_FM` |

Rockbox remains special for **BACK** (passthrough). Y2 POWER outside Solar no longer opens a Solar WM shell.

---

## Sysprops (cross-process contract)

| Property | Writer | Purpose |
|----------|--------|---------|
| `sys.solar.overlay.active` | `CompanionOverlayKeyGate` / `OverlayKeyGate` | Overlay owns keys |
| `sys.solar.overlay.ui` | Companion / Solar gate | Menu painted |
| `sys.solar.overlay.opening` | Gate + Xposed early arm | Duplicate open guard |
| `sys.solar.overlay.opening_at` | Gate | Stale opening heal |
| `sys.solar.overlay.shell_visible` | Companion / Solar shell at `addView`/`removeView` | WM shell attached (independent of gate) |
| `persist.solar.overlay.legacy_shell` | Manual / rollback | `1` = Solar `:overlay` paints again |
| `sys.solar.ime.active` | `SolarImeRouteArbiter` | IME tray owns keys |
| `sys.solar.handoff.active` | `ExternalInputHandoff` | Wheel→DPAD inject active |
| `sys.solar.handoff.jj` | `ExternalInputHandoff` + root switch/boot scripts | JJ/stock horizontal wheel shim |
| `persist.solar.home.target` | `LauncherPreference`, rescue | Effective HOME |
| `persist.solar.ui.now_playing` | `SolarUiState` via MainActivity | `1` = NP/video visible — VolumePanelHooks / `:overlay` skip new volume HUD |
| `sys.solar.input.policy_rev` | Boot | Policy JAR version sanity (`23` = short POWER sleep only when screen on at DOWN) |

**Rule:** Xposed and root tiers are read-only consumers of IME/overlay props except where documented.

### Solar-independent wheel remap (2026-07-08)

JJ and stock Innioasis HOME must keep full wheel/side/center input **even when the Solar app is pm-disabled, crashed, or LMK-killed**:

1. Root switch scripts (`solar-launcher-exec.sh`, `switch-to-stock.sh` legacy path) and boot init (`apply-preferred-home-boot.sh`) set `sys.solar.handoff.jj=1` whenever `persist.solar.home.target` is `jj` or `stock` — no Solar process involved.
2. `JjInputHooks` (context bridge, in-process) additionally falls back to `persist.solar.home.target` via `GlobalInputPolicy.isJjKeylayoutHomeTarget()` when the sys prop is unset — the remap self-arms from the launcher-of-record.
3. Solar's `ExternalInputHandoff` (MEDIA_BUTTON + root inject) remains the enhancement tier when Solar is alive; it is no longer the only arming authority.

The remap itself is unchanged: wheel `MEDIA_PLAY`/`MEDIA_PAUSE` (126/127 from canonical `.kl` scancodes 105/106 Y1, 103/108 Y2) → `DPAD_LEFT`/`DPAD_RIGHT` (21/22, matching the stock firmware keylayout JJ and the factory launcher were built against). Side keys 163/165 (`MEDIA_NEXT`/`MEDIA_PREVIOUS`) and center/BACK pass through untouched.

### Stuck-shell BACK heal (2026-07-08)

When `shell_visible=1` but capture is **not** armed (`active`/`opening` off), a BACK press that reaches the foreground app also fires `DISMISS_OVERLAY` to both shells (debounced 1s). Normal armed modal path is unchanged — keys stay swallowed + forwarded to the overlay.

Decision: `StaleOverlayGate.shouldDismissStuckShellOnBack` · fire point: `OverlayKeyForwarder.maybeHealStuckShellOnBack`.

---

## Key source files

| File | Role |
|------|------|
| `GlobalInputPolicy.java` | Hold ms, eligibility, passthrough |
| `GlobalOverlayPolicy.java` | Solar-side delegate + emergency mode |
| `SystemServerHooks.java` | PWM BACK/POWER/center hooks |
| `OverlayKeyForwarder.java` | Swallow + forward to overlay |
| `GlobalContextOverlayService.java` | Sole WM overlay shell (companion) |
| `CompanionOverlayKeyGate.java` | Arm/disarm overlay props (companion writer) |
| `OverlayKeyGate.java` | Legacy Solar gate + stale heal |
| `OverlayCenterActivation.java` | KEY_UP + sub-tier grace for overlay OK |
| `OverlayModalHost.java` | Legacy Solar quick bar / Power tier |
| `SolarOverlayClient.java` | Start companion shell from system_server |
| `SolarImeRouteArbiter.java` | IME mutex |
| `ExternalInputHandoff.java` | MEDIA_BUTTON → DPAD inject |
| `GlobalOverlayTriggerMain.java` | Root evdev fallback |
| `ToastHooks.java` | Stock Toast → passive hint HUD |
| `AppAnrHooks.java` / `AppErrorHooks.java` | ANR/crash replace (incl. system) + 2s fail-open |
| `SystemErrorDialogRouting.java` | Which processes replace ANR; wheel button order |
| `CompanionTierScheduler.java` | Native-error queues USB; else in-place tier morph |
| `UsbStorageOverlayReceiver.java` | USB always routes to the one shell (Solar fg included) |

### Sole shell + USB takeover (2026-07-08)

One WM window (`TYPE_SYSTEM_ERROR`) owns all system-facing menus. USB enable/lock never opens a second surface: if power/app-menu/volume is up, the shell morphs in place; if `native_error` is up, USB queues (`pending_usb`) and advances on dismiss. Rollback to Solar `:overlay` paint: `persist.solar.overlay.legacy_shell=1`.

---

## Y1 vs Y2 vs A5

| | Y1 | Y2 | A5 |
|---|----|----|-----|
| Modal trigger | BACK long | BACK or POWER long | (touch / companion) |
| Rockbox modal | BACK never | POWER yes | N/A (no Rockbox) |
| Single-press screen sleep | OK hold ~300ms (`centerHoldShouldSleep`; suppressed during queue/playlist/FM move) | Short POWER when screen was on at DOWN (`shouldForcePowerTapSleep`) | Overlay Sleep/Zzz chip (side button stays Back) |
| Screen wake | OK DOWN nudge / system | Stock short POWER (no Solar `goToSleep` when dark at DOWN) | System / Zzz cycle |
| Volume chip in overlay | Yes | Hidden | Yes |
| Sleep/Zzz chip (rightmost after Volume) | Yes | Hidden | Yes |
| Power chip (Restart / Shut down) | When rooted | When rooted | When rooted (`hasRootAccess`; silent su, not RootShell) |
| INJECT_EVENTS handoff | Often works | Usually root daemon path | Touch-first |
| Bridge APK | `SolarContextBridgeY1` | `SolarContextBridgeY2` | Y1 bridge on A5 ROM |

---

## Verification

After input policy changes:

1. Rebuild context bridge APKs.
2. `adb install` + reboot on **both** Y1 and Y2.
3. `./solar-rom/scripts/audit-device-parity.sh` on each device.
4. Test matrix (manual on hardware):
   - Settings — wheel scrolls (handoff DPAD)
   - Third-party app — `openOptionsMenu()` → overlay rows; selection executes
   - AlertDialog — overlay native tier; OK/Cancel wheel-navigable
   - Solar ANR — Wait/Close on overlay or stock Holo + `AnrDialogKeyForwarder` fail-open
   - Toast from stock app — passive hint bar or stock Toast fail-open
   - Rockbox BACK never opens modal; Y2 POWER-long OK on Rockbox
   - JJ BACK-long at ~300 ms; generic PM HOME at 420 ms
   - Power chip OK → Restart/Shutdown/launcher rows stay visible; hold OK on chip must not reboot
5. Test: short BACK in Rockbox/JJ reaches app; long hold opens modal only when policy says yes.

---

## Related

- [Third-party apps](third-party-apps.md) — IME + handoff detail
- [Third-party overlay integration](third-party-overlay-integration.md)
- [Launchers and home routing](launchers-and-home-routing.md)
- [Troubleshooting](../ecosystem/troubleshooting.md)
- [Global quick menu (user)](../using-solar/global-quick-menu.md)
