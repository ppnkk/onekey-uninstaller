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
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
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
    private volatile Boolean rootState; // null = not checked yet

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
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
        log.setText(AppLog.read(this));
    }

    private static String mark(boolean ok) { return ok ? "✔  " : "✘  "; }

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

    private void pickApps() {
        final boolean showSystem = Prefs.showSystem(this);
        toast("Loading apps…");
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            final List<String[]> apps = new ArrayList<>(); // {label, package}
            for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                if (ai.packageName.equals(getPackageName())) continue;
                boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                if (system && !showSystem) continue;
                apps.add(new String[]{String.valueOf(pm.getApplicationLabel(ai)), ai.packageName});
            }
            apps.sort((x, y) -> x[0].compareToIgnoreCase(y[0]));
            runOnUiThread(() -> showAppDialog(apps, showSystem));
        }).start();
    }

    private void showAppDialog(final List<String[]> apps, final boolean showSystem) {
        if (isFinishing()) return;
        // Only apps that are shown (installed) stay selected; stale entries are dropped.
        final Set<String> sel = new java.util.HashSet<>();
        Set<String> stored = Prefs.targets(this);
        for (String[] a : apps) if (stored.contains(a[1])) sel.add(a[1]);
        CharSequence[] names = new CharSequence[apps.size()];
        boolean[] checked = new boolean[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            names[i] = apps.get(i)[0] + "\n" + apps.get(i)[1];
            checked[i] = sel.contains(apps.get(i)[1]);
        }
        new AlertDialog.Builder(this).setTitle("Apps to delete on trigger")
                .setMultiChoiceItems(names, checked, (d, which, on) -> {
                    if (on) sel.add(apps.get(which)[1]);
                    else sel.remove(apps.get(which)[1]);
                })
                .setPositiveButton("Save", (d, w) -> {
                    Prefs.setTargets(this, sel);
                    refresh();
                })
                .setNeutralButton(showSystem ? "Hide system apps" : "Show system apps", (d, w) -> {
                    Prefs.setTargets(this, sel);
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
        if (m.alias == null) {
            new AlertDialog.Builder(this).setTitle("Hide the app icon")
                    .setMessage("To reopen the app after hiding, HOLD the VOLUME-UP button for 2 seconds "
                            + "(works anywhere, even on the lock screen). Keep the button trigger ON.\n\n"
                            + "On Samsung (One UI) a disabled icon cannot be removed by any app — a greyed "
                            + "tile that only opens \"App info\" stays in the app list. The reliable way on "
                            + "Samsung is Settings › Home screen › Hide apps: it keeps the app running "
                            + "so volume-up still reopens it. Or just pick a Disguise (e.g. Calculator) instead "
                            + "— it shows one clean, ordinary-looking icon.")
                    .setPositiveButton("Samsung Hide apps", (d, w) -> openSamsungHideApps())
                    .setNeutralButton("Hide anyway", (d, w) -> doDisguise(m))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            doDisguise(m);
        }
    }

    /** Opens Samsung's "Hide apps" screen (One UI home-screen settings), with fallbacks. */
    private void openSamsungHideApps() {
        try {
            Intent i = new Intent();
            i.setClassName("com.sec.android.app.launcher",
                    "com.android.homescreen.settings.HomeScreenSettingsActivity");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            toast("Tap \"Hide apps\", then select OneKey (or its disguise).");
            return;
        } catch (Throwable ignored) {
        }
        // Fallback: this app's details page, from where the user can reach Settings.
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            toast("Open Settings › Home screen › Hide apps on this phone.");
        } catch (Throwable ignored) {
            toast("Open Settings › Home screen › Hide apps manually.");
        }
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
