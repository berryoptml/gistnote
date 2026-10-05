package dev.gistnote.app.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class GitHubApi {
    private static final String API_ROOT = "https://api.github.com";
    private static final String API_VERSION = "2026-03-10";
    private final String token;

    public GitHubApi(String token) {
        this.token = token == null ? "" : token.trim();
    }

    public String getLogin() throws Exception {
        JSONObject user = new JSONObject(request("GET", API_ROOT + "/user", null));
        return user.getString("login");
    }

    public List<GistNote> listGists() throws Exception {
        List<GistNote> notes = new ArrayList<>();
        int page = 1;
        while (true) {
            JSONArray result = new JSONArray(request("GET",
                    API_ROOT + "/gists?per_page=100&page=" + page, null));
            for (int i = 0; i < result.length(); i++) {
                try {
                    notes.add(GistNote.fromJson(result.getJSONObject(i), null));
                } catch (Exception ignored) {
                    // Empty or unusual gists should not make the whole list unusable.
                }
            }
            if (result.length() < 100) break;
            page++;
        }
        return notes;
    }

    public List<GistRevision> listRevisions(String gistId) throws Exception {
        JSONArray result = new JSONArray(request("GET",
                API_ROOT + "/gists/" + gistId + "/commits?per_page=100", null));
        List<GistRevision> revisions = new ArrayList<>();
        for (int i = 0; i < result.length(); i++) {
            JSONObject item = result.getJSONObject(i);
            JSONObject change = item.optJSONObject("change_status");
            revisions.add(new GistRevision(
                    item.getString("version"),
                    item.optString("committed_at", ""),
                    change == null ? 0 : change.optInt("additions", 0),
                    change == null ? 0 : change.optInt("deletions", 0)));
        }
        return revisions;
    }

    public GistNote getRevision(String gistId, String revision, String preferredFilename)
            throws Exception {
        JSONObject response = new JSONObject(request("GET",
                API_ROOT + "/gists/" + gistId + "/" + revision, null));
        return resolveFullContent(GistNote.fromJson(response, preferredFilename));
    }

    public GistNote createGist(String description, String filename, String content) throws Exception {
        JSONObject file = new JSONObject().put("content", content);
        JSONObject body = new JSONObject()
                .put("description", description)
                .put("public", false)
                .put("files", new JSONObject().put(filename, file));
        JSONObject response = new JSONObject(request("POST", API_ROOT + "/gists", body.toString()));
        return resolveFullContent(GistNote.fromJson(response, filename));
    }

    public GistNote getGist(String gistId, String preferredFilename) throws Exception {
        JSONObject response = new JSONObject(request("GET", API_ROOT + "/gists/" + gistId, null));
        return resolveFullContent(GistNote.fromJson(response, preferredFilename));
    }

    public GistNote updateGist(String gistId, String filename, String content) throws Exception {
        JSONObject file = new JSONObject().put("content", content);
        JSONObject body = new JSONObject().put("files", new JSONObject().put(filename, file));
        JSONObject response = new JSONObject(request(
                "PATCH", API_ROOT + "/gists/" + gistId, body.toString()));
        return resolveFullContent(GistNote.fromJson(response, filename));
    }

    private GistNote resolveFullContent(GistNote note) throws Exception {
        if (!note.truncated || note.rawUrl.isEmpty()) return note;
        return note.withContent(request("GET", note.rawUrl, null));
    }

    private String request(String method, String address, String body) throws Exception {
        if (token.isEmpty()) throw new ApiException(401, "No GitHub token configured");
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(20_000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("X-GitHub-Api-Version", API_VERSION);
        connection.setRequestProperty("User-Agent", "GistNote-Android/0.1");
        if (body != null) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        String response = readFully(stream);
        connection.disconnect();
        if (status < 200 || status >= 300) {
            String message = response;
            try {
                message = new JSONObject(response).optString("message", response);
            } catch (Exception ignored) { }
            throw new ApiException(status, message);
        }
        return response;
    }

    private static String readFully(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int count;
            while ((count = reader.read(buffer)) >= 0) result.append(buffer, 0, count);
        }
        return result.toString();
    }

    public static final class ApiException extends IOException {
        public final int statusCode;

        public ApiException(int statusCode, String message) {
            super("GitHub " + statusCode + ": " + message);
            this.statusCode = statusCode;
        }
    }
}
