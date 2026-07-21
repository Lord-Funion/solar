package com.solar.launcher;

import com.solar.launcher.net.SolarHttp;
import com.solar.launcher.net.TlsHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Public donor roll from GitHub Pages — names only, no payment details.
 * Fetched when the user opens Settings → Donations → Our Donors while online.
 */
public final class SolarDonatorsClient {

    /** Canonical catalog URL on thesolarproject.github.io. */
    public static final String DEFAULT_DONATORS_URL =
            "https://thesolarproject.github.io/solar/donators.xml";

    private static final Pattern DONATOR_SELF_CLOSING =
            Pattern.compile("<donator\\s+([^>/]+)/>", Pattern.CASE_INSENSITIVE);
    private static final Pattern DONATOR_BLOCK =
            Pattern.compile("<donator(?:\\s+([^>]*))?>([^<]*)</donator>", Pattern.CASE_INSENSITIVE);

    /** One thanked donor row — display name plus optional short note from XML. */
    public static final class Donator {
        public final String name;
        public final String note;

        public Donator(String name, String note) {
            this.name = name == null ? "" : name.trim();
            this.note = note == null ? "" : note.trim();
        }

        /** Wheel list label — note appended when present. */
        public String listLabel() {
            if (note.isEmpty()) return name;
            return name + ": " + note;
        }
    }

    private SolarDonatorsClient() {}

    /** Pull donators.xml over HTTPS (caller should gate on ConnectivityHelper.isOnline). 2026-07-05 */
    public static List<Donator> fetchDonators(String donatorsUrl) throws IOException {
        if (donatorsUrl == null || donatorsUrl.trim().isEmpty()) {
            donatorsUrl = DEFAULT_DONATORS_URL;
        }
        TlsHelper.ensureSecurityProvider();
        okhttp3.Request req = new okhttp3.Request.Builder()
                .url(donatorsUrl.trim())
                .header("User-Agent", "SolarLauncher/1.0")
                .header("Accept", "application/xml,text/xml,*/*")
                .build();
        okhttp3.OkHttpClient client = SolarHttp.longReadClient();
        okhttp3.Response resp = client.newCall(req).execute();
        try {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new IOException("donators.xml HTTP " + resp.code());
            }
            String xml = new String(resp.body().bytes(), "UTF-8");
            return parseDonatorsXml(xml);
        } finally {
            if (resp.body() != null) resp.body().close();
        }
    }

    /** Parse {@code <donator name="…" note="…"/>} or {@code <donator>name</donator>} blocks. 2026-07-05 */
    static List<Donator> parseDonatorsXml(String xml) {
        List<Donator> out = new ArrayList<Donator>();
        if (xml == null || xml.trim().isEmpty()) return out;

        Matcher selfClosing = DONATOR_SELF_CLOSING.matcher(xml);
        while (selfClosing.find()) {
            Donator row = donatorFromAttrs(parseAttrs(selfClosing.group(1)), "");
            if (row != null) out.add(row);
        }

        Matcher blocks = DONATOR_BLOCK.matcher(xml);
        while (blocks.find()) {
            String attrsRaw = blocks.group(1);
            String body = blocks.group(2) == null ? "" : blocks.group(2).trim();
            Map<String, String> attrs = attrsRaw != null ? parseAttrs(attrsRaw) : new LinkedHashMap<String, String>();
            Donator row = donatorFromAttrs(attrs, body);
            if (row != null) out.add(row);
        }
        return out;
    }

    private static Donator donatorFromAttrs(Map<String, String> attrs, String bodyText) {
        String name = attrs.containsKey("name") ? attrs.get("name") : bodyText;
        if (name == null || name.trim().isEmpty()) return null;
        String note = attrs.containsKey("note") ? attrs.get("note")
                : (attrs.containsKey("message") ? attrs.get("message") : "");
        return new Donator(name, note);
    }

    private static Map<String, String> parseAttrs(String raw) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (raw == null) return out;
        Matcher m = Pattern.compile("(\\w+)=\"([^\"]*)\"").matcher(raw);
        while (m.find()) out.put(m.group(1), m.group(2));
        return out;
    }

    /** Tiny parse self-check — fails fast when XML shape regresses. */
    static void selfCheck() {
        String sample = "<?xml version=\"1.0\"?><donators>"
                + "<donator name=\"Ada\" note=\"Monthly\"/>"
                + "<donator>Bob</donator>"
                + "</donators>";
        List<Donator> parsed = parseDonatorsXml(sample);
        if (parsed.size() != 2) {
            throw new AssertionError("expected 2 donors, got " + parsed.size());
        }
        if (!"Ada".equals(parsed.get(0).name) || !"Monthly".equals(parsed.get(0).note)) {
            throw new AssertionError("attr donor mismatch");
        }
        if (!"Bob".equals(parsed.get(1).name)) {
            throw new AssertionError("body donor mismatch");
        }
    }
}
