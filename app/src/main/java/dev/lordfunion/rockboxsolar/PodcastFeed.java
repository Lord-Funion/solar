package dev.lordfunion.rockboxsolar;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class PodcastFeed {
    static final class Episode {
        final String title;
        final String url;
        final String mime;

        Episode(String title, String url, String mime) {
            this.title = title;
            this.url = url;
            this.mime = mime;
        }
    }

    static List<Episode> fetch(OkHttpClient client, String feedUrl) throws Exception {
        Response response = client.newCall(new Request.Builder().url(feedUrl)
                .header("User-Agent", "RockboxSolar/0.1 podcast client").build()).execute();
        if (!response.isSuccessful()) throw new IllegalStateException("HTTP " + response.code());
        ResponseBody body = response.body();
        if (body == null) throw new IllegalStateException("Empty feed");
        InputStream stream = body.byteStream();
        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(stream, null);
        ArrayList<Episode> episodes = new ArrayList<Episode>();
        boolean inItem = false;
        String title = null;
        String enclosure = null;
        String mime = null;
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT && episodes.size() < 100) {
            if (event == XmlPullParser.START_TAG) {
                String tag = parser.getName();
                if ("item".equalsIgnoreCase(tag) || "entry".equalsIgnoreCase(tag)) {
                    inItem = true;
                    title = null;
                    enclosure = null;
                    mime = null;
                } else if (inItem && "title".equalsIgnoreCase(tag)) {
                    title = parser.nextText();
                } else if (inItem && "enclosure".equalsIgnoreCase(tag)) {
                    enclosure = parser.getAttributeValue(null, "url");
                    mime = parser.getAttributeValue(null, "type");
                } else if (inItem && "link".equalsIgnoreCase(tag)) {
                    String rel = parser.getAttributeValue(null, "rel");
                    String href = parser.getAttributeValue(null, "href");
                    String type = parser.getAttributeValue(null, "type");
                    if (("enclosure".equalsIgnoreCase(rel) || (type != null && type.startsWith("audio/"))) && href != null) {
                        enclosure = href;
                        mime = type;
                    }
                }
            } else if (event == XmlPullParser.END_TAG) {
                String tag = parser.getName();
                if (("item".equalsIgnoreCase(tag) || "entry".equalsIgnoreCase(tag)) && inItem) {
                    if (enclosure != null && enclosure.length() > 0) {
                        episodes.add(new Episode(title == null || title.trim().length() == 0 ? "Untitled episode" : title.trim(), enclosure, mime));
                    }
                    inItem = false;
                }
            }
        }
        stream.close();
        response.close();
        return episodes;
    }
}
