package com.solar.launcher.radio.net;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RadioBrowserClientTest {

  private static final String STATIONS_JSON =
      "[{\"stationuuid\":\"abc-123\",\"name\":\"Test FM\",\"url_resolved\":\"http://stream/test\","
          + "\"countrycode\":\"US\",\"tags\":\"rock,pop\",\"favicon\":\"http://icon.png\","
          + "\"bitrate\":128,\"codec\":\"MP3\"},"
          + "{\"stationuuid\":\"\",\"name\":\"skip\",\"url\":\"http://x\"}]";

  private static final String COUNTRIES_JSON =
      "[{\"name\":\"United States\",\"iso_3166_1_2\":\"US\",\"stationcount\":12000}]";

  private static final String TAGS_JSON =
      "[{\"name\":\"jazz\",\"stationcount\":500},{\"name\":\"news\",\"stationcount\":300}]";

  private static final String SERVERS_JSON =
      "[{\"ip\":\"95.179.139.106\",\"name\":\"nl1.api.radio-browser.info\"},"
          + "{\"ip\":\"188.68.62.16\",\"name\":\"de1.api.radio-browser.info\"},"
          + "{\"ip\":\"2a03:4000:6:8077::1\",\"name\":\"de1.api.radio-browser.info\"}]";

  @Before
  public void resetMirrorCache() {
    RadioBrowserClient.clearMirrorCacheForTests();
  }

  @Test
  public void parseStations_skipsIncompleteRows() throws Exception {
    List<RadioBrowserClient.Station> list =
        RadioBrowserClient.parseStations(STATIONS_JSON.getBytes("UTF-8"));
    assertEquals(1, list.size());
    RadioBrowserClient.Station s = list.get(0);
    assertEquals("abc-123", s.stationuuid);
    assertEquals("Test FM", s.name);
    assertEquals("http://stream/test", s.urlResolved);
    assertEquals("US", s.countrycode);
    assertEquals("rock,pop", s.tags);
    assertEquals("http://icon.png", s.favicon);
    assertEquals(128, s.bitrate);
    assertEquals("MP3", s.codec);
  }

  @Test
  public void parseStation_fallsBackToUrlField() {
    org.json.JSONObject o = new org.json.JSONObject();
    try {
      o.put("stationuuid", "u1");
      o.put("name", "Fallback");
      o.put("url", "http://direct");
    } catch (Exception e) {
      throw new AssertionError(e);
    }
    RadioBrowserClient.Station s = RadioBrowserClient.parseStation(o);
    assertNotNull(s);
    assertEquals("http://direct", s.urlResolved);
    assertEquals(0, s.bitrate);
    assertEquals("", s.codec);
  }

  /** 2026-07-20: bad bitrate must not drop the station — fail-open to 0. */
  @Test
  public void parseStation_bitrateCodecFailOpen() throws Exception {
    org.json.JSONObject o = new org.json.JSONObject();
    o.put("stationuuid", "u2");
    o.put("name", "Weird Meta");
    o.put("url_resolved", "http://stream/ok");
    o.put("bitrate", "not-a-number");
    o.put("codec", org.json.JSONObject.NULL);
    RadioBrowserClient.Station s = RadioBrowserClient.parseStation(o);
    assertNotNull(s);
    assertEquals(0, s.bitrate);
    assertEquals("", s.codec);
  }

  @Test
  public void parseCountries() throws Exception {
    List<RadioBrowserClient.Country> list =
        RadioBrowserClient.parseCountries(COUNTRIES_JSON.getBytes("UTF-8"));
    assertEquals(1, list.size());
    assertEquals("US", list.get(0).isoCode);
    assertTrue(list.get(0).stationcount > 0);
  }

  @Test
  public void parseTags() throws Exception {
    List<RadioBrowserClient.Tag> list =
        RadioBrowserClient.parseTags(TAGS_JSON.getBytes("UTF-8"));
    assertEquals(2, list.size());
    assertEquals("jazz", list.get(0).name);
  }

  /** 2026-07-20: /json/servers → unique https://host/json bases, order preserved. */
  @Test
  public void parseServerBases_dedupesHosts() throws Exception {
    List<String> bases =
        RadioBrowserClient.parseServerBases(SERVERS_JSON.getBytes("UTF-8"));
    assertEquals(2, bases.size());
    assertEquals("https://nl1.api.radio-browser.info/json", bases.get(0));
    assertEquals("https://de1.api.radio-browser.info/json", bases.get(1));
  }

  @Test
  public void parseServerBases_junkReturnsEmpty() {
    List<String> bases = RadioBrowserClient.parseServerBases("not-json".getBytes());
    assertTrue(bases.isEmpty());
  }

  /** 2026-07-20: name search path encodes query + optional country. */
  @Test
  public void buildSearchByNamePath_includesNameAndCountry() throws Exception {
    String path = RadioBrowserClient.buildSearchByNamePath("BBC Radio", "gb", 40, 0);
    assertTrue(path.startsWith("/stations/search?"));
    assertTrue(path.contains("name=BBC+Radio") || path.contains("name=BBC%20Radio"));
    assertTrue(path.contains("countrycode=GB"));
    assertTrue(path.contains("limit=40"));
    assertTrue(path.contains("offset=0"));
    assertTrue(path.contains("hidebroken=true"));
  }

  @Test
  public void buildSearchByNamePath_omitsBlankCountry() throws Exception {
    String path = RadioBrowserClient.buildSearchByNamePath("jazz", null, 20, 40);
    assertFalse(path.contains("countrycode="));
    assertTrue(path.contains("limit=20"));
    assertTrue(path.contains("offset=40"));
  }

  /** 2026-07-20: blank name must not hit the network. */
  @Test
  public void searchByName_blankReturnsEmptyWithoutNetwork() throws Exception {
    RadioBrowserClient client = new RadioBrowserClient((okhttp3.OkHttpClient) null);
    assertTrue(client.searchByName("  ", "US", 40, 0).isEmpty());
    assertTrue(client.searchByName(null, null, 40, 0).isEmpty());
  }

  /** 2026-07-20: six-arg Station ctor still works for favorites play path. */
  @Test
  public void stationSixArgCtor_defaultsBitrateCodec() {
    RadioBrowserClient.Station s =
        new RadioBrowserClient.Station("id", "Name", "http://u", "US", "", "");
    assertEquals(0, s.bitrate);
    assertEquals("", s.codec);
  }
}
