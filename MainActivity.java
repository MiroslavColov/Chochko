package bg.cromasteering.diag;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Croma Steering Diag v0.1: ELM327 adapter connectivity and experimental read-only K-line probe.
 * Important: TRW Croma steering ECU target address / protocol aren't validated on hardware.
 */
public class MainActivity extends Activity {
    private static final int BLUETOOTH_REQUEST = 401;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ElmTransport elm = new ElmTransport();
    private final List<BluetoothDevice> devices = new ArrayList<>();
    private final StringBuilder log = new StringBuilder();
    private TextView status, voltage, pumpStatus, logView;
    private Spinner spinner;
    private volatile boolean busy = false;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.connectionStatus);
        voltage = findViewById(R.id.batteryVoltage);
        pumpStatus = findViewById(R.id.pumpStatus);
        logView = findViewById(R.id.logText);
        spinner = findViewById(R.id.deviceSpinner);
        Button refresh = findViewById(R.id.refreshButton);
        Button connect = findViewById(R.id.connectButton);
        Button disconnect = findViewById(R.id.disconnectButton);
        Button test = findViewById(R.id.adapterTestButton);
        Button volts = findViewById(R.id.voltageButton);
        Button probe = findViewById(R.id.klineButton);
        Button copy = findViewById(R.id.copyButton);

        refresh.setOnClickListener(v -> refreshDevices());
        connect.setOnClickListener(v -> connectSelected());
        disconnect.setOnClickListener(v -> {
            elm.close();
            setStatus("Disconnected");
            setPumpStatus("Pump ECU: not verified");
            append("Bluetooth socket closed.");
        });
        test.setOnClickListener(v -> task("Adapter test", () -> {
            cmd("ATZ", 5500);
            cmd("ATE0", 2000);
            cmd("ATL0", 2000);
            cmd("ATS0", 2000);
            cmd("ATI", 2000);
            cmd("ATDP", 2000);
            append("Adapter test completed. This does NOT confirm pump ECU access.");
        }));
        volts.setOnClickListener(v -> task("OBD voltage", () -> {
            String reply = cmd("ATRV", 2500);
            String value = ElmReplies.voltage(reply);
            ui.post(() -> voltage.setText("Vehicle voltage: " + value));
            append("This is adapter supply voltage, not a direct pump power measurement.");
        }));
        probe.setOnClickListener(v -> confirmProbe());
        copy.setOnClickListener(v -> {
            ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("Croma diagnostic log", log.toString()));
            Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show();
        });
        append("Ready. Pair an ELM327 Bluetooth Classic adapter in phone settings.");
        append("Pump uses car OBD pin 12 (green A1 adapter). No verified ECU fault-code reader yet.");
        checkBluetoothPermission();
    }

    private boolean hasBtPermission() {
        return Build.VERSION.SDK_INT < 31 ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }
    private void checkBluetoothPermission() {
        if (!hasBtPermission()) requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, BLUETOOTH_REQUEST);
        else refreshDevices();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == BLUETOOTH_REQUEST && hasBtPermission()) refreshDevices();
        else if (requestCode == BLUETOOTH_REQUEST) append("Bluetooth permission denied.");
    }

    private void refreshDevices() {
        if (!hasBtPermission()) { checkBluetoothPermission(); return; }
        devices.clear();
        List<String> labels = new ArrayList<>();
        try {
            BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager.getAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                append("Enable Bluetooth in Android settings, then refresh.");
            } else {
                Set<BluetoothDevice> paired = adapter.getBondedDevices();
                for (BluetoothDevice d : paired) {
                    devices.add(d);
                    String name = d.getName();
                    labels.add((name == null ? "Unknown" : name) + " · " + d.getAddress());
                }
            }
        } catch (SecurityException exc) { append("Bluetooth permission missing."); }
        if (labels.isEmpty()) labels.add("No paired devices — pair in Android settings");
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(a);
    }
    private void connectSelected() {
        if (!hasBtPermission()) { checkBluetoothPermission(); return; }
        int index = spinner.getSelectedItemPosition();
        if (index < 0 || index >= devices.size()) { append("No Bluetooth adapter selected."); return; }
        BluetoothDevice selected = devices.get(index);
        task("Bluetooth connect", () -> {
            setStatus("Connecting to Bluetooth adapter...");
            elm.connect(selected);
            setStatus("ELM Bluetooth socket connected (pump not verified)");
            append("SPP connected. Test the adapter next.");
        });
    }

    private void confirmProbe() {
        if (!elm.connected()) { append("Connect the ELM327 adapter first."); return; }
        new AlertDialog.Builder(this)
                .setTitle("Experimental test — parked only")
                .setMessage("Requires a green A1 adapter connecting the car's OBD pin 12. " +
                        "Uses UNVERIFIED KWP2000 address 0x10. Sends a start-communication " +
                        "request and read-identification request only. It cannot be used to " +
                        "declare the pump healthy or faulty. Do not use while driving. Continue?")
                .setNegativeButton("Cancel", (d, w) -> {})
                .setPositiveButton("Try read-only probe", (d, w) -> runProbe())
                .show();
    }

    private void runProbe() {
        task("Experimental TRW K-line probe", () -> {
            setPumpStatus("Pump ECU: probing (not yet verified)");
            append("WARNING: Unverified diagnostic address and KWP initialization.");
            cmd("ATE0", 2000);
            cmd("ATL0", 2000);
            cmd("ATH1", 2000);
            cmd("ATSP5", 2000);  // ISO 14230-4 fast init over ELM K-line on interface pin 7
            cmd("ATSH8110F1", 2000); // illustrative physical header for target 0x10
            String handshake = cmd("81", 6000); // KWP start communication, not actuation
            if (ElmReplies.indicatesNoData(handshake) || handshake.isEmpty()) {
                append("No proven ECU reply. Check cable/pin 12/ignition/interface; NOT proof of pump failure.");
                setPumpStatus("Pump ECU: no confirmed reply");
                return;
            }
            cmd("ATSH8210F1", 2500);
            String identity = cmd("1A80", 5000); // KWP read ECU identifier, not actuator
            append("Raw ECU identification attempt: " + identity);
            append("Response not decoded / ECU identity not validated. Consult AlfaOBD or MultiECUScan.");
            setPumpStatus("Pump ECU: unverified response — check diagnostic log");
        });
    }

    private String cmd(String text, int timeout) throws IOException {
        append("TX " + text);
        String response = elm.send(text, timeout);
        append("RX " + (response.isEmpty() ? "(empty)" : response));
        return response;
    }

    private interface Work { void run() throws Exception; }
    private void task(String name, Work work) {
        if (busy) { append("An operation is already running. Please wait."); return; }
        busy = true;
        append("--- " + name + " ---");
        io.execute(() -> {
            try { work.run(); }
            catch (Exception exc) {
                append("ERROR: " + exc.getMessage());
                if (!elm.connected()) setStatus("Disconnected");
            } finally { busy = false; }
        });
    }
    private void setStatus(String s) { ui.post(() -> status.setText(s)); }
    private void setPumpStatus(String s) { ui.post(() -> pumpStatus.setText(s)); }
    private synchronized void append(String entry) {
        String line = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + " " + entry + "\n";
        log.append(line);
        if (log.length() > 32000) log.delete(0, log.length() - 28000);
        String value = log.toString();
        ui.post(() -> logView.setText(value));
    }
    @Override protected void onDestroy() {
        elm.close();
        io.shutdownNow();
        super.onDestroy();
    }
}
