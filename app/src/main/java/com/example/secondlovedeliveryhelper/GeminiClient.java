package com.example.secondlovedeliveryhelper;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiClient {

    private static final MediaType JSON
            = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)   // connection setup
            .readTimeout(120, TimeUnit.SECONDS)     // wait up to 2 minutes for response
            .writeTimeout(30, TimeUnit.SECONDS)     // uploading request body
            .callTimeout(150, TimeUnit.SECONDS)     // total end-to-end timeout
            .build();

    private final String apiKey;

    public GeminiClient(String apiKey) {
        this.apiKey = apiKey;
    }

    public List<OrderItem> parseOrders(String rawText, String model) throws IOException {

        String prompt =
                "You are a data extraction assistant. Convert this raw text into JSON.\n" +
                        "The JSON must be a list of objects, each having keys: Name, Phone, Address, Details, Amount.\n" +
                        "Rules:\n" +
                        "1. Output ONLY valid JSON.\n" +
                        "2. Phone must be 01XXXXXXXXX.\n\n" +
                        rawText;

        // --------- Gemini 2.5 correct JSON structure ----------
        JsonObject root = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject contentObj = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject textPart = new JsonObject();

        textPart.addProperty("text", prompt);
        parts.add(textPart);
        contentObj.add("parts", parts);
        contents.add(contentObj);
        root.add("contents", contents);
        // -------------------------------------------------------

        Log.d("GEMINI_REQUEST", root.toString());

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;

        RequestBody requestBody = RequestBody.create(root.toString(), JSON);

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        Response response = client.newCall(request).execute();
        String respStr = response.body().string();

        Log.d("GEMINI_RESPONSE", respStr);

        return parseGeminiResponse(respStr);
    }

    private List<OrderItem> parseGeminiResponse(String respStr) {
        JsonObject root = JsonParser.parseString(respStr).getAsJsonObject();
        JsonArray candidates = root.getAsJsonArray("candidates");

        if (candidates == null || candidates.size() == 0)
            throw new RuntimeException("No candidates from Gemini");

        String text = candidates.get(0)
                .getAsJsonObject()
                .getAsJsonObject("content")
                .getAsJsonArray("parts")
                .get(0)
                .getAsJsonObject()
                .get("text").getAsString();

        String jsonText = extractJsonFromText(text);

        JsonElement json = JsonParser.parseString(jsonText);

        if (!json.isJsonArray())
            throw new RuntimeException("Invalid JSON returned");

        List<OrderItem> list = new ArrayList<>();

        for (JsonElement el : json.getAsJsonArray()) {
            JsonObject o = el.getAsJsonObject();

            list.add(new OrderItem(
                    safe(o, "Name"),
                    safe(o, "Phone"),
                    safe(o, "Address"),
                    safe(o, "Details"),
                    safe(o, "Amount")
            ));
        }
        return list;
    }

    private String safe(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : "";
    }

    private String extractJsonFromText(String text) {

        Pattern p = Pattern.compile("```json(.*?)```", Pattern.DOTALL);
        Matcher m = p.matcher(text);

        if (m.find()) {
            return m.group(1).trim();
        }

        int s = text.indexOf("{");
        int e = text.lastIndexOf("}") + 1;

        if (s >= 0 && e >= 1)
            return text.substring(s, e);

        throw new RuntimeException("JSON not found in Gemini response");
    }
}
