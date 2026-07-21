package com.solar.launcher.radio;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FmBandPlanTest {

  @Test
  public void usBand_includesCommercialLowEnd() {
    FmBandPlan us = FmBandPlan.fromRegionCode("US");
    assertEquals(87900, us.clampKhz(87900));
    assertEquals(107900, us.clampKhz(107900));
    assertEquals("87.9", FmBandPlan.khzToFraction(87900, us));
  }

  @Test
  public void jpBand_lowerRange() {
    FmBandPlan jp = FmBandPlan.fromRegionCode("JP");
    assertEquals(76000, jp.minKhz());
    assertEquals(90000, jp.maxKhz());
    assertEquals("76.0", FmBandPlan.formatMhz(76.0f));
  }

  @Test
  public void unknownRegion_fallsBackToUs() {
    FmBandPlan plan = FmBandPlan.fromRegionCode("ZZ");
    assertEquals("US", plan.regionCode);
    assertEquals(87900, plan.minKhz());
  }

  @Test
  public void clampSnapsToStep() {
    FmBandPlan eu = FmBandPlan.fromRegionCode("EU");
    // 2026-07-20 — 50 kHz grid: 101.105 → nearest 101.100.
    assertEquals(101100, eu.clampKhz(101105));
  }

  @Test
  public void allRegions_use50kHzStep() {
    // 2026-07-20 — MT6627 seek step; was 100 kHz software grid.
    for (String code : new String[] {"US", "EU", "JP", "AU", "KR", "RU"}) {
      assertEquals(code, 50, FmBandPlan.fromRegionCode(code).stepKhz());
    }
  }

  @Test
  public void ruBand_clampedToMt6627Floor() {
    // 2026-07-20 — Classic OIRT 65.9–74 is below chip; keep RU label, hear 76–108.
    FmBandPlan ru = FmBandPlan.fromRegionCode("RU");
    assertEquals("RU", ru.regionCode);
    assertEquals(76000, ru.minKhz());
    assertEquals(108000, ru.maxKhz());
    assertEquals(76000, ru.clampKhz(65900));
    assertEquals(108000, ru.clampKhz(110000));
  }

  @Test
  public void clampSnapsToHalfChannel() {
    FmBandPlan eu = FmBandPlan.fromRegionCode("EU");
    // Midway between 101.10 and 101.15 → rounds up to 101.15 on 50 kHz grid.
    assertEquals(101150, eu.clampKhz(101125));
  }

  @Test
  public void radioSettings_isoMapping() {
    assertEquals("US", RadioSettings.isoToFmRegion("us"));
    assertEquals("JP", RadioSettings.isoToFmRegion("jp"));
    assertEquals("EU", RadioSettings.isoToFmRegion("de"));
    assertEquals("RU", RadioSettings.isoToFmRegion("ru"));
  }

  @Test
  public void radioSettings_normalizeCountry() {
    assertEquals("US", RadioSettings.normalizeCountry(null));
    assertEquals("US", RadioSettings.normalizeCountry(""));
    assertEquals("DE", RadioSettings.normalizeCountry(" de "));
  }

  @Test
  public void radioSettings_normalizeRegion() {
    assertEquals("US", RadioSettings.normalizeRegion(null));
    assertEquals("EU", RadioSettings.normalizeRegion("eu"));
  }
}
