package com.example.secondlovedeliveryhelper;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainViewModel extends AndroidViewModel {

    private final MutableLiveData<List<OrderItem>> currentOrders = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> pathaoSuccess = new MutableLiveData<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient httpClient = new OkHttpClient();

    public MainViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<OrderItem>> getOrders() { return currentOrders; }
    public LiveData<String> getStatus() { return statusMessage; }
    public LiveData<Boolean> getLoading() { return isLoading; }
    public LiveData<Boolean> getPathaoSuccess() { return pathaoSuccess; }

    public void generateOrders(String rawText, String model) {
        if (rawText.isEmpty()) {
            statusMessage.setValue("Please enter text.");
            return;
        }

        SharedPreferences prefs = getApplication().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String apiKey = prefs.getString("gemini_api_key", "");

        if (apiKey.isEmpty()) {
            statusMessage.setValue("Gemini API Key not found. Please set it in Settings.");
            return;
        }

        isLoading.setValue(true);
        statusMessage.setValue("Processing with Gemini...");

        executor.execute(() -> {
            try {
                GeminiClient gem = new GeminiClient(apiKey);
                List<OrderItem> orders = gem.parseOrders(rawText, model);

                // Save CSV automatically
                CsvHelper.saveOrdersCsv(getApplication(), orders);

                currentOrders.postValue(orders);
                statusMessage.postValue("Success! " + orders.size() + " orders generated.");
            } catch (Exception e) {
                statusMessage.postValue("Error: " + e.getMessage());
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    public void createPathaoOrders() {
        List<OrderItem> orders = currentOrders.getValue();
        if (orders == null || orders.isEmpty()) {
            statusMessage.setValue("No orders to create.");
            return;
        }

        SharedPreferences prefs = getApplication().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String token = prefs.getString("pathao_access_token", "");

        if (token.isEmpty()) {
            statusMessage.setValue("Pathao Token missing. Go to Settings > Fetch Token.");
            return;
        }

        isLoading.setValue(true);
        statusMessage.setValue("Creating orders on Pathao...");

        executor.execute(() -> {
            try {
                String jsonBody = buildPathaoJson(orders, prefs); // Pass prefs to helper
                String url = "[https://api-hermes.pathao.com/aladdin/api/v1/orders/bulk](https://api-hermes.pathao.com/aladdin/api/v1/orders/bulk)";

                RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json"));

                Request request = new Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .post(body)
                        .build();

                Response response = httpClient.newCall(request).execute();
                String respBody = response.body().string();

                if (response.isSuccessful()) {
                    pathaoSuccess.postValue(true);
                    statusMessage.postValue("Pathao Orders Created Successfully!");
                } else {
                    statusMessage.postValue("Pathao Failed (" + response.code() + "): " + respBody);
                }
            } catch (Exception e) {
                statusMessage.postValue("Network Error / Config Error: " + e.getMessage());
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    private String buildPathaoJson(List<OrderItem> orders, SharedPreferences prefs) throws Exception {
        JsonObject root = new JsonObject();
        JsonArray ordersArray = new JsonArray();

        // Get Store ID from Settings
        String storeIdStr = prefs.getString("pathao_store_id", "").trim();
        int storeId;
        if (storeIdStr.isEmpty()) {
            throw new Exception("Store ID missing in Settings");
        }
        try {
            storeId = Integer.parseInt(storeIdStr);
        } catch (NumberFormatException e) {
            throw new Exception("Invalid Store ID: must be a number");
        }

        for (OrderItem item : orders) {
            JsonObject orderObj = new JsonObject();

            int amount = 0;
            try {
                amount = (int) Double.parseDouble(item.amount.replaceAll("[^\\d.]", ""));
            } catch (Exception ignored) {}

            orderObj.addProperty("store_id", storeId); // Used from settings
            orderObj.addProperty("delivery_type", 48);
            orderObj.addProperty("item_type", 2);
            orderObj.addProperty("item_quantity", 1);
            orderObj.addProperty("item_weight", "0.5");
            orderObj.addProperty("recipient_name", item.name);
            orderObj.addProperty("recipient_phone", item.phone);
            orderObj.addProperty("recipient_address", item.address);
            orderObj.addProperty("amount_to_collect", amount);
            orderObj.addProperty("item_description", item.details);
            orderObj.addProperty("merchant_order_id", "");
            orderObj.addProperty("special_instruction", "");

            ordersArray.add(orderObj);
        }
        root.add("orders", ordersArray);
        return root.toString();
    }

    public void saveExcelToUri(Uri uri) {
        executor.execute(() -> {
            try {
                List<OrderItem> orders = currentOrders.getValue();
                if (orders == null) orders = new ArrayList<>();

                String csv = CsvHelper.generateCsvString(orders);
                OutputStream os = getApplication().getContentResolver().openOutputStream(uri);
                os.write(csv.getBytes());
                os.close();
                statusMessage.postValue("Excel saved successfully to selected location.");
            } catch (Exception e) {
                statusMessage.postValue("Save Failed: " + e.getMessage());
            }
        });
    }
}