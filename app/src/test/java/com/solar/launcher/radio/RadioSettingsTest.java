package com.solar.launcher.radio;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * RadioSettings helpers that need no Android Context.
 * 2026-07-20 — Pref get/set need a device; ISO/normalize covered here.
 */
public class RadioSettingsTest {

  @Test
  public void isoToFmRegion_mapsCommonCountries() {
    assertEquals("US", RadioSettings.isoToFmRegion("us"));
    assertEquals("JP", RadioSettings.isoToFmRegion("jp"));
    assertEquals("EU", RadioSettings.isoToFmRegion("de"));
    assertEquals("RU", RadioSettings.isoToFmRegion("ru"));
    assertEquals("AU", RadioSettings.isoToFmRegion("nz"));
    assertEquals("KR", RadioSettings.isoToFmRegion("kr"));
  }

  @Test
  public void normalizeCountry_uppercasesAndDefaults() {
    assertEquals("US", RadioSettings.normalizeCountry(null));
    assertEquals("US", RadioSettings.normalizeCountry(""));
    assertEquals("DE", RadioSettings.normalizeCountry(" de "));
  }

  @Test
  public void normalizeRegion_uppercasesAndDefaults() {
    assertEquals("US", RadioSettings.normalizeRegion(null));
    assertEquals("EU", RadioSettings.normalizeRegion("eu"));
  }
}
