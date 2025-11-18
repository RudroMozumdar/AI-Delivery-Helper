package com.example.secondlovedeliveryhelper;

import android.content.Context;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class CsvHelper {

    public static File saveOrdersCsv(Context ctx, List<OrderItem> orders) throws IOException {
        File dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) dir = ctx.getFilesDir();
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "orders.csv");
        FileWriter fw = new FileWriter(file);
        fw.write(generateCsvString(orders));
        fw.flush();
        fw.close();
        return file;
    }

    public static List<OrderItem> loadOrdersCsv(Context ctx) throws IOException {
        List<OrderItem> list = new ArrayList<>();
        File dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) dir = ctx.getFilesDir();
        File file = new File(dir, "orders.csv");

        if (!file.exists()) return list;

        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;
        boolean isFirst = true;
        while ((line = br.readLine()) != null) {
            if (isFirst) { isFirst = false; continue; } // Skip header

            // Basic CSV split (Note: simpler than full regex for now)
            // We rely on the structure: parcel,Second_love,,NAME,PHONE,ADDRESS,,,AMOUNT,1,0.5,DETAILS
            // Index mapping: 3=Name, 4=Phone, 5=Address, 9=Amount, 12=Details

            String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
            if (parts.length >= 13) {
                String name = unescape(parts[3]);
                String phone = unescape(parts[4]);
                String address = unescape(parts[5]);
                String amount = unescape(parts[9]);
                String details = unescape(parts[12]);
                list.add(new OrderItem(name, phone, address, details, amount));
            }
        }
        br.close();
        return list;
    }

    public static String generateCsvString(List<OrderItem> orders) {
        StringBuilder sb = new StringBuilder();
        sb.append("ItemType,StoreName,MerchantOrderId,RecipientName(*),RecipientPhone(*),RecipientAddress(*),RecipientCity(*),RecipientZone(*),RecipientArea,AmountToCollect(*),ItemQuantity,ItemWeight,ItemDesc,SpecialInstruction\n");
        for (OrderItem o : orders) {
            sb.append("parcel,Second_love,,")
                    .append(escape(o.name)).append(",")
                    .append(escape(o.phone)).append(",")
                    .append(escape(o.address)).append(",")
                    .append(",,,")
                    .append(escape(o.amount)).append(",")
                    .append("1,0.5,")
                    .append(escape(o.details)).append(",\n");
        }
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        s = s.replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\"")) {
            s = "\"" + s + "\"";
        }
        return s;
    }

    private static String unescape(String s) {
        if (s == null) return "";
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return s.replace("\"\"", "\"");
    }
}