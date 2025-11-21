package com.example.secondlovedeliveryhelper;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

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
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(150, TimeUnit.SECONDS)
            .build();

    private final String apiKey;

    public GeminiClient(String apiKey) {
        this.apiKey = apiKey;
    }

    public List<OrderItem> parseOrders(String rawText, String model) throws IOException {
        // Old Prompt
//        String prompt =
//                "Extract delivery orders from the following text and return them as a JSON Array.\n" +
//                        "Each item in the array must be an object with these exact keys:\n" +
//                        "- \"Name\" (string, customer name)\n" +
//                        "- \"Phone\" (string, formatted as 01XXXXXXXXX)\n" +
//                        "- \"Address\" (string, full address)\n" +
//                        "- \"Details\" (string, product details/description)\n" +
//                        "- \"Amount\" (string, the amount to collect, e.g., '1200')\n\n" +
//                        "Rules:\n" +
//                        "1. Return ONLY the JSON Array. No markdown formatting, no explanations.\n" +
//                        "2. If a field is missing, use an empty string \"\".\n\n" +
//                        "Text to process:\n" +
//                        rawText;

        // Improved prompt for strict JSON generation
        String prompt =
                "You are an expert logistics data entry assistant. Parse the following text into a structured JSON Array of delivery orders.\n\n" +

                        "OUTPUT FORMAT:\n" +
                        "Return ONLY a raw JSON Array of objects. No markdown formatting (no ```json), no conversational text.\n\n" +

                        "JSON SCHEMA:\n" +
                        "- \"Name\": (String) Customer name. Keep in original script (Bangla/English).\n" +
                        "- \"Phone\": (String) 11-digit format '01XXXXXXXXX'. Remove '+88', spaces, dashes.\n" +
                        "- \"Address\": (String) Delivery location ONLY (House, Road, Area, Thana, District).\n" +
                        "- \"Details\": (String) Product size, color, quantity, and delivery instructions.\n" +
                        "- \"Amount\": (String) Collection amount (COD). Numbers only (e.g., '1200'). Convert '1.5k' to '1500'. Remove 'Tk', 'Taka'.\n\n" +

                        "PROCESSING RULES:\n" +
                        "1. NOISE FILTER: Ignore greetings ('Hi bhai'), questions ('Kobe pabo?'), and conversational filler.\n" +
                        "2. SEPARATION: Do not put product info in 'Address'. Put it in 'Details'.\n" +
                        "3. MISSING DATA: If a field is not found, use an empty string \"\". Do not make up data.\n" +
                        "4. MULTIPLE ORDERS: If the text contains multiple different customers, create separate objects.\n\n" +

                        "EXAMPLES:\n" +
                        "Input: 'Salam bhai, ekta black shirt lagbe. Name: Rahim, 01711-112233, Adr: 10/A, Dhanmondi. Price 1.2k'\n" +
                        "Output: [{\"Name\":\"Rahim\",\"Phone\":\"01711112233\",\"Address\":\"10/A, Dhanmondi\",\"Details\":\"Black shirt\",\"Amount\":\"1200\"}]\n\n" +

                        "Input: 'Rohima Begum. 01800 000 000. Sylhet Sadar. Red saree. 2050tk'\n" +
                        "Output: [{\"Name\":\"Rohima Begum\",\"Phone\":\"01800000000\",\"Address\":\"Sylhet Sadar\",\"Details\":\"Red saree\",\"Amount\":\"2050\"}]\n\n" +

                        "Text to process:\n" +
                        rawText;

        // Structure request with generationConfig for JSON enforcement
        JsonObject root = new JsonObject();

        // Contents
        JsonArray contents = new JsonArray();
        JsonObject contentObj = new JsonObject();
        JsonArray parts = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt);
        parts.add(textPart);
        contentObj.add("parts", parts);
        contents.add(contentObj);
        root.add("contents", contents);

        // Generation Config - Force JSON response
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("responseMimeType", "application/json");
        root.add("generationConfig", generationConfig);

        Log.d("GEMINI_REQUEST", root.toString());

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;

        RequestBody requestBody = RequestBody.create(root.toString(), JSON);

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("Gemini API Error: " + response.code() + " " + response.message());
        }

        String respStr = response.body().string();
        Log.d("GEMINI_RESPONSE", respStr);

        return parseGeminiResponse(respStr);
    }

    private List<OrderItem> parseGeminiResponse(String respStr) {
        JsonObject root;
        try {
            root = JsonParser.parseString(respStr).getAsJsonObject();
        } catch (JsonSyntaxException e) {
            throw new RuntimeException("Failed to parse Gemini API response structure.");
        }

        JsonArray candidates = root.getAsJsonArray("candidates");
        if (candidates == null || candidates.size() == 0)
            throw new RuntimeException("No candidates returned from Gemini.");

        String text = candidates.get(0)
                .getAsJsonObject()
                .getAsJsonObject("content")
                .getAsJsonArray("parts")
                .get(0)
                .getAsJsonObject()
                .get("text").getAsString();

        // Clean up the text just in case (remove markdown blocks if model ignores MIME type)
        String jsonText = cleanJsonText(text);

        JsonElement jsonElement;
        try {
            jsonElement = JsonParser.parseString(jsonText);
        } catch (JsonSyntaxException e) {
            throw new RuntimeException("Gemini returned invalid JSON: " + jsonText);
        }

        if (!jsonElement.isJsonArray()) {
            // Handle edge case where it returns a single object instead of an array
            if (jsonElement.isJsonObject()) {
                JsonArray arr = new JsonArray();
                arr.add(jsonElement);
                jsonElement = arr;
            } else {
                throw new RuntimeException("Expected JSON Array, got: " + jsonText);
            }
        }

        List<OrderItem> list = new ArrayList<>();
        for (JsonElement el : jsonElement.getAsJsonArray()) {
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
        if (o.has(key) && !o.get(key).isJsonNull()) {
            return o.get(key).getAsString();
        }
        return "";
    }

    private String cleanJsonText(String text) {
        text = text.trim();
        // Remove markdown code blocks if present
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        return text.trim();
    }
}