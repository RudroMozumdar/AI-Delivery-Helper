package com.example.secondlovedeliveryhelper;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

class CsvSaver {

    public static File saveOrdersCsv(Context ctx, List<OrderItem> orders) throws IOException {
        File dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) dir = ctx.getFilesDir();
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "orders.csv");
        FileWriter fw = new FileWriter(file);
        fw.write("ItemType,StoreName,MerchantOrderId,RecipientName(*),RecipientPhone(*),RecipientAddress(*),RecipientCity(*),RecipientZone(*),RecipientArea,AmountToCollect(*),ItemQuantity,ItemWeight,ItemDesc,SpecialInstruction\n");
        for (OrderItem o : orders) {
            fw.write("parcel,Second_love,," +
                    escape(o.name) + "," +
                    escape(o.phone) + "," +
                    escape(o.address) + "," +
                    ",,," +
                    escape(o.amount) + "," +
                    "1,0.5," +
                    escape(o.details) + ",\n");
        }
        fw.flush();
        fw.close();
        return file;
    }

    private static String escape(String s) {
        if (s == null) return "";
        s = s.replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\"")) {
            s = "\"" + s + "\"";
        }
        return s;
    }
}
