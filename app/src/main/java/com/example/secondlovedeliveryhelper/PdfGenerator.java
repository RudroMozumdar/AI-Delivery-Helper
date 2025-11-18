package com.example.secondlovedeliveryhelper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

class PdfGenerator {

    private static final String STORE_NAME = "Second Love";
    private static final String STORE_ADDRESS = "Dhaka, Bangladesh";

    public static File generateInvoicesPdf(Context ctx, List<OrderItem> orders) throws IOException {
        PdfDocument pdf = new PdfDocument();
        Paint paint = new Paint();
        paint.setTextSize(10);

        int pageWidth = 300;   // approx for 58mm ticket (in pixels, not exact mm)
        int pageHeight = 800;

        int y = 20;
        int pageNumber = 1;
        PdfDocument.Page page = pdf.startPage(
                new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create());
        Canvas canvas = page.getCanvas();

        for (int i = 0; i < orders.size(); i++) {
            OrderItem o = orders.get(i);

            if (y > pageHeight - 100) {
                pdf.finishPage(page);
                pageNumber++;
                page = pdf.startPage(
                        new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create());
                canvas = page.getCanvas();
                y = 20;
            }

            paint.setFakeBoldText(true);
            canvas.drawText(STORE_NAME, 10, y, paint);
            y += 14;
            paint.setFakeBoldText(false);
            canvas.drawText(STORE_ADDRESS, 10, y, paint);
            y += 10;
            canvas.drawLine(5, y, pageWidth - 5, y, paint);
            y += 10;

            // No invoice number printed to match your printer rule, but we could have it in PDF.
            paint.setFakeBoldText(true);
            canvas.drawText("Invoice #" + String.format("%03d", i + 1), 10, y, paint);
            paint.setFakeBoldText(false);
            y += 12;

            canvas.drawText("Name: " + o.name, 10, y, paint); y += 12;
            canvas.drawText("Phone: " + o.phone, 10, y, paint); y += 12;
            canvas.drawText("Address: " + o.address, 10, y, paint); y += 12;
            paint.setFakeBoldText(true);
            canvas.drawText("Details:", 10, y, paint);
            paint.setFakeBoldText(false);
            y += 12;
            for (String line : wrapText(o.details, 40)) {
                canvas.drawText(line, 10, y, paint);
                y += 12;
            }
            paint.setFakeBoldText(true);
            canvas.drawText("Amount: Tk " + o.amount, 10, y, paint);
            paint.setFakeBoldText(false);
            y += 16;

            canvas.drawLine(5, y, pageWidth - 5, y, paint);
            y += 16;
        }

        pdf.finishPage(page);

        File dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) dir = ctx.getFilesDir();
        if (!dir.exists()) dir.mkdirs();
        File outFile = new File(dir, "all_invoices.pdf");
        FileOutputStream fos = new FileOutputStream(outFile);
        pdf.writeTo(fos);
        fos.close();
        pdf.close();
        return outFile;
    }

    private static java.util.List<String> wrapText(String text, int maxChars) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (text == null) return list;
        text = text.trim();
        while (text.length() > maxChars) {
            int cut = maxChars;
            int space = text.lastIndexOf(' ', maxChars);
            if (space > 0) cut = space;
            list.add(text.substring(0, cut));
            text = text.substring(cut).trim();
        }
        if (!text.isEmpty()) list.add(text);
        return list;
    }
}
