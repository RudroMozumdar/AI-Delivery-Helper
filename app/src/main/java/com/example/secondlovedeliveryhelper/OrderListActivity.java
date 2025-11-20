package com.example.secondlovedeliveryhelper;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OrderListActivity extends AppCompatActivity {

    private BluetoothPrinterManager printerManager;
    private BluetoothAdapter bluetoothAdapter;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_list);

        // 1. Initialize Bluetooth Managers
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager.getAdapter();
        printerManager = new BluetoothPrinterManager(this);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        try {
            List<OrderItem> orders = CsvHelper.loadOrdersCsv(this);
            if (orders.isEmpty()) {
                Toast.makeText(this, "No orders found in CSV", Toast.LENGTH_SHORT).show();
            }
            // 2. Pass the print action listener to the adapter
            recyclerView.setAdapter(new OrderAdapter(orders, this::printSingleOrder));
        } catch (Exception e) {
            Toast.makeText(this, "Error loading CSV: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // 3. Logic to print a single order
    private void printSingleOrder(OrderItem item) {
        SharedPreferences prefs = getSharedPreferences("PrinterPrefs", MODE_PRIVATE);
        String address = prefs.getString("PrinterAddress", null);

        if (address == null) {
            Toast.makeText(this, "No printer saved. Pair in Main Screen first.", Toast.LENGTH_LONG).show();
            return;
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth is off", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Printing...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            try {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                // Connect
                printerManager.connect(this, device);
                // Print single item (wrapped in a list because the manager expects a list)
                printerManager.printInvoices(this, Collections.singletonList(item));
                // Close
                printerManager.close();

                runOnUiThread(() -> Toast.makeText(OrderListActivity.this, "Printed: " + item.name, Toast.LENGTH_SHORT).show());
            } catch (SecurityException se) {
                runOnUiThread(() -> Toast.makeText(OrderListActivity.this, "Permission Denied", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                printerManager.close(); // Ensure cleanup
                runOnUiThread(() -> Toast.makeText(OrderListActivity.this, "Print Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }
    // --- ADAPTER CLASS ---
    private static class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.ViewHolder> {
        private final List<OrderItem> list;
        private final OnPrintClickListener printListener;

        interface OnPrintClickListener {
            void onPrintClick(OrderItem item);
        }

        public OrderAdapter(List<OrderItem> list, OnPrintClickListener printListener) {
            this.list = list;
            this.printListener = printListener;
        }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_order_card, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            OrderItem item = list.get(position);
            holder.tvName.setText(item.name);
            holder.tvPhone.setText(item.phone);
            holder.tvAddress.setText(item.address);
            holder.tvDetails.setText(item.details);
            holder.tvAmount.setText("Tk " + item.amount);

            // This works for Button, ImageButton, or ImageView
            holder.btnPrintOne.setOnClickListener(v -> {
                if (printListener != null) {
                    printListener.onPrintClick(item);
                }
            });
        }

        @Override
        public int getItemCount() { return list.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvPhone, tvAddress, tvDetails, tvAmount;
            // CHANGE: Use 'View' instead of 'Button' to prevent casting errors
            View btnPrintOne;

            public ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.cardName);
                tvPhone = itemView.findViewById(R.id.cardPhone);
                tvAddress = itemView.findViewById(R.id.cardAddress);
                tvDetails = itemView.findViewById(R.id.cardDetails);
                tvAmount = itemView.findViewById(R.id.cardAmount);

                // This will now find the view regardless of whether it's an ImageButton or Button
                btnPrintOne = itemView.findViewById(R.id.btnPrintOne);
            }
        }
    }
}