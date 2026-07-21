package com.solar.launcher.radio;

import java.util.Locale;

/**
 * FM broadcast band limits per regulatory region.
 * 2026-07-20 — Step/floor match MT6627 (76–108 MHz, 50 kHz); RU OIRT below chip is clamped.
 */
public final class FmBandPlan {
  public final String regionCode;
  public final float minMhz;
  public final float maxMhz;
  public final float stepMhz;

  public FmBandPlan(String regionCode, float minMhz, float maxMhz, float stepMhz) {
    this.regionCode = regionCode;
    this.minMhz = minMhz;
    this.maxMhz = maxMhz;
    this.stepMhz = stepMhz;
  }

  /**
   * Resolve plan for region code; unknown codes fall back to US.
   * 2026-07-20 — All regions use 0.05 MHz (50 kHz) to match MT6627 seek grid.
   * RU: keep label RU but clamp to 76–108 (chip floor); old OIRT 65.9–74 was untunable.
   * Layman: radio dial steps like the chip; Russia no longer aims below what hardware can hear.
   * Reversal: step 0.1f everywhere; RU min 65.9 / max 74.0.
   */
  public static FmBandPlan fromRegionCode(String regionCode) {
    // 2026-07-20 — Shared 50 kHz grid for every region plan below.
    final float step = 0.05f;
    String r = regionCode == null ? "US" : regionCode.trim().toUpperCase(Locale.US);
    if ("EU".equals(r)) return new FmBandPlan("EU", 87.5f, 108.0f, step);
    if ("JP".equals(r)) return new FmBandPlan("JP", 76.0f, 90.0f, step);
    if ("AU".equals(r)) return new FmBandPlan("AU", 87.5f, 108.0f, step);
    if ("KR".equals(r)) return new FmBandPlan("KR", 88.0f, 108.0f, step);
    // 2026-07-20 — MT6627 cannot tune classic RU OIRT; stay on chip 76–108 window.
    if ("RU".equals(r)) return new FmBandPlan("RU", 76.0f, 108.0f, step);
    // US / CA / default — includes 87.9 commercial low end
    return new FmBandPlan("US", 87.9f, 107.9f, step);
  }

  public int minKhz() {
    return Math.round(minMhz * 1000f);
  }

  public int maxKhz() {
    return Math.round(maxMhz * 1000f);
  }

  public int stepKhz() {
    return Math.max(1, Math.round(stepMhz * 1000f));
  }

  /** Clamp and snap frequency to band grid. */
  public int clampKhz(int khz) {
    int min = minKhz();
    int max = maxKhz();
    int step = stepKhz();
    if (khz < min) khz = min;
    if (khz > max) khz = max;
    int offset = khz - min;
    int steps = Math.round((float) offset / (float) step);
    return min + steps * step;
  }

  /** Human label e.g. {@code 101.1}. */
  public static String formatMhz(float mhz) {
    return String.format(Locale.US, "%.1f", mhz);
  }

  /**
   * Fractional MHz display for kHz input — e.g. 101100 → {@code 101.1}.
   * 2026-07-20 — Still one decimal in UI; snap uses 50 kHz under the hood.
   */
  public static String khzToFraction(int khz, FmBandPlan plan) {
    if (plan == null) plan = fromRegionCode("US");
    int clamped = plan.clampKhz(khz);
    return formatMhz(clamped / 1000f);
  }
}
