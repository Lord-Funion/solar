package com.solar.launcher.diag;

import com.solar.launcher.BuildConfig;
import com.solar.launcher.DeviceFeatures;
import com.solar.launcher.net.SolarHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * 2026-07-16 — HTTPS client for the solar-diag Cloudflare Worker (TLS 1.2 via SolarHttp).
 * Posts JSON to /v1/report with X-Solar-Diag-Token. GitHub credentials stay on Cloudflare.
 */
public final class SolarDiagClient {
    public static final String AUTH_HEADER = "X-Solar-Diag-Token";
    private static final String UA = DeviceFeatures.reachClientName() + "/SolarDiag";

    public static final class Result {
        public final boolean ok;
        public final int issueNumber;
        public final String htmlUrl;
        public final String error;

        Result(boolean ok, int issueNumber, String htmlUrl, String error) {
            this.ok = ok;
            this.issueNumber = issueNumber;
            this.htmlUrl = htmlUrl != null ? htmlUrl : "";
            this.error = error != null ? error : "";
        }

        public static Result success(int number, String url) {
            return new Result(true, number, url, "");
        }

        public static Result fail(String err) {
            return new Result(false, 0, "", err != null ? err : "unknown");
        }
    }

    public static final class FilePart {
        public final String name;
        public final String text;

        public FilePart(String name, String text) {
            this.name = name != null ? name : "file.txt";
            this.text = text != null ? text : "";
        }
    }

    private SolarDiagClient() {}

    public static boolean isConfigured() {
        String url = baseUrl();
        String token = ingestToken();
        return url != null && !url.isEmpty() && token != null && !token.isEmpty();
    }

    /**
     * Blocking POST — call from a worker thread only.
     * @param soulseekUsername for remote_pull and user_message only; null otherwise
     * @param userMessage optional quoted support message (user_report)
     */
    public static Result submit(String type, String feature, String trigger,
            String soulseekUsername, JSONObject device, String summary,
            String titleHint, List<FilePart> files) {
        return submit(type, feature, trigger, soulseekUsername, device, summary, titleHint,
                null, files);
    }

    public static Result submit(String type, String feature, String trigger,
            String soulseekUsername, JSONObject device, String summary,
            String titleHint, String userMessage, List<FilePart> files) {
        if (!isConfigured()) return Result.fail("not_configured");
        try {
            JSONObject body = new JSONObject();
            body.put("type", type != null ? type : "other");
            if (feature != null && !feature.isEmpty()) body.put("feature", feature);
            body.put("trigger", trigger != null ? trigger : "routine");
            // Username when developer remote-pulls or the user reports via Solar Development.
            if (("remote_pull".equals(trigger) || "user_message".equals(trigger))
                    && soulseekUsername != null && !soulseekUsername.trim().isEmpty()) {
                body.put("soulseek_username", soulseekUsername.trim());
            }
            if (device != null) body.put("device", device);
            if (summary != null && !summary.isEmpty()) body.put("summary", summary);
            if (titleHint != null && !titleHint.isEmpty()) body.put("title", titleHint);
            if (userMessage != null && !userMessage.trim().isEmpty()) {
                body.put("user_message", userMessage.trim());
            }
            JSONArray arr = new JSONArray();
            if (files != null) {
                for (FilePart f : files) {
                    if (f == null) continue;
                    JSONObject fo = new JSONObject();
                    fo.put("name", f.name);
                    fo.put("text", f.text != null ? f.text : "");
                    arr.put(fo);
                }
            }
            body.put("files", arr);
            String raw = new String(
                    SolarHttp.postJsonAuth(reportUrl(), body.toString(), UA, AUTH_HEADER, ingestToken()),
                    "UTF-8");
            JSONObject root = new JSONObject(raw);
            if (!root.optBoolean("ok", false)) {
                return Result.fail(root.optString("error", "server_rejected"));
            }
            return Result.success(root.optInt("issue_number", 0), root.optString("html_url", ""));
        } catch (Throwable e) {
            // 2026-07-20 — OOM/Error on huge payloads must not escape as uncaught (kills Solar).
            // Was: catch (Exception) only. Reversal: catch Exception again.
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            // ponytail: hide cloudflare worker URL in error messages
            int idx = msg.indexOf(" for http");
            if (idx > 0) {
                msg = msg.substring(0, idx);
            }
            return Result.fail(msg);
        }
    }

    static String baseUrl() {
        String url = BuildConfig.SOLAR_DIAG_URL;
        if (url == null) return "";
        url = url.trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    static String reportUrl() {
        return baseUrl() + "/v1/report";
    }

    static String ingestToken() {
        String t = BuildConfig.SOLAR_DIAG_TOKEN;
        return t != null ? t.trim() : "";
    }
}
