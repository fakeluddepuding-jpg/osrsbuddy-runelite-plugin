package com.osrsbuddy;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Thin HTTP client for the two OSRSBuddy edge functions:
 *   POST /plugin-claim   (exchange pairing code for an API token)
 *   POST /plugin-sync    (push character snapshot)
 */
@Slf4j
@Singleton
public class ApiClient
{
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http = new OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();

    @Inject private Gson gson;
    @Inject private OsrsBuddyConfig config;

    public interface Callback<T> { void onResult(T result, Throwable error); }

    /** Exchange pairing code for a long-lived API token. */
    public void claim(String code, Callback<String> cb)
    {
        JsonObject body = new JsonObject();
        body.addProperty("code", code.trim());
        body.addProperty("label", "RuneLite — " + System.getProperty("os.name"));

        Request req = new Request.Builder()
            .url(config.serverUrl() + "/plugin-claim")
            .post(RequestBody.create(JSON, gson.toJson(body)))
            .build();

        http.newCall(req).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(Call call, IOException e) { cb.onResult(null, e); }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody rb = response.body()) {
                    String s = rb != null ? rb.string() : "";
                    if (!response.isSuccessful()) {
                        cb.onResult(null, new IOException("Pairing failed (" + response.code() + "): " + s));
                        return;
                    }
                    JsonObject obj = gson.fromJson(s, JsonObject.class);
                    String token = obj.has("token") ? obj.get("token").getAsString() : null;
                    if (token == null) { cb.onResult(null, new IOException("No token in response")); return; }
                    cb.onResult(token, null);
                }
            }
        });
    }

    /** Push a partial character snapshot. Silently no-ops if not paired. */
    public void sync(Map<String, Object> payload, Callback<Void> cb)
    {
        String token = config.apiToken();
        if (token == null || token.isEmpty()) {
            if (cb != null) cb.onResult(null, new IllegalStateException("Not paired"));
            return;
        }

        Request req = new Request.Builder()
            .url(config.serverUrl() + "/plugin-sync")
            .header("Authorization", "Bearer " + token)
            .post(RequestBody.create(JSON, gson.toJson(payload)))
            .build();

        http.newCall(req).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(Call call, IOException e) {
                log.warn("OSRSBuddy sync failed", e);
                if (cb != null) cb.onResult(null, e);
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody rb = response.body()) {
                    if (!response.isSuccessful()) {
                        String s = rb != null ? rb.string() : "";
                        log.warn("OSRSBuddy sync HTTP {}: {}", response.code(), s);
                        if (cb != null) cb.onResult(null, new IOException("HTTP " + response.code()));
                        return;
                    }
                    if (cb != null) cb.onResult(null, null);
                }
            }
        });
    }
}
