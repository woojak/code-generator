package com.labeltools.t8306test;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {

    private static final String DEFAULT_IP = "10.20.140.50";
    private static final int DEFAULT_PORT = 9100;
    private static final int CONNECT_TIMEOUT_MS = 1500;
    private static final int SCAN_TIMEOUT_MS = 250;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private EditText ipInput;
    private EditText portInput;
    private TextView statusText;
    private TextView logText;
    private Button connectButton;
    private Button printButton;
    private Button scanButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        appendLog("Ready. No data has been sent to the printer.");
        showLocalNetworkInfo();
    }

    private View buildUi() {
        int pad = dp(18);
        int gap = dp(10);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Printronix T8306\nPrinter Test");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Diagnostic build for RAW TCP / port 9100\nDefault printer: P_100077 / 10.20.140.50");
        subtitle.setTextSize(15);
        subtitle.setPadding(0, gap, 0, gap * 2);
        root.addView(subtitle);

        root.addView(label("Printer IP"));
        ipInput = new EditText(this);
        ipInput.setSingleLine(true);
        ipInput.setText(DEFAULT_IP);
        ipInput.setInputType(InputType.TYPE_CLASS_PHONE);
        root.addView(ipInput, matchWrap());

        root.addView(label("Port"));
        portInput = new EditText(this);
        portInput.setSingleLine(true);
        portInput.setText(String.valueOf(DEFAULT_PORT));
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(portInput, matchWrap());

        statusText = new TextView(this);
        statusText.setText("STATUS: not tested");
        statusText.setTextSize(18);
        statusText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusText.setPadding(0, gap * 2, 0, gap);
        root.addView(statusText);

        connectButton = button("TEST CONNECTION");
        connectButton.setOnClickListener(v -> testConnection());
        root.addView(connectButton, matchWrap());

        printButton = button("PRINT PGL TEST LABEL");
        printButton.setOnClickListener(v -> confirmPrintTest());
        root.addView(printButton, marginTop(matchWrap(), gap));

        scanButton = button("FIND PORT 9100 PRINTERS (/24)");
        scanButton.setOnClickListener(v -> confirmScan());
        root.addView(scanButton, marginTop(matchWrap(), gap));

        Button copyButton = button("COPY LOG");
        copyButton.setOnClickListener(v -> copyLog());
        root.addView(copyButton, marginTop(matchWrap(), gap));

        TextView warning = new TextView(this);
        warning.setText("The network scan is never started automatically. Use it only when you are allowed to scan the local company subnet. It checks TCP port 9100 only.");
        warning.setTextSize(13);
        warning.setPadding(0, gap * 2, 0, gap);
        root.addView(warning);

        logText = new TextView(this);
        logText.setTextSize(13);
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setTextIsSelectable(true);
        logText.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(logText, matchWrap());

        return scrollView;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams marginTop(LinearLayout.LayoutParams p, int top) {
        p.topMargin = top;
        return p;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(8), 0, 0);
        return view;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setAllCaps(false);
        return b;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String host() {
        return ipInput.getText().toString().trim();
    }

    private int port() {
        try {
            return Integer.parseInt(portInput.getText().toString().trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean validateTarget() {
        if (host().isEmpty()) {
            toast("Enter printer IP.");
            return false;
        }
        if (port() < 1 || port() > 65535) {
            toast("Port must be between 1 and 65535.");
            return false;
        }
        return true;
    }

    private void testConnection() {
        if (!validateTarget()) return;
        setBusy(true);
        String targetHost = host();
        int targetPort = port();
        statusText.setText("STATUS: testing...");
        appendLog("Connecting to " + targetHost + ":" + targetPort + " ...");

        ioExecutor.execute(() -> {
            try (Socket socket = new Socket()) {
                long start = System.currentTimeMillis();
                socket.connect(new InetSocketAddress(targetHost, targetPort), CONNECT_TIMEOUT_MS);
                long elapsed = System.currentTimeMillis() - start;
                runOnUiThread(() -> {
                    statusText.setText("STATUS: ONLINE ✓");
                    appendLog("Connected successfully in " + elapsed + " ms. No print data sent.");
                    setBusy(false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusText.setText("STATUS: UNREACHABLE ✗");
                    appendLog("Connection failed: " + readableError(e));
                    setBusy(false);
                });
            }
        });
    }

    private void confirmPrintTest() {
        if (!validateTarget()) return;
        new AlertDialog.Builder(this)
                .setTitle("Print test label?")
                .setMessage("This will send a small PGL print job to " + host() + ":" + port() + ". It does not change printer configuration.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("PRINT", (dialog, which) -> printPglTest())
                .show();
    }

    private void printPglTest() {
        setBusy(true);
        String targetHost = host();
        int targetPort = port();
        appendLog("Sending PGL test label to " + targetHost + ":" + targetPort + " ...");

        ioExecutor.execute(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(targetHost, targetPort), CONNECT_TIMEOUT_MS);
                socket.setSoTimeout(2000);

                OutputStream out = socket.getOutputStream();
                String pgl = buildPglTestJob(targetHost);
                byte[] bytes = pgl.getBytes(StandardCharsets.US_ASCII);
                out.write(bytes);
                out.flush();
                socket.shutdownOutput();

                runOnUiThread(() -> {
                    statusText.setText("STATUS: PRINT JOB SENT ✓");
                    appendLog("PGL test job sent: " + bytes.length + " bytes.");
                    appendLog("Check the T8306 output tray. If nothing prints, copy this log and keep the printer panel photo.");
                    setBusy(false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusText.setText("STATUS: PRINT FAILED ✗");
                    appendLog("Print failed: " + readableError(e));
                    setBusy(false);
                });
            }
        });
    }

    private String buildPglTestJob(String targetHost) {
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        // PGL syntax follows Printronix's documented CREATE / ALPHA / EXECUTE form structure.
        // SFCC is the factory-default '~' visible in the PGL documentation.
        return "~CREATE;ANDROIDTEST\r\n"
                + "ALPHA\r\n"
                + "10;10;3;3;$42 LABEL TEST$\r\n"
                + "18;10;2;2;$PRINTRONIX T8306$\r\n"
                + "25;10;2;2;$IP " + sanitizePgl(targetHost) + "$\r\n"
                + "32;10;2;2;$" + sanitizePgl(stamp) + "$\r\n"
                + "39;10;2;2;$RAW TCP 9100 OK$\r\n"
                + "STOP\r\n"
                + "END\r\n"
                + "~EXECUTE;ANDROIDTEST;1\r\n";
    }

    private String sanitizePgl(String text) {
        return text.replace("$", "").replace("~", "").replace("\r", " ").replace("\n", " ");
    }

    private void confirmScan() {
        String localIp = findLocalIpv4();
        if (localIp == null) {
            toast("Could not determine the phone's local IPv4 address.");
            appendLog("Scan unavailable: no non-loopback IPv4 found.");
            return;
        }

        String prefix = subnetPrefix(localIp);
        new AlertDialog.Builder(this)
                .setTitle("Scan local subnet?")
                .setMessage("Phone IP: " + localIp + "\nScan range: " + prefix + "1–254\nPort: 9100 only\n\nStart only if this network scan is allowed at your workplace.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("SCAN", (dialog, which) -> scanSubnet(prefix))
                .show();
    }

    private void scanSubnet(String prefix) {
        setBusy(true);
        statusText.setText("STATUS: scanning port 9100...");
        appendLog("Scanning " + prefix + "1-254 on TCP/9100 ...");

        ioExecutor.execute(() -> {
            ExecutorService pool = Executors.newFixedThreadPool(32);
            List<String> found = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger completed = new AtomicInteger(0);

            for (int i = 1; i <= 254; i++) {
                final String candidate = prefix + i;
                pool.submit(() -> {
                    try (Socket s = new Socket()) {
                        s.connect(new InetSocketAddress(candidate, DEFAULT_PORT), SCAN_TIMEOUT_MS);
                        found.add(candidate);
                    } catch (Exception ignored) {
                    } finally {
                        completed.incrementAndGet();
                    }
                });
            }

            pool.shutdown();
            try {
                pool.awaitTermination(25, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            Collections.sort(found, (a, b) -> Integer.compare(lastOctet(a), lastOctet(b)));
            runOnUiThread(() -> {
                if (found.isEmpty()) {
                    statusText.setText("STATUS: no TCP/9100 printers found");
                    appendLog("Scan finished. No hosts accepted TCP/9100.");
                } else {
                    statusText.setText("STATUS: found " + found.size() + " host(s) ✓");
                    appendLog("Scan finished. TCP/9100 open on: " + String.join(", ", found));
                    if (found.size() == 1) {
                        ipInput.setText(found.get(0));
                        appendLog("Single result selected automatically: " + found.get(0));
                    } else {
                        showFoundHosts(found);
                    }
                }
                setBusy(false);
            });
        });
    }

    private void showFoundHosts(List<String> hosts) {
        String[] items = hosts.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("TCP/9100 hosts")
                .setItems(items, (dialog, which) -> {
                    ipInput.setText(items[which]);
                    appendLog("Selected: " + items[which]);
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private int lastOctet(String ip) {
        try {
            String[] parts = ip.split("\\.");
            return Integer.parseInt(parts[3]);
        } catch (Exception e) {
            return 999;
        }
    }

    private String findLocalIpv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface nif = interfaces.nextElement();
                if (!nif.isUp() || nif.isLoopback()) continue;
                Enumeration<InetAddress> addresses = nif.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String subnetPrefix(String ip) {
        int p = ip.lastIndexOf('.');
        return p > 0 ? ip.substring(0, p + 1) : ip + ".";
    }

    private void showLocalNetworkInfo() {
        ioExecutor.execute(() -> {
            String ip = findLocalIpv4();
            runOnUiThread(() -> {
                if (ip != null) {
                    appendLog("Phone local IPv4: " + ip + " (scanner uses " + subnetPrefix(ip) + "0/24)");
                } else {
                    appendLog("Phone local IPv4: not detected yet.");
                }
            });
        });
    }

    private String readableError(Exception e) {
        String msg = e.getMessage();
        return e.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
    }

    private void setBusy(boolean busy) {
        connectButton.setEnabled(!busy);
        printButton.setEnabled(!busy);
        scanButton.setEnabled(!busy);
    }

    private void appendLog(String text) {
        if (logText == null) return;
        String stamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        String old = logText.getText().toString();
        logText.setText(old + (old.isEmpty() ? "" : "\n") + "[" + stamp + "] " + text);
    }

    private void copyLog() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("T8306 Printer Test log", logText.getText()));
        toast("Log copied.");
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        super.onDestroy();
    }
}
