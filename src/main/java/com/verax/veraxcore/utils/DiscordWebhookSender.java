package com.verax.veraxcore.utils;

import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class DiscordWebhookSender {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
            .build();

    /**
     * Sends the JSON payload synchronously to the Discord webhook URL using the shared connection pool.
     */
    public static boolean sendPayload(String webhookUrl, String jsonPayload, Logger logger) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty() || webhookUrl.contains("YOUR_")) {
            return false;
        }

        try {
            RequestBody body = RequestBody.create(jsonPayload, JSON);
            Request request = new Request.Builder()
                    .url(webhookUrl)
                    .header("User-Agent", "VeraxCore-DiscordHook")
                    .post(body)
                    .build();

            try (Response response = CLIENT.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    return true;
                } else {
                    if (logger != null) {
                        logger.warning("Discord Webhook HTTP error: " + response.code() + " (" + response.message() + ")");
                    }
                    return false;
                }
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Discord Webhook network error: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Sends the JSON payload asynchronously using OkHttp's internal thread pool and connection reuse.
     */
    public static void sendPayloadAsync(String webhookUrl, String jsonPayload, Logger logger) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty() || webhookUrl.contains("YOUR_")) {
            return;
        }

        try {
            RequestBody body = RequestBody.create(jsonPayload, JSON);
            Request request = new Request.Builder()
                    .url(webhookUrl)
                    .header("User-Agent", "VeraxCore-DiscordHook")
                    .post(body)
                    .build();

            CLIENT.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    if (logger != null) {
                        logger.warning("Discord Webhook async error: " + e.getMessage());
                    }
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try (Response res = response) {
                        if (!res.isSuccessful() && logger != null) {
                            logger.warning("Discord Webhook async HTTP error: " + res.code());
                        }
                    }
                }
            });
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Discord Webhook enqueue error: " + e.getMessage());
            }
        }
    }

    /**
     * Safely escapes any string for valid JSON format (quotes, backslashes, tabs, newlines, control characters).
     */
    public static String escapeJson(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < ' ') {
                        String hex = Integer.toHexString(c);
                        sb.append("\\u");
                        for (int k = 0; k < 4 - hex.length(); k++) {
                            sb.append('0');
                        }
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * Safely closes client thread pools and connections on plugin disable.
     */
    public static void close() {
        try {
            CLIENT.dispatcher().executorService().shutdown();
            CLIENT.connectionPool().evictAll();
        } catch (Throwable ignored) {}
    }
}
