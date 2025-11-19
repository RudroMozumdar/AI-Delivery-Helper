package com.example.secondlovedeliveryhelper;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BluetoothPrinterManager {
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final BluetoothAdapter adapter;
    private BluetoothSocket socket;
    private OutputStream outputStream;

    public BluetoothPrinterManager(Context ctx) {
        BluetoothManager manager = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();
    }

    private boolean hasBtPermission(Context ctx) {
        if (Build.VERSION.SDK_INT < 31) return true;
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    public Set<BluetoothDevice> getPaired(Context ctx) {
        if (!hasBtPermission(ctx)) throw new SecurityException("Bluetooth permission required");
        return adapter.getBondedDevices();
    }

    @SuppressLint("MissingPermission")
    public void connect(Context ctx, BluetoothDevice device) throws IOException {
        if (!hasBtPermission(ctx)) throw new SecurityException("Bluetooth permission required");
        close();
        socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
        adapter.cancelDiscovery();
        socket.connect();
        outputStream = socket.getOutputStream();
        outputStream.write(new byte[]{0x1B, 0x40}); // Init
        outputStream.flush();
    }

    public void printInvoices(Context ctx, List<OrderItem> orders) throws IOException {
        if (outputStream == null) throw new IOException("Not connected");
        for (OrderItem o : orders) {
            printLogo(ctx);
            outputStream.write(new byte[]{0x1D, 0x21, 0x01}); // Double size
            outputStream.write(format(o).getBytes("UTF-8"));
        }
        outputStream.write(new byte[]{0x1D, 0x21, 0x00}); // Reset
        outputStream.write("\n\n\n".getBytes());
        outputStream.flush();
    }

    public void close() {
        try {
            if (outputStream != null) outputStream.close();
        } catch (Exception ignored) {
        }
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
        outputStream = null;
        socket = null;
    }

    private void printLogo(Context ctx) throws IOException {
        Bitmap logo = null;
        SharedPreferences prefs = ctx.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String uriString = prefs.getString("printer_logo_uri", null);

        if (uriString != null) {
            try {
                Uri uri = Uri.parse(uriString);
                InputStream is = ctx.getContentResolver().openInputStream(uri);
                logo = BitmapFactory.decodeStream(is);
                is.close();
            } catch (Exception e) {
                // Fallback if custom logo fails
            }
        }

        // Default fallback
        if (logo == null) {
            logo = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.logo);
        }

        if (logo != null) {
            byte[] raster = bitmapToEscPosGSv0(logo);
            outputStream.write(raster);
            outputStream.flush();
        }
    }

    private String format(OrderItem o) {
        String line = "------------------------------\n";
        return "            Ferdous           \n" +
                line +
                "Name   : " + safe(o.name) + "\n" +
                "Phone  : " + safe(o.phone) + "\n" +
                "Details: " + safe(o.details) + "\n\n" +
                "Amount: Tk " + safe(o.amount) + "\n\n" + line;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private byte[] bitmapToEscPosGSv0(Bitmap bitmap) throws IOException {
        int targetWidth = 384;
        float ratio = (float) targetWidth / bitmap.getWidth();
        int newHeight = (int) (bitmap.getHeight() * ratio);
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, targetWidth, newHeight, false);
        Bitmap grayscale = resized.copy(Bitmap.Config.ARGB_8888, false);
        int width = grayscale.getWidth();
        int height = grayscale.getHeight();
        int bytesPerRow = width / 8;
        byte[] imageBytes = new byte[bytesPerRow * height];
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x += 8) {
                byte b = 0;
                for (int bit = 0; bit < 8; bit++) {
                    int pixelX = x + bit;
                    int pixelColor = grayscale.getPixel(pixelX, y);
                    int r = (pixelColor >> 16) & 0xFF;
                    int g = (pixelColor >> 8) & 0xFF;
                    int bColor = pixelColor & 0xFF;
                    if ((r + g + bColor) / 3 < 128) b |= (byte) (1 << (7 - bit));
                }
                imageBytes[index++] = b;
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(new byte[]{0x1D, 0x76, 0x30, 0x00});
        baos.write(bytesPerRow & 0xFF);
        baos.write((bytesPerRow >> 8) & 0xFF);
        baos.write(height & 0xFF);
        baos.write((height >> 8) & 0xFF);
        baos.write(imageBytes, 0, imageBytes.length);
        return baos.toByteArray();
    }
}