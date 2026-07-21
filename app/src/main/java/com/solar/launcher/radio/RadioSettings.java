package com.solar.launcher.radio;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;

import java.util.Locale;

/**
 * FM + internet radio user prefs — band region, country filter, buffer location, last station.
 * 2026-07-15 — Last kHz so reopen lands on the dial you left, not a fixed 101.1.
 */
public final class RadioSettings {
  public static final String PREF_FM_BAND_REGION = "fm_band_region";
  public static final String PREF_INTERNET_RADIO_COUNTRY = "internet_radio_country";
  /** 2026-07-20 — Optional Radio Browser state/region name; empty = whole country. */
  public static final String PREF_INTERNET_RADIO_STATE = "internet_radio_state";
  public static final String PREF_AUTO_DETECT_REGION = "auto_detect_region";
  public static final String PREF_BUFFER_ON_SD = "buffer_on_sd";
  /** Last tuned/played FM kHz — 0 means never saved. */
  public static final String PREF_LAST_FM_KHZ = "last_fm_khz";

  private static final String PREFS = "radio_settings";
  private static final String DEFAULT_REGION = "US";
  private static final String DEFAULT_COUNTRY = "US";

  private RadioSettings() {}

  private static SharedPreferences prefs(Context ctx) {
    return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  /**
   * Last FM frequency in kHz, or 0 if the user has never tuned.
   * Layman: remembers the station you were listening to.
   */
  public static int getLastFmKhz(Context ctx) {
    if (ctx == null) return 0;
    return prefs(ctx).getInt(PREF_LAST_FM_KHZ, 0);
  }

  /**
   * Persist last FM dial position after a successful tune/play.
   * Technical: unclamped store; callers clamp with {@link FmBandPlan} on read.
   */
  public static void setLastFmKhz(Context ctx, int khz) {
    if (ctx == null || khz <= 0) return;
    prefs(ctx).edit().putInt(PREF_LAST_FM_KHZ, khz).commit();
  }

  /**
   * Effective FM band region for tuning.
   * 2026-07-15 — When auto-detect is on (default), use SIM/locale band; stored value is ignored
   * until the user turns auto-detect off or picks a band manually.
   * Layman: Radio follows your country by default so EU/JP dials aren't stuck on US limits.
   * Reversal: always return stored pref (old: default US even while auto-detect claimed on).
   */
  public static String getFmBandRegion(Context ctx) {
    if (getAutoDetectRegion(ctx)) {
      return normalizeRegion(detectFmBandFromLocale(ctx));
    }
    return normalizeRegion(prefs(ctx).getString(PREF_FM_BAND_REGION, DEFAULT_REGION));
  }

  public static void setFmBandRegion(Context ctx, String region) {
    prefs(ctx).edit().putString(PREF_FM_BAND_REGION, normalizeRegion(region)).commit();
  }

  /**
   * Stored Online Radio country filter (defaults to US when unset).
   * Prefer {@link #effectiveInternetRadioCountry} when pinning browse focus.
   */
  public static String getInternetRadioCountry(Context ctx) {
    return normalizeCountry(prefs(ctx).getString(PREF_INTERNET_RADIO_COUNTRY, DEFAULT_COUNTRY));
  }

  public static void setInternetRadioCountry(Context ctx, String country) {
    prefs(ctx).edit().putString(PREF_INTERNET_RADIO_COUNTRY, normalizeCountry(country)).commit();
  }

  /**
   * Best country for Online Radio: saved pref → IP geo → SIM/locale → US.
   * 2026-07-20 — Soft-filled pref wins; otherwise guess so the country list can pin/focus home.
   * Layman: opens Online Radio near your country without forcing you into it.
   * Reversal: callers used {@link #getInternetRadioCountry} only (pref default US, no ladder).
   */
  public static String effectiveInternetRadioCountry(Context ctx) {
    if (ctx != null) {
      SharedPreferences p = prefs(ctx);
      // 2026-07-20 — Only trust an explicit write (geo soft-apply or user pick), not the US default.
      if (p.contains(PREF_INTERNET_RADIO_COUNTRY)) {
        String stored = p.getString(PREF_INTERNET_RADIO_COUNTRY, "");
        if (stored != null && stored.trim().length() == 2) {
          return normalizeCountry(stored);
        }
      }
      try {
        String geo = com.solar.launcher.SolarGeoRegion.countryCode(ctx);
        if (geo != null && geo.length() == 2) return normalizeCountry(geo);
      } catch (Exception ignored) {}
    }
    String iso = detectCountryIsoSimOrLocale(ctx);
    if (iso != null && iso.length() == 2) return normalizeCountry(iso);
    return DEFAULT_COUNTRY;
  }

  /**
   * Optional Radio Browser state/region under the country; empty means whole country.
   * 2026-07-20 — Remembers last state pick so browse can focus that row later.
   * Layman: if you drilled into California, we remember; blank = all stations in the country.
   * Reversal: no state pref — browse always starts at country-wide tags/stations.
   */
  public static String getInternetRadioState(Context ctx) {
    if (ctx == null) return "";
    String s = prefs(ctx).getString(PREF_INTERNET_RADIO_STATE, "");
    return s == null ? "" : s.trim();
  }

  /**
   * Persist optional state name (may be empty to clear / whole-country).
   * 2026-07-20 — Keep display spelling from Radio Browser; do not force ISO uppercasing.
   */
  public static void setInternetRadioState(Context ctx, String state) {
    if (ctx == null) return;
    String v = state == null ? "" : state.trim();
    prefs(ctx).edit().putString(PREF_INTERNET_RADIO_STATE, v).commit();
  }

  public static boolean getAutoDetectRegion(Context ctx) {
    return prefs(ctx).getBoolean(PREF_AUTO_DETECT_REGION, true);
  }

  public static void setAutoDetectRegion(Context ctx, boolean enabled) {
    prefs(ctx).edit().putBoolean(PREF_AUTO_DETECT_REGION, enabled).commit();
  }

  public static boolean getBufferOnSd(Context ctx) {
    return prefs(ctx).getBoolean(PREF_BUFFER_ON_SD, true);
  }

  public static void setBufferOnSd(Context ctx, boolean onSd) {
    prefs(ctx).edit().putBoolean(PREF_BUFFER_ON_SD, onSd).commit();
  }

  /**
   * Guess FM band from IP geo cache, then SIM/network ISO, then device locale.
   * 2026-07-16 — Prefer SolarGeoRegion IP country so Wi‑Fi-only devices still get home FM band.
   * Coarse ISO→region map — add per-country overrides if users report wrong band.
   */
  public static String detectFmBandFromLocale(Context ctx) {
    String iso = null;
    if (ctx != null) {
      try {
        String geo = com.solar.launcher.SolarGeoRegion.countryCode(ctx);
        if (geo != null && geo.length() == 2) iso = geo;
      } catch (Exception ignored) {}
    }
    if (iso == null || iso.length() != 2) {
      iso = detectCountryIsoSimOrLocale(ctx);
    }
    return isoToFmRegion(iso);
  }

  /**
   * SIM/network then locale ISO-2; null if nothing usable.
   * 2026-07-20 — Shared by FM auto-detect and Online Radio effective country (after pref/geo).
   * Layman: if no saved country and no IP guess, use the SIM or phone language region.
   */
  static String detectCountryIsoSimOrLocale(Context ctx) {
    String iso = null;
    if (ctx != null) {
      try {
        TelephonyManager tm =
            (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) {
          String network = tm.getNetworkCountryIso();
          if (network != null && network.length() == 2) iso = network;
          if (iso == null || iso.length() != 2) {
            String sim = tm.getSimCountryIso();
            if (sim != null && sim.length() == 2) iso = sim;
          }
        }
      } catch (Exception ignored) {}
    }
    if (iso == null || iso.length() != 2) {
      Locale locale = Locale.getDefault();
      if (locale != null && locale.getCountry() != null && locale.getCountry().length() == 2) {
        iso = locale.getCountry();
      }
    }
    if (iso == null || iso.length() != 2) return null;
    return iso.toUpperCase(Locale.US);
  }

  /** Testable without TelephonyManager. Public for geo soft-apply. */
  public static String isoToFmRegion(String iso) {
    if (iso == null || iso.length() != 2) return DEFAULT_REGION;
    String c = iso.toUpperCase(Locale.US);
    if ("US".equals(c) || "CA".equals(c) || "MX".equals(c)) return "US";
    if ("JP".equals(c)) return "JP";
    if ("AU".equals(c) || "NZ".equals(c)) return "AU";
    if ("KR".equals(c)) return "KR";
    if ("RU".equals(c)) return "RU";
    // EU + most of world uses 87.5–108 MHz plan
    return "EU";
  }

  static String normalizeRegion(String region) {
    if (region == null || region.trim().isEmpty()) return DEFAULT_REGION;
    return region.trim().toUpperCase(Locale.US);
  }

  static String normalizeCountry(String country) {
    if (country == null || country.trim().isEmpty()) return DEFAULT_COUNTRY;
    return country.trim().toUpperCase(Locale.US);
  }
}
