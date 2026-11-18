package com.onekey.uninstaller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private TextView status;
    private TextView log;
    private Button btnApps;
    private Button btnPower;
    private Button btnDisguise;
    private Button btnLock;
    private volatile Boolean rootState; // null = not checked yet

    public static final String EXTRA_REVEAL = "reveal";
    private boolean realBuilt;

    // calculator decoy state
    private TextView calcDisplay;
    private StringBuilder calcCur = new StringBuilder("0");
    private double calcAcc;
    private String calcOp;
    private boolean calcStart = true, calcEvaluated;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        applyGate();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyGate();
    }

    /** Decides what to show on launch: the calculator decoy, the PIN lock, or the real app. */
    private void applyGate() {
        boolean reveal = getIntent() != null && getIntent().getBooleanExtra(EXTRA_REVEAL, false);
        boolean disguised = !"real".equals(Prefs.sp(this).getString("mask", "real"));
        if (disguised && !reveal) { buildDecoy(); return; }
        if (Prefs.isLockSet(this) && !realBuilt) { promptLock(); return; }
        buildRealUi();
    }

    private void buildRealUi() {
        if (realBuilt) return;
        realBuilt = true;
        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("OneKey Uninstaller");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("One hardware-button press makes an encrypted backup of the selected apps and uninstalls them.");
        sub.setPadding(0, dp(4), 0, dp(12));
        root.addView(sub);

        status = new TextView(this);
        status.setPadding(dp(12), dp(12), dp(12), dp(12));
        status.setBackgroundColor(Color.parseColor("#EEEEEE"));
        status.setTextColor(Color.parseColor("#212121"));
        root.addView(status);

        root.addView(header("Setup"));
        root.addView(button("1. Set backup password", v -> askNewPassword()));
        btnApps = button("2. Choose apps to delete", v -> pickApps());
        root.addView(btnApps);
        root.addView(button("3. Turn on button trigger (Accessibility)", v -> openAccessibility()));
        root.addView(button("4. Check root access", v -> checkRoot(true)));
        root.addView(button("5. Allow backup folder on phone storage", v -> askStorage()));
        root.addView(button("6. Connect Shizuku (silent delete without root)", v -> connectShizuku()));
        btnDisguise = button("", v -> pickDisguise());
        root.addView(btnDisguise);
        btnLock = button("", v -> manageLock());
        root.addView(btnLock);

        root.addView(header("Trigger"));
        root.addView(check("ARMED (buttons are active)", "armed", false));
        root.addView(check("Show warning popup with 5 second countdown and Cancel button before deleting "
                + "(turn off for instant, silent run)", "warn", true));
        root.addView(check("Volume-down: hold for 2 seconds", "vol", true));
        root.addView(check("Power button: press several times quickly", "power", false));
        btnPower = button("", v -> pickPowerCount());
        root.addView(btnPower);
        root.addView(check("Allow delete without data backup (phone without root: only the app file is "
                + "backed up, app data is LOST)", "apkOnly", false));

        root.addView(header("Actions"));
        Button run = button("RUN NOW (backup + delete selected apps)", v -> confirmRun());
        run.setTextColor(Color.parseColor("#C62828"));
        root.addView(run);
        root.addView(button("Backups: restore or delete", v -> showBackups()));

        root.addView(header("Log"));
        root.addView(button("Clear log", v -> {
            AppLog.clear(this);
            refresh();
        }));
        log = new TextView(this);
        log.setTextSize(12);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextIsSelectable(true);
        root.addView(log);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
        checkRoot(false);
        try {
            rikka.shizuku.Shizuku.addRequestPermissionResultListener(shizukuResult);
            rikka.shizuku.Shizuku.addBinderReceivedListenerSticky(shizukuBinder);
        } catch (Throwable ignored) {
        }
        refresh(); // populate the status/log now, since we may be built after onResume (unlock/reveal)
    }

    private final rikka.shizuku.Shizuku.OnRequestPermissionResultListener shizukuResult = (code, result) -> {
        AppLog.add(this, result == PackageManager.PERMISSION_GRANTED
                ? "Shizuku access allowed." : "Shizuku access was denied.");
        runOnUiThread(this::refresh);
    };

    private final rikka.shizuku.Shizuku.OnBinderReceivedListener shizukuBinder =
            () -> runOnUiThread(this::refresh);

    @Override
    protected void onDestroy() {
        try {
            rikka.shizuku.Shizuku.removeRequestPermissionResultListener(shizukuResult);
            rikka.shizuku.Shizuku.removeBinderReceivedListener(shizukuBinder);
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    private void connectShizuku() {
        if (Shz.ready()) {
            toast("Shizuku is connected");
            return;
        }
        if (!Shz.running()) {
            new AlertDialog.Builder(this).setTitle("Shizuku is not running")
                    .setMessage("Install the Shizuku app, open it and start its service (it shows how, using "
                            + "Wireless debugging). Then come back and tap this button again.\n\nShizuku must be "
                            + "started again after every phone restart.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        try {
            Shz.requestPermission();
        } catch (Throwable t) {
            AppLog.add(this, "Shizuku request failed: " + t);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!realBuilt) return; // decoy or lock screen: the real views do not exist
        AppLog.listener = () -> runOnUiThread(this::refresh);
        refresh();
    }

    @Override
    protected void onPause() {
        AppLog.listener = null;
        super.onPause();
    }

    // ------------------------------------------------------------ UI helpers

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView header(String t) {
        TextView h = new TextView(this);
        h.setText(t);
        h.setTextSize(16);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(0, dp(18), 0, dp(4));
        return h;
    }

    private Button button(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return b;
    }

    private CheckBox check(String t, final String key, boolean def) {
        CheckBox c = new CheckBox(this);
        c.setText(t);
        c.setChecked(Prefs.sp(this).getBoolean(key, def));
        c.setOnCheckedChangeListener((v, on) -> {
            Prefs.set(this, key, on);
            refresh();
        });
        return c;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private boolean accessibilityOn() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        String me = new ComponentName(this, TriggerService.class).flattenToString();
        String meShort = new ComponentName(this, TriggerService.class).flattenToShortString();
        for (String s : enabled.split(":")) {
            if (s.equalsIgnoreCase(me) || s.equalsIgnoreCase(meShort)) return true;
        }
        return false;
    }

    private void refresh() {
        if (!realBuilt || status == null) return; // only the real screen has these views
        Boolean r = rootState;
        boolean acc = accessibilityOn();
        boolean owner = Engine.isDeviceOwner(this);
        StringBuilder s = new StringBuilder();
        s.append(mark(Prefs.hasKeys(this))).append("Backup password set\n");
        s.append(mark(!Prefs.targets(this).isEmpty())).append("Apps selected: ")
                .append(Prefs.targets(this).size()).append('\n');
        s.append(mark(acc)).append("Button trigger service ").append(acc ? "ON" : "OFF").append('\n');
        s.append(mark(Prefs.armed(this))).append(Prefs.armed(this) ? "ARMED" : "Not armed").append('\n');
        if (r == null) s.append("…  Checking root\n");
        else if (r) s.append("✔  Root: yes. Full app data is backed up and restored\n");
        else s.append("✘  Root: no. Only the app file (APK) can be backed up\n");
        boolean shz = Shz.ready();
        if (r == null || !r) {
            s.append(mark(shz || owner)).append(shz ? "Shizuku connected: silent delete\n"
                    : owner ? "Device owner: silent delete\n"
                    : Shz.running() ? "Shizuku is running but not allowed yet (tap step 6)\n"
                    : "Shizuku not running: each delete must be confirmed on screen\n");
        }
        s.append("Backups: ").append(Engine.backupDir(this).getAbsolutePath());
        status.setText(s);
        btnApps.setText("2. Choose apps to delete (" + Prefs.targets(this).size() + " selected)");
        btnPower.setText("Power presses needed: " + Prefs.powerCount(this) + " (tap to change)");
        if (btnDisguise != null)
            btnDisguise.setText("7. Disguise: " + Disguise.current(this).label);
        if (btnLock != null)
            btnLock.setText("8. App lock: " + (Prefs.isLockSet(this) ? "On (PIN set)" : "Off"));
        log.setText(AppLog.read(this));
    }

    private static String mark(boolean ok) { return ok ? "✔  " : "✘  "; }

    // ------------------------------------------------------------ calculator decoy

    /** A working calculator shown instead of the real app while disguised. */
    private void buildDecoy() {
        calcCur = new StringBuilder("0");
        calcAcc = 0;
        calcOp = null;
        calcStart = true;
        calcEvaluated = false;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#1C1C1C"));
        int pad = dp(6);
        root.setPadding(pad, pad, pad, pad);

        calcDisplay = new TextView(this);
        calcDisplay.setText("0");
        calcDisplay.setTextColor(Color.WHITE);
        calcDisplay.setTextSize(56);
        calcDisplay.setSingleLine(true);
        calcDisplay.setGravity(Gravity.END | Gravity.BOTTOM);
        calcDisplay.setPadding(dp(16), dp(40), dp(20), dp(16));
        root.addView(calcDisplay,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 2.2f));

        String[][] rows = {
                {"C", "⌫", "%", "÷"},
                {"7", "8", "9", "×"},
                {"4", "5", "6", "−"},
                {"1", "2", "3", "+"},
                {"±", "0", ".", "="},
        };
        for (String[] r : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (String key : r) row.addView(calcButton(key));
            root.addView(row,
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        setContentView(root);
    }

    private Button calcButton(final String key) {
        Button b = new Button(this);
        b.setText(key);
        b.setAllCaps(false);
        b.setTextSize(22);
        b.setTextColor(Color.WHITE);
        boolean op = "÷×−+=".contains(key);
        boolean fn = "C⌫%±".contains(key);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.parseColor(op ? "#FF9500" : fn ? "#5A5A5A" : "#333333"));
        bg.setCornerRadius(dp(8));
        b.setBackground(bg);
        int m = dp(4);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(m, m, m, m);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> calcInput(key));
        return b;
    }

    private void calcInput(String k) {
        switch (k) {
            case "C":
                calcCur = new StringBuilder("0");
                calcAcc = 0; calcOp = null; calcStart = true; calcEvaluated = false;
                break;
            case "⌫": // backspace
                if (!calcStart) {
                    if (calcCur.length() > 1) calcCur.deleteCharAt(calcCur.length() - 1);
                    else calcCur = new StringBuilder("0");
                }
                break;
            case "±": // negate
                if (calcCur.length() > 0 && calcCur.charAt(0) == '-') calcCur.deleteCharAt(0);
                else if (!"0".contentEquals(calcCur)) calcCur.insert(0, '-');
                break;
            case "%":
                try { calcCur = new StringBuilder(fmt(Double.parseDouble(calcCur.toString()) / 100.0)); }
                catch (Exception ignored) {}
                break;
            case "÷": case "×": case "−": case "+":
                applyPending(); calcOp = k; calcStart = true; calcEvaluated = false;
                break;
            case "=":
                applyPending(); calcOp = null; calcStart = true; calcEvaluated = true;
                break;
            case ".":
                if (calcStart) { calcCur = new StringBuilder("0."); calcStart = false; }
                else if (calcCur.indexOf(".") < 0) calcCur.append(".");
                break;
            default: // a digit
                if (calcStart || calcEvaluated) {
                    calcCur = new StringBuilder(k); calcStart = false; calcEvaluated = false;
                } else if ("0".contentEquals(calcCur)) {
                    calcCur = new StringBuilder(k);
                } else {
                    calcCur.append(k);
                }
        }
        if (calcDisplay != null) calcDisplay.setText(calcCur.toString());
    }

    private void applyPending() {
        double cur;
        try { cur = Double.parseDouble(calcCur.toString()); } catch (Exception e) { cur = 0; }
        if (calcOp == null) {
            calcAcc = cur;
        } else switch (calcOp) {
            case "+": calcAcc += cur; break;
            case "−": calcAcc -= cur; break;
            case "×": calcAcc *= cur; break;
            case "÷": calcAcc = cur == 0 ? 0 : calcAcc / cur; break;
            default: calcAcc = cur;
        }
        calcCur = new StringBuilder(fmt(calcAcc));
    }

    private String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "0";
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    // ------------------------------------------------------------ app lock (PIN)

    private void promptLock() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int pad = dp(28);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.WHITE);

        TextView t = new TextView(this);
        t.setText("Enter PIN");
        t.setTextSize(20);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.parseColor("#212121"));
        t.setGravity(Gravity.CENTER);
        root.addView(t);

        final EditText pin = new EditText(this);
        pin.setHint("PIN");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setGravity(Gravity.CENTER);
        pin.setTextSize(22);
        root.addView(pin,
                new LinearLayout.LayoutParams(dp(200), ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView err = new TextView(this);
        err.setTextColor(Color.parseColor("#C62828"));
        err.setPadding(0, dp(8), 0, dp(8));
        root.addView(err);

        Button go = button("Unlock", null);
        go.setOnClickListener(v -> {
            if (Prefs.checkLock(this, pin.getText().toString())) {
                realBuilt = false;
                buildRealUi();
            } else {
                err.setText("Wrong PIN");
                pin.setText("");
            }
        });
        root.addView(go,
                new LinearLayout.LayoutParams(dp(200), ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private void manageLock() {
        if (!Prefs.isLockSet(this)) { setLockDialog(); return; }
        new AlertDialog.Builder(this).setTitle("App lock")
                .setMessage("A PIN is set. OneKey asks for it every time the real screen opens "
                        + "(including after Volume-up from the Calculator disguise).")
                .setPositiveButton("Change PIN", (d, w) -> setLockDialog())
                .setNeutralButton("Turn off", (d, w) -> {
                    Prefs.clearLock(this);
                    AppLog.add(this, "App lock turned off.");
                    refresh();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void setLockDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText p1 = new EditText(this);
        p1.setHint("New PIN (at least 4 digits)");
        p1.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        final EditText p2 = new EditText(this);
        p2.setHint("Repeat PIN");
        p2.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        TextView note = new TextView(this);
        note.setText("If you forget this PIN you must clear the app's data to get back in "
                + "(your backups on storage are kept).");
        box.addView(note);
        box.addView(p1);
        box.addView(p2);
        new AlertDialog.Builder(this).setTitle("Set app lock PIN").setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String a = p1.getText().toString();
                    if (a.length() < 4) { toast("PIN is too short"); return; }
                    if (!a.equals(p2.getText().toString())) { toast("PINs do not match"); return; }
                    Prefs.setLock(this, a);
                    AppLog.add(this, "App lock PIN set.");
                    refresh();
                }).show();
    }

    // ------------------------------------------------------------ setup steps

    private void askNewPassword() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText p1 = pwField("Password (at least 6 characters)");
        final EditText p2 = pwField("Repeat password");
        TextView note = new TextView(this);
        note.setText("You need this password to restore backups. It cannot be recovered if you forget it."
                + (Prefs.hasKeys(this) ? "\n\nBackups made earlier keep their old password." : ""));
        box.addView(note);
        box.addView(p1);
        box.addView(p2);
        new AlertDialog.Builder(this).setTitle("Backup password").setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    final String a = p1.getText().toString();
                    if (a.length() < 6) {
                        toast("Password is too short");
                        return;
                    }
                    if (!a.equals(p2.getText().toString())) {
                        toast("Passwords do not match");
                        return;
                    }
                    toast("Creating encryption keys…");
                    new Thread(() -> {
                        try {
                            Prefs.setKeys(this, Crypto.create(a.toCharArray()));
                            AppLog.add(this, "Backup password set.");
                        } catch (Exception e) {
                            AppLog.add(this, "Could not create keys: " + e);
                        }
                    }).start();
                }).show();
    }

    private EditText pwField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    private static final class AppItem {
        final String label, pkg;
        final android.graphics.drawable.Drawable icon;
        boolean checked;
        AppItem(String label, String pkg, android.graphics.drawable.Drawable icon) {
            this.label = label;
            this.pkg = pkg;
            this.icon = icon;
        }
    }

    private void pickApps() {
        final boolean showSystem = Prefs.showSystem(this);
        toast("Loading apps…");
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Set<String> stored = Prefs.targets(this);
            final List<AppItem> items = new ArrayList<>();
            for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                if (ai.packageName.equals(getPackageName())) continue;
                boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                if (system && !showSystem) continue;
                String label = String.valueOf(pm.getApplicationLabel(ai));
                android.graphics.drawable.Drawable icon = null;
                try { icon = pm.getApplicationIcon(ai); } catch (Throwable ignored) {}
                AppItem it = new AppItem(label, ai.packageName, icon);
                it.checked = stored.contains(ai.packageName); // drop stale (uninstalled) entries
                items.add(it);
            }
            items.sort((x, y) -> x.label.compareToIgnoreCase(y.label));
            runOnUiThread(() -> showAppDialog(items, showSystem));
        }).start();
    }

    private Set<String> checkedPkgs(List<AppItem> items) {
        Set<String> sel = new java.util.HashSet<>();
        for (AppItem it : items) if (it.checked) sel.add(it.pkg);
        return sel;
    }

    private void showAppDialog(final List<AppItem> items, final boolean showSystem) {
        if (isFinishing()) return;
        final int iconPx = dp(40);
        final BaseAdapter adapter = new BaseAdapter() {
            public int getCount() { return items.size(); }
            public Object getItem(int p) { return items.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int p, View cv, ViewGroup parent) {
                LinearLayout row;
                if (cv instanceof LinearLayout) {
                    row = (LinearLayout) cv;
                } else {
                    row = new LinearLayout(MainActivity.this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(10), dp(8), dp(10), dp(8));
                    ImageView iv = new ImageView(MainActivity.this);
                    iv.setLayoutParams(new LinearLayout.LayoutParams(iconPx, iconPx));
                    row.addView(iv);
                    LinearLayout col = new LinearLayout(MainActivity.this);
                    col.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    clp.leftMargin = dp(12);
                    clp.rightMargin = dp(8);
                    col.setLayoutParams(clp);
                    TextView name = new TextView(MainActivity.this);
                    name.setTextSize(15);
                    name.setTextColor(Color.parseColor("#212121"));
                    TextView pkg = new TextView(MainActivity.this);
                    pkg.setTextSize(11);
                    pkg.setTextColor(Color.parseColor("#757575"));
                    col.addView(name);
                    col.addView(pkg);
                    row.addView(col);
                    CheckBox cb = new CheckBox(MainActivity.this);
                    cb.setClickable(false);
                    cb.setFocusable(false);
                    row.addView(cb);
                    row.setTag(new View[]{iv, name, pkg, cb});
                }
                View[] h = (View[]) row.getTag();
                AppItem it = items.get(p);
                ((ImageView) h[0]).setImageDrawable(it.icon);
                ((TextView) h[1]).setText(it.label);
                ((TextView) h[2]).setText(it.pkg);
                ((CheckBox) h[3]).setChecked(it.checked);
                return row;
            }
        };
        final ListView lv = new ListView(this);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((parent, view, pos, id) -> {
            items.get(pos).checked = !items.get(pos).checked;
            adapter.notifyDataSetChanged();
        });
        new AlertDialog.Builder(this).setTitle("Apps to delete on trigger").setView(lv)
                .setPositiveButton("Save", (d, w) -> {
                    Prefs.setTargets(this, checkedPkgs(items));
                    refresh();
                })
                .setNeutralButton(showSystem ? "Hide system apps" : "Show system apps", (d, w) -> {
                    Prefs.setTargets(this, checkedPkgs(items));
                    Prefs.set(this, "showSystem", !showSystem);
                    pickApps();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void openAccessibility() {
        new AlertDialog.Builder(this).setTitle("Button trigger")
                .setMessage("On the next screen open \"OneKey Uninstaller button trigger\" (under Installed / "
                        + "Downloaded apps) and switch it on.\n\nIf Android says \"Restricted setting\": open "
                        + "Settings > Apps > OneKey Uninstaller, tap the ⋮ menu at the top right, choose "
                        + "\"Allow restricted settings\", then come back and try again.")
                .setPositiveButton("Open settings", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (Exception e) {
                        toast("Open Settings > Accessibility manually");
                    }
                })
                .setNeutralButton("App info", (d, w) -> startActivity(new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))))
                .show();
    }

    private void checkRoot(final boolean verbose) {
        if (verbose) toast("Checking root… approve the request if your root manager asks");
        new Thread(() -> {
            final boolean r = Shell.hasRoot(30);
            rootState = r;
            if (verbose) AppLog.add(this, r ? "Root access granted." : "No root access on this phone.");
            runOnUiThread(this::refresh);
        }).start();
    }

    private void askStorage() {
        if (Engine.hasAllFilesAccess(this)) {
            toast("Already allowed. Backups go to " + Engine.backupDir(this));
            return;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    private void pickDisguise() {
        final Disguise.Mask[] masks = Disguise.MASKS;
        CharSequence[] names = new CharSequence[masks.length];
        String cur = Disguise.current(this).key;
        int sel = 0;
        for (int i = 0; i < masks.length; i++) {
            names[i] = masks[i].label + (masks[i].key.equals(cur) ? "  (current)" : "");
            if (masks[i].key.equals(cur)) sel = i;
        }
        final int[] pick = {sel};
        new AlertDialog.Builder(this).setTitle("Disguise: app name and icon")
                .setSingleChoiceItems(names, sel, (d, w) -> pick[0] = w)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (d, w) -> applyDisguise(masks[pick[0]]))
                .show();
    }

    private void applyDisguise(final Disguise.Mask m) {
        if (!"calc".equals(m.key)) { doDisguise(m); return; }
        final boolean acc = accessibilityOn();
        new AlertDialog.Builder(this).setTitle("Disguise as Calculator")
                .setMessage("Your app will look and work like a real Calculator. To get back to OneKey, "
                        + "HOLD the VOLUME-UP button for 2 seconds (works even on the lock screen).\n\n"
                        + (acc ? "The button trigger is ON — keep it on, it is the only way back in."
                               : "⚠ The button trigger is OFF. Turn it on first (step 3), otherwise you "
                               + "will NOT be able to reopen the app after disguising it."))
                .setNegativeButton("Cancel", null)
                .setPositiveButton(acc ? "Apply" : "Apply anyway", (d, w) -> doDisguise(m))
                .show();
    }

    private void doDisguise(final Disguise.Mask m) {
        toast("Applying… the icon may take a few seconds to change");
        new Thread(() -> {
            try {
                Disguise.apply(this, m.key);
            } catch (Throwable t) {
                AppLog.add(this, "Disguise failed: " + t);
            }
            runOnUiThread(this::refresh);
        }).start();
    }

    private void pickPowerCount() {
        final String[] opts = {"3", "4", "5", "6", "7", "8"};
        new AlertDialog.Builder(this).setTitle("Power presses needed")
                .setItems(opts, (d, w) -> {
                    Prefs.set(this, "powerCount", Integer.parseInt(opts[w]));
                    refresh();
                }).show();
    }

    // ------------------------------------------------------------ actions

    private void confirmRun() {
        if (Prefs.targets(this).isEmpty()) {
            toast("Choose apps first");
            return;
        }
        if (!Prefs.hasKeys(this)) {
            toast("Set a backup password first");
            return;
        }
        new AlertDialog.Builder(this).setTitle("Run now?")
                .setMessage("Back up and delete " + Prefs.targets(this).size() + " selected app(s) now?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Run", (d, w) -> Engine.runPanic(this, "Run now button")).show();
    }

    private void showBackups() {
        final List<File> files = Engine.listBackups(this);
        if (files.isEmpty()) {
            toast("No backups yet");
            return;
        }
        CharSequence[] names = new CharSequence[files.size()];
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        for (int i = 0; i < files.size(); i++) {
            File f = files.get(i);
            String n = f.getName();
            int us = n.lastIndexOf('_');
            names[i] = (us > 0 ? n.substring(0, us) : n) + "\n" + df.format(new Date(f.lastModified()))
                    + "  •  " + size(f.length());
        }
        new AlertDialog.Builder(this).setTitle("Backups")
                .setItems(names, (d, w) -> backupMenu(files.get(w)))
                .setNegativeButton("Close", null).show();
    }

    private static String size(long b) {
        if (b > 1024 * 1024) return String.format(Locale.US, "%.1f MB", b / 1048576.0);
        return (b / 1024) + " KB";
    }

    private void backupMenu(final File f) {
        new AlertDialog.Builder(this).setTitle(f.getName())
                .setItems(new String[]{"Restore", "Delete this backup"}, (d, w) -> {
                    if (w == 0) askRestore(f);
                    else new AlertDialog.Builder(this).setMessage("Delete this backup permanently?")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete", (d2, w2) -> {
                                AppLog.add(this, f.delete() ? "Deleted backup " + f.getName()
                                        : "Could not delete " + f.getName());
                            }).show();
                }).show();
    }

    private void askRestore(final File f) {
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText p = pwField("Backup password");
        box.addView(p, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this).setTitle("Restore").setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restore", (d, w) -> {
                    final char[] pw = p.getText().toString().toCharArray();
                    toast("Restoring… see the log below");
                    new Thread(() -> {
                        try {
                            JSONObject meta = Engine.readMeta(f, pw);
                            AppLog.add(this, "Backup of " + meta.optString("label") + " "
                                    + meta.optString("versionName")
                                    + (meta.optBoolean("hasData") ? " (with app data)" : " (app file only)"));
                            if (!Shz.ready() && Boolean.FALSE.equals(rootState)
                                    && !getPackageManager().canRequestPackageInstalls()) {
                                AppLog.add(this, "Allow \"Install unknown apps\" for OneKey Uninstaller, then "
                                        + "tap Restore again.");
                                runOnUiThread(() -> startActivity(new Intent(
                                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:" + getPackageName()))));
                                return;
                            }
                            Engine.restoreAsync(this, f, pw);
                        } catch (Crypto.BadPasswordException e) {
                            AppLog.add(this, "Wrong password.");
                        } catch (Exception e) {
                            AppLog.add(this, "Cannot read backup: " + e);
                        }
                    }).start();
                }).show();
    }
}
