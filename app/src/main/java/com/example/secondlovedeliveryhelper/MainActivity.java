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
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.secondlovedeliveryhelper.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    private BluetoothPrinterManager printerManager;
    private BluetoothDevice selectedDevice;
    private BluetoothAdapter adapter;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> loadSavedPrinter());

    private final ActivityResultLauncher<Intent> saveExcelLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    viewModel.saveExcelToUri(result.getData().getData());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        // Setup UI Logic
        setupObservers();
        setupButtons();

        // Bluetooth Init
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();
        printerManager = new BluetoothPrinterManager(this);

        loadSavedPrinter();
        setupModelSpinner();
        requestPermissions();
    }

    private void setupObservers() {
        viewModel.getStatus().observe(this, msg -> {
            binding.tvStatus.setText(msg);
            if (msg.contains("Success") || msg.contains("Saved")) toast(msg);
        });

        viewModel.getLoading().observe(this, isLoading -> {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.INVISIBLE);
            binding.progressBar.setIndeterminate(isLoading);
        });

        viewModel.getOrders().observe(this, orders -> {
            if (orders != null && !orders.isEmpty()) {
                binding.tvStatus.setText("Ready. " + orders.size() + " orders generated.");
            }
        });
    }

    private void setupButtons() {
        binding.btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        binding.btnViewOrders.setOnClickListener(v ->
                startActivity(new Intent(this, OrderListActivity.class)));

        binding.btnGenerate.setOnClickListener(v ->
                viewModel.generateOrders(binding.etRawText.getText().toString(), binding.spModel.getSelectedItem().toString()));

        binding.btnCreatePathaoOrders.setOnClickListener(v -> viewModel.createPathaoOrders());

        binding.btnSaveExcel.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_TITLE, "orders.csv");
            saveExcelLauncher.launch(intent);
        });

        binding.btnSelectPrinter.setOnClickListener(v -> loadPairedDevices());
        binding.btnPrintAll.setOnClickListener(v -> printInvoices());
    }

    private void setupModelSpinner() {
        String[] models = {"gemini-2.5-flash", "gemini-2.5-pro"};
        binding.spModel.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, models));
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            });
        }
    }

    private void printInvoices() {
        if (selectedDevice == null) { toast("Select printer first"); return; }
        List<OrderItem> orders = viewModel.getOrders().getValue();
        if (orders == null || orders.isEmpty()) { toast("Generate orders first"); return; }

        binding.tvStatus.setText("Printing...");
        new Thread(() -> {
            try {
                printerManager.connect(this, selectedDevice);
                printerManager.printInvoices(this, orders);
                runOnUiThread(() -> toast("Printed Successfully"));
            } catch (Exception e) {
                runOnUiThread(() -> toast("Print Error: " + e.getMessage()));
            } finally {
                printerManager.close();
            }
        }).start();
    }

    @SuppressLint("MissingPermission")
    private void loadPairedDevices() {
        try {
            Set<BluetoothDevice> list = printerManager.getPaired(this);
            List<BluetoothDevice> devices = new ArrayList<>(list);
            List<String> names = new ArrayList<>();
            for (BluetoothDevice d : devices) names.add(d.getName());

            new AlertDialog.Builder(this)
                    .setTitle("Select Printer")
                    .setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names), (dlg, which) -> {
                        selectedDevice = devices.get(which);
                        binding.tvPrinter.setText("Selected: " + selectedDevice.getName());
                        getSharedPreferences("PrinterPrefs", MODE_PRIVATE).edit()
                                .putString("PrinterAddress", selectedDevice.getAddress()).apply();
                    }).show();
        } catch (Exception e) { toast("Pair printer first"); }
    }

    @SuppressLint("MissingPermission")
    private void loadSavedPrinter() {
        String addr = getSharedPreferences("PrinterPrefs", MODE_PRIVATE).getString("PrinterAddress", null);
        if (addr != null && adapter != null && adapter.isEnabled()) {
            try { selectedDevice = adapter.getRemoteDevice(addr); binding.tvPrinter.setText("Saved: " + selectedDevice.getName()); }
            catch (Exception e) { binding.tvPrinter.setText("Printer Error"); }
        }
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
}