package com.solar.launcher.radio.net;

import com.solar.launcher.AppVersion;
import com.solar.launcher.BuildConfig;
import com.solar.launcher.net.TlsHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Radio Browser directory — https://api.radio-browser.info
 * 2026-07-20: live mirror discovery + brief cache; de1→nl1 hard fallback if discovery fails.
 * Reversal: drop cache / searchByName / bitrate·codec; restore static BASES-only get().
 */
public final class RadioBrowserClient {
  // 2026-07-20: last-resort mirrors when all.api /json/servers is down — de1 then nl1.
  private static final String[] HARD_FALLBACK_BASES = {
    "https://de1.api.radio-browser.info/json",
    "https://nl1.api.radio-browser.info/json"
  };
  // 2026-07-20: DNS round-robin host that lists live API mirrors as JSON.
  private static final String MIRROR_DISCOVERY_URL =
      "https://all.api.radio-browser.info/json/servers";
  // 2026-07-20: keep mirror list warm ~10 min so we do not hammer discovery each click.
  private static final long MIRROR_CACHE_MS = 10L * 60L * 1000L;

  private static final Object MIRROR_LOCK = new Object();
  private static String[] cachedBases;
  private static long cachedBasesAtMs;

  private final android.content.Context appCtx;
  private final OkHttpClient client;

  public static final class Station {
    public final String stationuuid;
    public final String name;
    public final String urlResolved;
    public final String countrycode;
    public final String tags;
    public final String favicon;
    /** 2026-07-20: kbps from last check; 0 means unknown / missing. */
    public final int bitrate;
    /** 2026-07-20: stream codec label (MP3, AAC…); empty when absent. */
    public final String codec;

    /**
     * Six-arg ctor kept for favorites / older callers — bitrate and codec stay blank.
     * 2026-07-20: wraps full ctor so MediaSuiteHost need not change yet.
     */
    public Station(String stationuuid, String name, String urlResolved, String countrycode,
        String tags, String favicon) {
      this(stationuuid, name, urlResolved, countrycode, tags, favicon, 0, "");
    }

    /** Full station row including optional bitrate/codec from the directory. 2026-07-20 */
    public Station(String stationuuid, String name, String urlResolved, String countrycode,
        String tags, String favicon, int bitrate, String codec) {
      this.stationuuid = stationuuid;
      this.name = name;
      this.urlResolved = urlResolved;
      this.countrycode = countrycode;
      this.tags = tags;
      this.favicon = favicon;
      this.bitrate = bitrate < 0 ? 0 : bitrate;
      this.codec = codec == null ? "" : codec;
    }
  }

  public static final class Country {
    public final String name;
    public final String isoCode;
    public final int stationcount;

    public Country(String name, String isoCode, int stationcount) {
      this.name = name;
      this.isoCode = isoCode;
      this.stationcount = stationcount;
    }
  }

  public static final class State {
    public final String name;
    public final String country;
    public final int stationcount;

    public State(String name, String country, int stationcount) {
      this.name = name;
      this.country = country;
      this.stationcount = stationcount;
    }
  }

  public static final class Tag {
    public final String name;
    public final int stationcount;

    public Tag(String name, int stationcount) {
      this.name = name;
      this.stationcount = stationcount;
    }
  }

  public RadioBrowserClient(android.content.Context ctx) {
    appCtx = ctx.getApplicationContext();
    client = TlsHelper.client();
  }

  /** RadioBrowserClient for tests — no Context. */
  RadioBrowserClient(OkHttpClient client) {
    appCtx = null;
    this.client = client;
  }

  /** Lists countries with station counts from the directory. */
  public List<Country> listCountries() throws IOException {
    byte[] raw = get("/countries");
    return parseCountries(raw);
  }

  /** Lists states/regions inside one ISO country code. */
  public List<State> listStates(String countrycode) throws IOException {
    String cc = normalizeCode(countrycode);
    byte[] raw = get("/states/countrycode/" + enc(cc));
    return parseStates(raw);
  }

  /** Lists popular genre tags, capped by limit. */
  public List<Tag> listTags(int limit) throws IOException {
    if (limit < 1) limit = 40;
    byte[] raw = get("/tags?limit=" + limit + "&order=stationcount&reverse=true");
    return parseTags(raw);
  }

  /** Browse stations by country / state / tag with paging. */
  public List<Station> searchStations(String countrycode, String state, String tag, int limit,
      int offset) throws IOException {
    if (limit < 1) limit = 40;
    if (offset < 0) offset = 0;
    StringBuilder path = new StringBuilder("/stations/search?hidebroken=true&order=clickcount&reverse=true");
    path.append("&limit=").append(limit).append("&offset=").append(offset);
    if (countrycode != null && !countrycode.trim().isEmpty()) {
      path.append("&countrycode=").append(enc(normalizeCode(countrycode)));
    }
    if (state != null && !state.trim().isEmpty()) {
      path.append("&state=").append(enc(state.trim()));
    }
    if (tag != null && !tag.trim().isEmpty()) {
      path.append("&tag=").append(enc(tag.trim()));
    }
    byte[] raw = get(path.toString());
    return parseStations(raw);
  }

  /**
   * Name search with optional country filter and paging — advanced /stations/search.
   * 2026-07-20: blank name → empty list (no network). Reversal: remove method; UI toast-only.
   */
  public List<Station> searchByName(String name, String countrycode, int limit, int offset)
      throws IOException {
    if (name == null || name.trim().isEmpty()) {
      return new ArrayList<Station>();
    }
    if (limit < 1) limit = 40;
    if (offset < 0) offset = 0;
    byte[] raw = get(buildSearchByNamePath(name.trim(), countrycode, limit, offset));
    return parseStations(raw);
  }

  /** Builds the search URL path for name (+ optional country). Package-visible for tests. 2026-07-20 */
  static String buildSearchByNamePath(String name, String countrycode, int limit, int offset)
      throws IOException {
    StringBuilder path =
        new StringBuilder("/stations/search?hidebroken=true&order=clickcount&reverse=true");
    path.append("&limit=").append(limit).append("&offset=").append(offset);
    path.append("&name=").append(enc(name));
    if (countrycode != null && !countrycode.trim().isEmpty()) {
      path.append("&countrycode=").append(enc(normalizeCode(countrycode)));
    }
    return path.toString();
  }

  /** Tells Radio Browser a station was played (popularity / health). */
  public void reportClick(String stationuuid) throws IOException {
    if (stationuuid == null || stationuuid.trim().isEmpty()) return;
    get("/url/" + enc(stationuuid.trim()));
  }

  static List<Country> parseCountries(byte[] raw) throws IOException {
    List<Country> out = new ArrayList<Country>();
    try {
      JSONArray arr = new JSONArray(new String(raw, "UTF-8"));
      for (int i = 0; i < arr.length(); i++) {
        JSONObject o = arr.optJSONObject(i);
        if (o == null) continue;
        out.add(
            new Country(o.optString("name", ""), o.optString("iso_3166_1_2", ""),
                o.optInt("stationcount", 0)));
      }
    } catch (Exception e) {
      throw new IOException("countries parse failed", e);
    }
    return out;
  }

  static List<State> parseStates(byte[] raw) throws IOException {
    List<State> out = new ArrayList<State>();
    try {
      JSONArray arr = new JSONArray(new String(raw, "UTF-8"));
      for (int i = 0; i < arr.length(); i++) {
        JSONObject o = arr.optJSONObject(i);
        if (o == null) continue;
        out.add(
            new State(o.optString("name", ""), o.optString("country", ""),
                o.optInt("stationcount", 0)));
      }
    } catch (Exception e) {
      throw new IOException("states parse failed", e);
    }
    return out;
  }

  static List<Tag> parseTags(byte[] raw) throws IOException {
    List<Tag> out = new ArrayList<Tag>();
    try {
      JSONArray arr = new JSONArray(new String(raw, "UTF-8"));
      for (int i = 0; i < arr.length(); i++) {
        JSONObject o = arr.optJSONObject(i);
        if (o == null) continue;
        out.add(new Tag(o.optString("name", ""), o.optInt("stationcount", 0)));
      }
    } catch (Exception e) {
      throw new IOException("tags parse failed", e);
    }
    return out;
  }

  static List<Station> parseStations(byte[] raw) throws IOException {
    List<Station> out = new ArrayList<Station>();
    try {
      JSONArray arr = new JSONArray(new String(raw, "UTF-8"));
      for (int i = 0; i < arr.length(); i++) {
        Station s = parseStation(arr.optJSONObject(i));
        if (s != null) out.add(s);
      }
    } catch (Exception e) {
      throw new IOException("stations parse failed", e);
    }
    return out;
  }

  /**
   * Turns one JSON station object into a Station, or null if required fields missing.
   * 2026-07-20: bitrate/codec fail-open (0 / "") so bad types never drop a playable row.
   */
  static Station parseStation(JSONObject o) {
    if (o == null) return null;
    String uuid = o.optString("stationuuid", "").trim();
    String name = o.optString("name", "").trim();
    String url = o.optString("url_resolved", "").trim();
    if (url.isEmpty()) url = o.optString("url", "").trim();
    if (uuid.isEmpty() || name.isEmpty() || url.isEmpty()) return null;
    int bitrate = 0;
    try {
      if (o.has("bitrate") && !o.isNull("bitrate")) {
        bitrate = o.optInt("bitrate", 0);
        if (bitrate < 0) bitrate = 0;
      }
    } catch (Exception ignored) {
      bitrate = 0;
    }
    String codec = "";
    try {
      // JSON null must not become the literal "null" string
      if (o.has("codec") && !o.isNull("codec")) {
        codec = o.optString("codec", "").trim();
      }
    } catch (Exception ignored) {
      codec = "";
    }
    return new Station(uuid, name, url, o.optString("countrycode", "").trim(),
        o.optString("tags", "").trim(), o.optString("favicon", "").trim(), bitrate, codec);
  }

  /**
   * Parses /json/servers into https://{name}/json bases (unique hostnames, order kept).
   * 2026-07-20: empty / junk → empty list so caller can fall back to de1/nl1.
   */
  static List<String> parseServerBases(byte[] raw) {
    List<String> out = new ArrayList<String>();
    if (raw == null || raw.length == 0) return out;
    try {
      JSONArray arr = new JSONArray(new String(raw, "UTF-8"));
      Set<String> seen = new LinkedHashSet<String>();
      for (int i = 0; i < arr.length(); i++) {
        JSONObject o = arr.optJSONObject(i);
        if (o == null) continue;
        String host = o.optString("name", "").trim();
        if (host.isEmpty()) continue;
        // strip accidental scheme / path if a mirror ever returns a full URL
        if (host.startsWith("https://")) host = host.substring(8);
        else if (host.startsWith("http://")) host = host.substring(7);
        int slash = host.indexOf('/');
        if (slash >= 0) host = host.substring(0, slash);
        if (host.isEmpty() || !seen.add(host)) continue;
        out.add("https://" + host + "/json");
      }
    } catch (Exception ignored) {
      // fail-open: discovery parse miss → caller uses hard fallbacks
    }
    return out;
  }

  /** Clears mirror cache so unit tests start clean. 2026-07-20 */
  static void clearMirrorCacheForTests() {
    synchronized (MIRROR_LOCK) {
      cachedBases = null;
      cachedBasesAtMs = 0L;
    }
  }

  /**
   * Returns live mirror bases (cached briefly), always ending with de1→nl1 if missing.
   * 2026-07-20: discovery via all.api; on miss return hard fallbacks only.
   */
  String[] resolveBases() {
    long now = System.currentTimeMillis();
    synchronized (MIRROR_LOCK) {
      if (cachedBases != null && (now - cachedBasesAtMs) < MIRROR_CACHE_MS) {
        return cachedBases;
      }
    }
    String[] fresh = discoverBases();
    synchronized (MIRROR_LOCK) {
      cachedBases = fresh;
      cachedBasesAtMs = now;
      return cachedBases;
    }
  }

  /** Hits all.api (then de1/nl1 /servers) and merges with hard fallbacks. 2026-07-20 */
  private String[] discoverBases() {
    List<String> discovered = new ArrayList<String>();
    try {
      byte[] raw = getAbsolute(MIRROR_DISCOVERY_URL);
      discovered.addAll(parseServerBases(raw));
    } catch (IOException ignored) {
      // try hard mirrors' /servers next
    }
    if (discovered.isEmpty()) {
      for (String base : HARD_FALLBACK_BASES) {
        try {
          byte[] raw = getAbsolute(base + "/servers");
          discovered.addAll(parseServerBases(raw));
          if (!discovered.isEmpty()) break;
        } catch (IOException ignored) {
          // keep trying
        }
      }
    }
    LinkedHashSet<String> ordered = new LinkedHashSet<String>();
    ordered.addAll(discovered);
    for (String fb : HARD_FALLBACK_BASES) {
      ordered.add(fb);
    }
    if (ordered.isEmpty()) {
      return HARD_FALLBACK_BASES.clone();
    }
    return ordered.toArray(new String[ordered.size()]);
  }

  /** GET against already-resolved bases (discovery + hard fallback). */
  private byte[] get(String path) throws IOException {
    TlsHelper.ensureSecurityProvider();
    IOException last = null;
    String[] bases = resolveBases();
    for (String base : bases) {
      try {
        return getAbsolute(base + path);
      } catch (IOException e) {
        last = e;
      }
    }
    throw last != null ? last : new IOException("All Radio Browser mirrors failed");
  }

  /** One absolute HTTPS GET with Solar User-Agent — used by API paths and mirror discovery. */
  private byte[] getAbsolute(String url) throws IOException {
    TlsHelper.ensureSecurityProvider();
    Request req =
        new Request.Builder()
            .url(url)
            .header("User-Agent", userAgent())
            .header("Accept", "application/json")
            .build();
    Response resp = client.newCall(req).execute();
    try {
      if (!resp.isSuccessful() || resp.body() == null) {
        throw new IOException("HTTP " + resp.code() + " for " + url);
      }
      return resp.body().bytes();
    } finally {
      if (resp.body() != null) resp.body().close();
    }
  }

  private String userAgent() {
    String ver =
        appCtx != null
            ? AppVersion.installedVersionName(appCtx)
            : (BuildConfig.VERSION_NAME != null ? BuildConfig.VERSION_NAME : "dev");
    return "Solar/" + ver;
  }

  private static String enc(String s) throws IOException {
    return URLEncoder.encode(s, "UTF-8");
  }

  private static String normalizeCode(String code) {
    return code == null ? "" : code.trim().toUpperCase(Locale.US);
  }
}
