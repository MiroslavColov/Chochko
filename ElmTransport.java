package bg.cromasteering.diag;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/** Minimal Bluetooth Classic SPP transport for ELM327 compatible adapters. */
final class ElmTransport {
    static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private volatile BluetoothSocket socket;
    private InputStream in;
    private OutputStream out;

    synchronized void connect(BluetoothDevice device) throws IOException {
        close();
        BluetoothSocket next;
        try { next = device.createRfcommSocketToServiceRecord(SPP_UUID); }
        catch (SecurityException exc) { throw new IOException("Bluetooth permission denied", exc); }
        socket = next;
        try {
            next.connect();
            in = next.getInputStream();
            out = next.getOutputStream();
        } catch (IOException | SecurityException exc) {
            close();
            throw new IOException("SPP connection failed: " + exc.getMessage(), exc);
        }
    }

    boolean connected() { return socket != null && socket.isConnected() && in != null && out != null; }

    synchronized String send(String command, int timeoutMs) throws IOException {
        if (!connected()) throw new IOException("Not connected to an ELM327 adapter.");
        String sanitized = command.trim().toUpperCase(Locale.US);
        if (!sanitized.matches("[A-Z0-9 ]{1,30}")) throw new IOException("Invalid command");
        // Drain stale bytes that may be left by an earlier response.
        while (in.available() > 0) in.read();
        out.write((sanitized + "\r").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        StringBuilder response = new StringBuilder();
        long stopAt = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < stopAt) {
            int available = in.available();
            if (available > 0) {
                for (int i = 0; i < available; i++) {
                    int b = in.read();
                    if (b < 0) throw new IOException("Adapter closed the connection");
                    if (b == '>') return response.toString().replace('\r', '\n').trim();
                    if (response.length() < 10000 && b != 0) response.append((char) b);
                }
            } else {
                try { Thread.sleep(25); }
                catch (InterruptedException exc) { Thread.currentThread().interrupt(); throw new IOException("Interrupted"); }
            }
        }
        throw new IOException("Timed out waiting for ELM prompt. Partial reply: " + response);
    }

    void close() {
        BluetoothSocket s = socket;
        socket = null;
        in = null;
        out = null;
        if (s != null) try { s.close(); } catch (IOException ignored) { }
    }
}
