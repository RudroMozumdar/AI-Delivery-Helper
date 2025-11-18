package com.example.secondlovedeliveryhelper;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class OrderListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_list);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        try {
            List<OrderItem> orders = CsvHelper.loadOrdersCsv(this);
            if (orders.isEmpty()) {
                Toast.makeText(this, "No orders found in CSV", Toast.LENGTH_SHORT).show();
            }
            recyclerView.setAdapter(new OrderAdapter(orders));
        } catch (Exception e) {
            Toast.makeText(this, "Error loading CSV: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // --- ADAPTER CLASS ---
    private static class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.ViewHolder> {
        private final List<OrderItem> list;

        public OrderAdapter(List<OrderItem> list) { this.list = list; }

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
        }

        @Override
        public int getItemCount() { return list.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvPhone, tvAddress, tvDetails, tvAmount;
            public ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.cardName);
                tvPhone = itemView.findViewById(R.id.cardPhone);
                tvAddress = itemView.findViewById(R.id.cardAddress);
                tvDetails = itemView.findViewById(R.id.cardDetails);
                tvAmount = itemView.findViewById(R.id.cardAmount);
            }
        }
    }
}