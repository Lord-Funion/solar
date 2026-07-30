package dev.lordfunion.rockboxsolar;

import org.json.JSONException;
import org.json.JSONObject;

final class SshProfile {
    static final String AUTH_PASSWORD = "password";
    static final String AUTH_KEY = "key";

    String id;
    String name;
    String host;
    int port;
    String user;
    String authMode;
    String keyPath;

    SshProfile(String id, String name, String host, int port, String user,
               String authMode, String keyPath) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.port = port;
        this.user = user;
        this.authMode = authMode;
        this.keyPath = keyPath;
    }

    JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("name", name);
        object.put("host", host);
        object.put("port", port);
        object.put("user", user);
        object.put("authMode", authMode);
        object.put("keyPath", keyPath);
        return object;
    }

    static SshProfile fromJson(JSONObject object) {
        String id = object.optString("id", Long.toString(System.currentTimeMillis()));
        String host = object.optString("host", "").trim();
        String name = object.optString("name", host).trim();
        int port = object.optInt("port", 22);
        String user = object.optString("user", "").trim();
        String auth = object.optString("authMode", AUTH_PASSWORD);
        String keyPath = object.optString("keyPath", "").trim();
        return new SshProfile(id, name, host, port, user, auth, keyPath);
    }

    String displayName() {
        String label = name == null || name.trim().length() == 0 ? host : name.trim();
        return label + " — " + user + "@" + host + (port == 22 ? "" : ":" + port);
    }

    boolean usesKey() {
        return AUTH_KEY.equals(authMode);
    }
}
