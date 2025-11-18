package com.example.secondlovedeliveryhelper;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.secondlovedeliveryhelper.databinding.ActivityMainBinding;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private BluetoothAdapter adapter;
    private BluetoothPrinterManager printerManager;
    private BluetoothDevice selectedDevice;

    private List<OrderItem> currentOrders = new ArrayList<>();

    // OkHttpClient for Pathao API
    private final OkHttpClient httpClient = new OkHttpClient();

    // Permission Launcher
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                // Now that permissions are granted (or denied), try to load the saved printer.
                // This is important for Android 12+ where permission is needed to get the device name.
                loadSavedPrinter();
            });
    private final ActivityResultLauncher<Intent> saveExcelLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    saveCsvToUri(uri);
                }
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();

        printerManager = new BluetoothPrinterManager(this);
        loadSavedPrinter(); // <-- ADD THIS
        setupModelSpinner();
        setupButtons();

        requestPermissions();

    }

    private void setupModelSpinner() {
        String[] models = {
                "gemini-2.5-flash",
                "gemini-2.5-pro"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, models);
        binding.spModel.setAdapter(adapter);
    }
    private void setupButtons() {
        binding.btnSelectPrinter.setOnClickListener(v -> loadPairedDevices());
        binding.btnGenerate.setOnClickListener(v -> generateInvoices());
        binding.btnPrintAll.setOnClickListener(v -> printInvoices());
        binding.btnSaveExcel.setOnClickListener(v -> openSaveFileDialog());

        // Setup for the new button
        binding.btnCreatePathaoOrders.setOnClickListener(v -> createPathaoOrders());
    }
    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            });
        }
    }
    private void openSaveFileDialog() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "orders.csv");
        saveExcelLauncher.launch(intent);
    }
    private void saveCsvToUri(Uri uri) {
        try {
            String csv = generateCsvString(currentOrders); // We create this method next

            OutputStream os = getContentResolver().openOutputStream(uri);
            os.write(csv.getBytes());
            os.close();

            toast("Excel saved successfully!");
        } catch (Exception e) {
            toast("Failed: " + e.getMessage());
        }
    }
    public static String generateCsvString(List<OrderItem> orders) {
        StringBuilder sb = new StringBuilder();

        sb.append("ItemType,StoreName,MerchantOrderId,RecipientName(*),RecipientPhone(*),RecipientAddress(*),RecipientCity(*),RecipientZone(*),RecipientArea,AmountToCollect(*),ItemQuantity,ItemWeight,ItemDesc,SpecialInstruction\n");

        for (OrderItem o : orders) {
            sb.append("parcel,Second_love,,");
            sb.append(o.name).append(",");
            sb.append(o.phone).append(",");
            sb.append(o.address.replace(",", " ")).append(",");
            sb.append(",,,");
            sb.append(o.amount).append(",");
            sb.append("1,0.5,");
            sb.append(o.details.replace(",", " ")).append("\n");
        }

        return sb.toString();
    }
    @SuppressLint("MissingPermission")
    private void loadPairedDevices() {

        try {
            Set<BluetoothDevice> list = printerManager.getPaired(this);

            List<BluetoothDevice> devices = new ArrayList<>(list);
            List<String> names = new ArrayList<>();

            for (BluetoothDevice d : devices) {
                names.add(d.getName() + " (" + d.getAddress() + ")");
            }

            ArrayAdapter<String> adapter =
                    new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names);

            new AlertDialog.Builder(this)
                    .setTitle("Select Printer")
                    .setAdapter(adapter, (dlg, which) -> {
                        selectedDevice = devices.get(which);
                        binding.tvPrinter.setText("Selected: " + selectedDevice.getName());

                        // --- ADD THIS ---
                        // Save the selected device's address
                        getSharedPreferences("PrinterPrefs", MODE_PRIVATE)
                                .edit()
                                .putString("PrinterAddress", selectedDevice.getAddress())
                                .apply();
                        // --- END ---
                    })
                    .show();

        } catch (Exception e) {
            Toast.makeText(this, "Pair your printer first", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Loads the saved printer address from SharedPreferences and sets it as the
     * active 'selectedDevice'.
     */
    @SuppressLint("MissingPermission")
    private void loadSavedPrinter() {
        android.content.SharedPreferences prefs = getSharedPreferences("PrinterPrefs", MODE_PRIVATE);
        String printerAddress = prefs.getString("PrinterAddress", null);

        if (printerAddress == null) {
            binding.tvPrinter.setText("No printer selected");
            return; // No saved printer
        }

        if (adapter == null) {
            toast("Bluetooth not supported");
            return; // Bluetooth not supported
        }

        // Check for permission on Android 12+
        if (Build.VERSION.SDK_INT >= 31 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

            // We can't get the device name without permission, but we can't ask for it here.
            // requestPermissions() will handle asking, and the launcher's callback
            // will re-run this method.
            binding.tvPrinter.setText("Grant permission to load printer");
            return;
        }

        try {
            BluetoothDevice device = adapter.getRemoteDevice(printerAddress);
            if (device != null) {
                selectedDevice = device;
                binding.tvPrinter.setText("Selected: " + selectedDevice.getName());
            } else {
                binding.tvPrinter.setText("Saved printer not found");
            }
        } catch (Exception e) {
            Log.e("LoadPrinter", "Failed to load saved printer", e);
            binding.tvPrinter.setText("Error loading printer");
            // Clear the invalid preference
            prefs.edit().remove("PrinterAddress").apply();
        }
    }

    private void generateInvoices() {
        String text = binding.etRawText.getText().toString().trim();

        if (text.isEmpty()) {
            toast("Enter raw text");
            return;
        }

        binding.tvStatus.setText("Processing…");
        binding.progressBar.setProgress(20);

        executor.execute(() -> {
            try {
                // NOTE: Hardcoding API keys is insecure. Consider a safer alternative.
                GeminiClient gem = new GeminiClient("AIzaSyCArK2u4CIXJt-JRrIPnpzWy-aohjUFPYg");

                List<OrderItem> orders =
                        gem.parseOrders(text, binding.spModel.getSelectedItem().toString());

                currentOrders = orders;

                File csv = CsvSaver.saveOrdersCsv(MainActivity.this, orders);
                File pdf = PdfGenerator.generateInvoicesPdf(MainActivity.this, orders);

                runOnUiThread(() -> {
                    toast("Generated");
                    binding.progressBar.setProgress(100);
                    binding.tvStatus.setText("Done. " + orders.size() + " orders processed.");
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    toast("Error: " + e.getMessage());
                    binding.tvStatus.setText("Error: " + e.getMessage());
                });
            }
        });
    }

    private void printInvoices() {
        if (selectedDevice == null) {
            toast("Select printer first");
            return;
        }
        if (currentOrders.isEmpty()) {
            toast("Generate invoices first");
            return;
        }

        binding.tvStatus.setText("Printing...");
        binding.progressBar.setProgress(40);

        executor.execute(() -> {
            try {
                printerManager.connect(MainActivity.this, selectedDevice);
                printerManager.printInvoices(MainActivity.this, currentOrders);


                runOnUiThread(() -> toast("Printed"));

            } catch (Exception e) {
                runOnUiThread(() -> toast("Error: " + e.getMessage()));
            } finally {
                printerManager.close();
            }
        });
    }

    /**
     * New method to create Pathao orders.
     */
    private void createPathaoOrders() {
        if (currentOrders.isEmpty()) {
            toast("Generate invoices first");
            return;
        }

        binding.tvStatus.setText("Creating Pathao orders...");
        binding.progressBar.setProgress(30);

        executor.execute(() -> {
            try {
                // 1. Build the JSON payload from the currentOrders list
                String jsonBody = buildPathaoJson(currentOrders);
                Log.d("PathaoAPI", "Request Body: " + jsonBody);

                // 2. Define API constants
                String url = "https://api-hermes.pathao.com/aladdin/api/v1/orders/bulk";

                // --- SECURITY WARNING ---
                // Hardcoding tokens is very insecure and bad practice.
                // This token will expire. You must fetch a new one and store it securely.
                String authToken = "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI3OTE3NiIsImF1ZCI6WyI2MTkzIl0sImV4cCI6MTc3MDc1MTY4MSwibmJmIjoxNzYyOTc1NjgxLCJpYXQiOjE3NjI5NzU2ODEsImp0aSI6IjI3N2JlOWIyY2M5MTNhOTFhN2IxOGYyMDFhOGE3NmIxMjQ3OWFjMjBiMjljODk1YTkxMGVkMjlkY2M3YWJjOGYiLCJtZXJjaGFudF9pZCI6IllSZEc0cFFlRHoiLCJzY29wZXMiOltdfQ.uoyOPDGOUI2UvWvdFWgLuNb5d0DpnsX3TabWOScfs-nTfcYsu-Ht7BKt4dmZvQ8VCZ6r48barcCiUAfkwieuH_IYTJcnoLDXg088IWVhiEiQIwPRABFOuvfZp-zgUmth_MnbOQleBT9J8mw8b_RxbffR7-CEkXDEdWexjs6IEQYagGNZ4706nuXlc_b_fegvh6sIP8ZJRYVddkwI6WGwSDd6TZXEI8gkj4OLvMHoLGVMqxVcuZ0V62cdiks7xnmhDnsWcUUD7zjm9l0mGDP7S1q_f9kMJLsXPPn2gHrgAqC_W6FCK0m0onMT_hAL5SIhytrvATEvdyq7h1Az3JwBNAd6VPiNmdW9YNuuaMUGVB-S2YOKqPVVvpuJdl0pZiRo3ZOofMlCfZgD3_Zt1kkyo49ntspR3EfWDc0GjJfaz7pDO0IYtZIFqEQnz6fYKV3nZMVUo51C8Mi6eS9KwAlaQLRY6hHAhXDtkJWdwU0r3WV2uOhr13BPpS4uUdqhwz3SzQMHmU6w5vPS61_-JeHF3XvHzteDWh7chKHcy_cbwxe6AD_7biSLLAphSpfyAAqUh8WXkIWnp0wllssD1Z_Sr9GTUCf7eAwCl-KBTUclUz3TAoBUsaCXODbkz_-52Ic1Y6oS7hECA9da4btct_fimyPLAfWWF2Dmy1-RfKpofrI";

                MediaType JSON = MediaType.get("application/json; charset=utf-8");
                RequestBody body = RequestBody.create(jsonBody, JSON);

                // 3. Create the request
                Request request = new Request.Builder()
                        .url(url)
                        .header("Authorization", authToken)
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .post(body)
                        .build();

                // 4. Execute the request
                // Note: You must add <uses-permission android:name="android.permission.INTERNET" /> to your AndroidManifest.xml
                Response response = httpClient.newCall(request).execute();
                String responseBody = response.body().string();
                Log.d("PathaoAPI", "Response: " + responseBody);

                // 5. Handle the response on the UI thread
                if (response.isSuccessful() && response.code() == 202) {
                    runOnUiThread(() -> {
                        toast("Pathao bulk order request accepted!");
                        binding.tvStatus.setText("Pathao orders accepted");
                        binding.progressBar.setProgress(100);
                    });
                } else {
                    runOnUiThread(() -> {
                        toast("Pathao API Error: " + response.code());
                        binding.tvStatus.setText("Pathao Error: " + responseBody);
                    });
                }

            } catch (Exception e) {
                Log.e("PathaoAPI", "Error creating orders", e);
                runOnUiThread(() -> {
                    toast("Failed to create orders: " + e.getMessage());
                    binding.tvStatus.setText("Error: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Helper method to build the JSON string for the Pathao API.
     */
    private String buildPathaoJson(List<OrderItem> orders) {
        JsonObject root = new JsonObject();
        JsonArray ordersArray = new JsonArray();

        for (OrderItem item : orders) {
            JsonObject orderObj = new JsonObject();

            // Convert amount to integer
            int amountToCollect = 0;
            try {
                // Remove "Tk" or other non-numeric characters if present
                String cleanAmount = item.amount.replaceAll("[^\\d.]", "");
                if (!cleanAmount.isEmpty()) {
                    amountToCollect = (int) Double.parseDouble(cleanAmount);
                }
            } catch (NumberFormatException e) {
                Log.w("PathaoAPI", "Invalid amount format: " + item.amount);
                amountToCollect = 0; // Default to 0 if parsing fails
            }

            // Hardcoded values from your example
            orderObj.addProperty("store_id", 82357);
            orderObj.addProperty("delivery_type", 48); // 48 = Normal Delivery
            orderObj.addProperty("item_type", 2); // 2 = Parcel
            orderObj.addProperty("item_quantity", 1);
            orderObj.addProperty("item_weight", "0.5");

            // Values from OrderItem
            orderObj.addProperty("recipient_name", item.name);
            orderObj.addProperty("recipient_phone", item.phone);
            orderObj.addProperty("recipient_address", item.address);
            orderObj.addProperty("amount_to_collect", amountToCollect);
            orderObj.addProperty("item_description", item.details);

            // Optional fields
            orderObj.addProperty("merchant_order_id", "");
            orderObj.addProperty("special_instruction", "");

            ordersArray.add(orderObj);
        }

        root.add("orders", ordersArray);
        return root.toString();
    }


    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}