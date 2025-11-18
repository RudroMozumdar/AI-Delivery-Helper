package com.example.secondlovedeliveryhelper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SettingsActivity extends AppCompatActivity {

    private EditText etGeminiKey, etClientId, etClientSecret, etUsername, etPassword;
    private TextView tvTokenStatus, tvLogoPath;
    private ImageView ivLogoPreview;
    private SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient client = new OkHttpClient();

    private final ActivityResultLauncher<Intent> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    // Persist permission to access this URI later
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    prefs.edit().putString("printer_logo_uri", uri.toString()).apply();
                    tvLogoPath.setText("Selected: " + uri.getLastPathSegment());
                    ivLogoPreview.setImageURI(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);

        etGeminiKey = findViewById(R.id.etGeminiKey);
        etClientId = findViewById(R.id.etClientId);
        etClientSecret = findViewById(R.id.etClientSecret);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        tvTokenStatus = findViewById(R.id.tvTokenStatus);
        tvLogoPath = findViewById(R.id.tvLogoPath);
        ivLogoPreview = findViewById(R.id.ivLogoPreview);

        loadSettings();

        findViewById(R.id.btnSave).setOnClickListener(v -> saveSettings());
        findViewById(R.id.btnFetchToken).setOnClickListener(v -> fetchToken());
        findViewById(R.id.btnSelectLogo).setOnClickListener(v -> selectLogo());

        String savedLogo = prefs.getString("printer_logo_uri", null);
        if(savedLogo != null) {
            try {
                ivLogoPreview.setImageURI(Uri.parse(savedLogo));
                tvLogoPath.setText("Logo Loaded");
            } catch (Exception e) {
                tvLogoPath.setText("Error loading saved logo");
            }
        }
    }

    private void loadSettings() {
        etGeminiKey.setText(prefs.getString("gemini_api_key", ""));
        etClientId.setText(prefs.getString("pathao_client_id", ""));
        etClientSecret.setText(prefs.getString("pathao_client_secret", ""));
        etUsername.setText(prefs.getString("pathao_username", ""));
        etPassword.setText(prefs.getString("pathao_password", ""));

        String token = prefs.getString("pathao_access_token", "");
        if (!token.isEmpty()) {
            tvTokenStatus.setText("Token Active");
            tvTokenStatus.setTextColor(0xFF00FF00); // Green
        }
    }

    private void saveSettings() {
        prefs.edit()
                .putString("gemini_api_key", etGeminiKey.getText().toString().trim())
                .putString("pathao_client_id", etClientId.getText().toString().trim())
                .putString("pathao_client_secret", etClientSecret.getText().toString().trim())
                .putString("pathao_username", etUsername.getText().toString().trim())
                .putString("pathao_password", etPassword.getText().toString().trim())
                .apply();
        Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show();
    }

    private void selectLogo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        pickImageLauncher.launch(intent);
    }

    private void fetchToken() {
        String clientId = etClientId.getText().toString().trim();
        String clientSecret = etClientSecret.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            Toast.makeText(this, "Missing Credentials", Toast.LENGTH_SHORT).show();
            return;
        }

        tvTokenStatus.setText("Fetching...");
        tvTokenStatus.setTextColor(0xFFFFFF00); // Yellow

        executor.execute(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("client_id", clientId);
                json.put("client_secret", clientSecret);
                json.put("username", username);
                json.put("password", password);
                json.put("grant_type", "password");

                RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json"));
                Request request = new Request.Builder()
                        .url("https://api-hermes.pathao.com/aladdin/api/v1/issue-token")
                        .post(body)
                        .build();

                Response response = client.newCall(request).execute();
                String respStr = response.body().string();

                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        if (response.isSuccessful()) {
                            JSONObject respJson = new JSONObject(respStr);
                            String token = respJson.getString("access_token");
                            prefs.edit().putString("pathao_access_token", token).apply();
                            tvTokenStatus.setText("Token Fetched Successfully!");
                            tvTokenStatus.setTextColor(0xFF00FF00);
                        } else {
                            tvTokenStatus.setText("Failed: " + response.code());
                            tvTokenStatus.setTextColor(0xFFFF0000);
                        }
                    } catch (Exception e) {
                        tvTokenStatus.setText("Error parsing response");
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> tvTokenStatus.setText("Network Error"));
            }
        });
    }
}