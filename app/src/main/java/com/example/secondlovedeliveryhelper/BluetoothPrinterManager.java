package com.example.secondlovedeliveryhelper;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BluetoothPrinterManager {

    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // 58mm printers are usually 384 dots wide
    private static final int PRINTER_WIDTH_DOTS = 384;

    private final BluetoothAdapter adapter;
    private BluetoothSocket socket;
    private OutputStream outputStream;

    public BluetoothPrinterManager(Context ctx) {
        BluetoothManager manager = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();
    }

    private boolean hasBtPermission(Context ctx) {
        if (Build.VERSION.SDK_INT < 31) return true;
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    public Set<BluetoothDevice> getPaired(Context ctx) {
        if (!hasBtPermission(ctx)) throw new SecurityException("Bluetooth permission required");
        return adapter.getBondedDevices();
    }

    @SuppressLint("MissingPermission")
    public void connect(Context ctx, BluetoothDevice device) throws IOException {
        if (!hasBtPermission(ctx)) throw new SecurityException("Bluetooth permission required");

        close(); // close any existing connection

        socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
        adapter.cancelDiscovery();
        socket.connect();
        outputStream = socket.getOutputStream();

        // Initialize printer (ESC @)
        outputStream.write(new byte[]{0x1B, 0x40});
        outputStream.flush();
    }

    public void printInvoices(Context ctx, List<OrderItem> orders) throws IOException {

        if (outputStream == null)
            throw new IOException("Not connected");


        for (OrderItem o : orders) {
            printLogo(ctx);

            // Big font for header
            outputStream.write(new byte[]{0x1D, 0x21, 0x01});  // double size

            outputStream.write(format(o).getBytes("UTF-8"));
        }

        // Reset font
        outputStream.write(new byte[]{0x1D, 0x21, 0x00});

        // Feed paper
        outputStream.write("\n\n\n".getBytes());
        outputStream.flush();
    }

    public void close() {
        try {
            if (outputStream != null) {
                outputStream.flush();
                outputStream.close();
            }
        } catch (Exception ignored) {}
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception ignored) {}
        outputStream = null;
        socket = null;
    }

    // ===================== LOGO PRINTING =====================

    private void printLogo(Context ctx) throws IOException {
        Bitmap logo = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.logo);
        byte[] raster = bitmapToEscPosGSv0(logo);
        outputStream.write(raster);
        outputStream.flush();
    }


    /**
     * Convert bitmap to ESC/POS bit image using 24-dot double-density mode.
     * This sends the image in horizontal stripes of 24 pixels.
     */
    private byte[] convertBitmapToEscPos(Bitmap bmp) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();

        int bytesPerRow = (width + 7) / 8;  // one bit per pixel

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Process in stripes of 24 dots height (ESC * 33)
        for (int y = 0; y < height; y += 24) {
            baos.write(0x1B); // ESC
            baos.write('*');  // '*'
            baos.write(33);   // 24-dot double-density mode
            baos.write(bytesPerRow & 0xFF);         // nL
            baos.write((bytesPerRow >> 8) & 0xFF);  // nH

            for (int x = 0; x < bytesPerRow * 8; x++) {
                byte slice = 0;
                for (int bit = 0; bit < 8; bit++) {
                    int px = x + bit;
                    if (px < width) {
                        int colByte = 0;
                        for (int k = 0; k < 24; k++) {
                            int py = y + k;
                            if (py >= height) break;
                            int pixel = bmp.getPixel(px, py);
                            int r = (pixel >> 16) & 0xFF;
                            int g = (pixel >> 8) & 0xFF;
                            int b = pixel & 0xFF;
                            int gray = (r + g + b) / 3;
                            // threshold: darker pixel = black dot
                            if (gray < 128) {
                                // which bit to set? one byte per vertical pixel, but we send row-by-row,
                                // so pack them simply (most printers are forgiving)
                                colByte |= (1 << (7 - bit));
                            }
                        }
                        slice = (byte) colByte;
                    }
                }
                baos.write(slice);
            }
            // line feed after each stripe
            baos.write(0x0A);
        }

        return baos.toByteArray();
    }

    // ===================== TEXT FORMAT =====================

    private String format(OrderItem o) {
        String line = "------------------------------\n";
        return line +

                "Name   : " + safe(o.name) + "\n" +
                "Phone  : " + safe(o.phone) + "\n" +
                "Details: " + safe(o.details) + "\n\n" +
                "Amount: Tk " + safe(o.amount) + "\n\n" + line;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
    private byte[] bitmapToEscPosGSv0(Bitmap bitmap) {

        // 1. Resize to thermal printer max width (58mm = 384 dots)
        int targetWidth = 384;

        float ratio = (float) targetWidth / bitmap.getWidth();
        int newHeight = (int) (bitmap.getHeight() * ratio);

        Bitmap resized = Bitmap.createScaledBitmap(bitmap, targetWidth, newHeight, false);

        // 2. Convert to ARGB_8888 for safe pixel access
        Bitmap grayscale = resized.copy(Bitmap.Config.ARGB_8888, false);

        int width = grayscale.getWidth();
        int height = grayscale.getHeight();

        int bytesPerRow = width / 8;
        byte[] imageBytes = new byte[bytesPerRow * height];

        int index = 0;

        // 3. Convert each pixel to 1-bit (black/white)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x += 8) {
                byte b = 0;
                for (int bit = 0; bit < 8; bit++) {
                    int pixelX = x + bit;
                    int pixelColor = grayscale.getPixel(pixelX, y);

                    int r = (pixelColor >> 16) & 0xFF;
                    int g = (pixelColor >> 8) & 0xFF;
                    int bColor = pixelColor & 0xFF;

                    int gray = (r + g + bColor) / 3;

                    if (gray < 128) {
                        b |= (1 << (7 - bit)); // black pixel
                    }
                }
                imageBytes[index++] = b;
            }
        }

        // 4. Build ESC/POS GS v 0 command
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        baos.write(0x1D); // GS
        baos.write(0x76); // v
        baos.write(0x30); // 0
        baos.write(0x00); // mode 0 = normal
        baos.write(bytesPerRow & 0xFF);
        baos.write((bytesPerRow >> 8) & 0xFF);
        baos.write(height & 0xFF);
        baos.write((height >> 8) & 0xFF);

        baos.write(imageBytes, 0, imageBytes.length);

        return baos.toByteArray();
    }


}
