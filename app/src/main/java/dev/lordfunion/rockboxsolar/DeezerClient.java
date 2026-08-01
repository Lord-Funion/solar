package dev.lordfunion.rockboxsolar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

final class DeezerClient {
    static final class Track {
        long id;
        String title;
        String artist;
        String album;
        String link;
        String preview;
        String cover;
        int duration;
        boolean explicit;

        String label() {
            return title + " — " + artist + (album.length() == 0 ? "" : "\n" + album)
                    + " • " + format(duration) + (preview.length() == 0 ? " • no preview" : " • preview");
        }

        private static String format(int seconds) {
            return String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60);
        }
    }

    private final OkHttpClient client;
    DeezerClient(OkHttpClient client) { this.client = client; }

    List<Track> search(String query) throws Exception {
        String url = "https://api.deezer.com/search?q=" + URLEncoder.encode(query, "UTF-8") + "&limit=50";
        Request request = new Request.Builder().url(url).header("User-Agent", "RockboxSolar/0.3").build();
        Response response = client.newCall(request).execute();
        try {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Deezer HTTP " + response.code());
            JSONObject root = new JSONObject(response.body().string());
            if (root.has("error")) throw new IOException(root.getJSONObject("error").optString("message", "Deezer error"));
            JSONArray data = root.optJSONArray("data");
            ArrayList<Track> tracks = new ArrayList<Track>();
            if (data == null) return tracks;
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                Track track = new Track();
                track.id = item.optLong("id");
                track.title = item.optString("title", "Unknown title");
                track.link = item.optString("link", "");
                track.preview = item.optString("preview", "");
                track.duration = item.optInt("duration", 0);
                track.explicit = item.optBoolean("explicit_lyrics", false);
                JSONObject artist = item.optJSONObject("artist");
                track.artist = artist == null ? "Unknown artist" : artist.optString("name", "Unknown artist");
                JSONObject album = item.optJSONObject("album");
                track.album = album == null ? "" : album.optString("title", "");
                track.cover = album == null ? "" : album.optString("cover_medium", album.optString("cover", ""));
                tracks.add(track);
            }
            return tracks;
        } finally { response.close(); }
    }
}
