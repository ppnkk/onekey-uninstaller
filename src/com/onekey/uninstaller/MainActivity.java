package com.onekey.uninstaller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
    private TextView log;
    private volatile Boolean rootState; // null = not checked yet

    // tabbed main screen
    private android.widget.FrameLayout pages;
    private final View[] pageViews = new View[4];
    private final LinearLayout[] navItems = new LinearLayout[4];
    private boolean syncing; // true while refresh() sets switches, so listeners ignore it

    /** One tappable line in a card: icon badge, title, subtitle, status pill, chevron. */
    private static final class Row {
        View view;
        android.widget.FrameLayout badge;
        TextView title, sub, pill;
    }

    // home
    private LinearLayout hero;
    private TextView heroTitle, heroSub, heroProgress;
    private android.widget.Switch heroSwitch;
    private Row rPwd, rApps, rAcc, rMode, rApk;
    private View apkDiv;
    private Button btnBackups;
    // setup
    private Row sPwd, sApps, sAcc, sStorage, sRoot, sShz, sDisguise, sLock;
    // trigger
    private android.widget.Switch swVol, swPower, swWarn, swApk;
    private Row rPowerCount;

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
        styleWindow();
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Ui.BG);

        pages = new android.widget.FrameLayout(this);
        shell.addView(pages, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(dp(8));
        nav.setPadding(0, dp(6), 0, dp(6));
        navItems[0] = navItem(0, R.drawable.ic_home, "Home");
        navItems[1] = navItem(1, R.drawable.ic_checklist, "Setup");
        navItems[2] = navItem(2, R.drawable.ic_bolt, "Trigger");
        navItems[3] = navItem(3, R.drawable.ic_history, "Log");
        for (LinearLayout it : navItems) nav.addView(it, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        shell.addView(nav, Ui.mw());

        buildHome();
        buildSetup();
        buildTrigger();
        buildLog();
        setContentView(shell);
        showTab(0);
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

    /** Light status/navigation bars that match the page background. */
    @SuppressWarnings("deprecation")
    private void styleWindow() {
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    /** A scrollable page with a big title; returns the body to add cards to. */
    private LinearLayout page(int index, String title, String subtitle) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(20), dp(16), dp(24));
        TextView t = Ui.text(this, title, 26, Ui.TEXT, true);
        body.addView(t);
        TextView s = Ui.text(this, subtitle, 14, Ui.SUB, false);
        s.setPadding(0, dp(4), 0, dp(18));
        body.addView(s);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(body);
        pageViews[index] = sv;
        pages.addView(sv);
        return body;
    }

    private LinearLayout navItem(final int index, int iconRes, String label) {
        LinearLayout it = new LinearLayout(this);
        it.setOrientation(LinearLayout.VERTICAL);
        it.setGravity(Gravity.CENTER_HORIZONTAL);
        it.setPadding(0, dp(4), 0, dp(4));
        android.widget.FrameLayout ind = new android.widget.FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setImageResource(iconRes);
        ind.addView(iv, new android.widget.FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        it.addView(ind, new LinearLayout.LayoutParams(dp(60), dp(32)));
        TextView tv = Ui.text(this, label, 12, Ui.SUB, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(4), 0, 0);
        it.addView(tv);
        it.setBackground(Ui.ripple(null, null));
        it.setOnClickListener(v -> showTab(index));
        return it;
    }

    private void showTab(int index) {
        for (int k = 0; k < pageViews.length; k++) {
            boolean sel = k == index;
            pageViews[k].setVisibility(sel ? View.VISIBLE : View.GONE);
            android.widget.FrameLayout ind = (android.widget.FrameLayout) navItems[k].getChildAt(0);
            ind.setBackground(sel ? Ui.round(Ui.ACCENT_SOFT, dp(16)) : null);
            ((ImageView) ind.getChildAt(0)).setColorFilter(sel ? Ui.ACCENT : Ui.SUB);
            ((TextView) navItems[k].getChildAt(1)).setTextColor(sel ? Ui.ACCENT : Ui.SUB);
        }
    }

    private Row row(int iconRes, String title, View.OnClickListener l) {
        Row r = new Row();
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(12), dp(12), dp(10), dp(12));
        v.setMinimumHeight(dp(64));
        r.badge = Ui.badge(this, iconRes, Ui.ACCENT, Ui.ACCENT_SOFT, 40);
        v.addView(r.badge);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        clp.leftMargin = dp(14);
        clp.rightMargin = dp(8);
        r.title = Ui.text(this, title, 15.5f, Ui.TEXT, true);
        r.sub = Ui.text(this, "", 13, Ui.SUB, false);
        col.addView(r.title);
        col.addView(r.sub);
        v.addView(col, clp);
        r.pill = Ui.pill(this);
        v.addView(r.pill);
        if (l != null) {
            ImageView ch = Ui.icon(this, R.drawable.ic_chevron, 0xFFB0B5BF, 20);
            ((LinearLayout.LayoutParams) ch.getLayoutParams()).leftMargin = dp(4);
            v.addView(ch);
            v.setBackground(Ui.ripple(null, Ui.round(Color.WHITE, dp(14))));
            v.setOnClickListener(l);
        }
        r.view = v;
        return r;
    }

    private void setRow(Row r, int state, String sub, String pill) {
        r.sub.setText(sub);
        Ui.setPill(r.pill, state, pill);
        if (state == Ui.INFO) Ui.tintBadge(r.badge, Ui.ACCENT, Ui.ACCENT_SOFT);
        else Ui.tintBadge(r.badge, Ui.color(state), Ui.soft(state));
    }

    /** Adds rows to a card with thin dividers between them. */
    private LinearLayout cardOf(LinearLayout parent, View... rows) {
        LinearLayout c = Ui.card(this);
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) c.addView(Ui.divider(this, 66));
            c.addView(rows[i]);
        }
        parent.addView(c);
        return c;
    }

    private android.widget.Switch switchRow(LinearLayout card, int iconRes, String title, String sub,
                                            final String key, boolean def) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(12), dp(12), dp(12), dp(12));
        v.setMinimumHeight(dp(64));
        v.addView(Ui.badge(this, iconRes, Ui.ACCENT, Ui.ACCENT_SOFT, 40));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        clp.leftMargin = dp(14);
        clp.rightMargin = dp(8);
        col.addView(Ui.text(this, title, 15.5f, Ui.TEXT, true));
        col.addView(Ui.text(this, sub, 13, Ui.SUB, false));
        v.addView(col, clp);
        final android.widget.Switch s = new android.widget.Switch(this);
        Ui.styleSwitch(s, Ui.ACCENT, 0x803F51B5);
        s.setChecked(Prefs.sp(this).getBoolean(key, def));
        s.setOnCheckedChangeListener((b, on) -> {
            if (syncing) return;
            Prefs.set(this, key, on);
            refresh();
        });
        v.addView(s);
        v.setBackground(Ui.ripple(null, Ui.round(Color.WHITE, dp(14))));
        v.setOnClickListener(x -> s.toggle());
        if (card.getChildCount() > 0) card.addView(Ui.divider(this, 66));
        card.addView(v);
        return s;
    }

    // ------------------------------------------------------------ pages

    private void buildHome() {
        LinearLayout b = page(0, "OneKey Uninstaller", "One button press: encrypted backup, then delete.");

        hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(20), dp(16), dp(18));
        hero.setElevation(dp(2));
        LinearLayout.LayoutParams hlp = Ui.mw();
        hlp.bottomMargin = dp(20);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.badge(this, R.drawable.ic_shield, Color.WHITE, 0x33FFFFFF, 52));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, Ui.WRAP, 1f);
        clp.leftMargin = dp(14);
        clp.rightMargin = dp(8);
        heroTitle = Ui.text(this, "", 22, Color.WHITE, true);
        heroSub = Ui.text(this, "", 13.5f, 0xE6FFFFFF, false);
        col.addView(heroTitle);
        col.addView(heroSub);
        top.addView(col, clp);
        heroSwitch = new android.widget.Switch(this);
        Ui.styleSwitch(heroSwitch, Color.WHITE, 0x99FFFFFF);
        heroSwitch.setOnCheckedChangeListener((x, on) -> {
            if (syncing) return;
            Prefs.set(this, "armed", on);
            refresh();
        });
        top.addView(heroSwitch);
        hero.addView(top);
        heroProgress = Ui.text(this, "", 13, Color.WHITE, true);
        heroProgress.setPadding(dp(12), dp(5), dp(12), dp(5));
        heroProgress.setBackground(Ui.round(0x33FFFFFF, dp(999)));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP);
        plp.topMargin = dp(16);
        hero.addView(heroProgress, plp);
        b.addView(hero, hlp);

        b.addView(Ui.label(this, "Readiness"));
        rPwd = row(R.drawable.ic_key, "Backup password", v -> askNewPassword());
        rApps = row(R.drawable.ic_apps, "Apps to delete", v -> pickApps());
        rAcc = row(R.drawable.ic_bolt, "Button trigger", v -> openAccessibility());
        rMode = row(R.drawable.ic_delete, "Delete mode", v -> showTab(1));
        rApk = row(R.drawable.ic_warning, "Delete without data backup", v -> showTab(2));
        LinearLayout c = cardOf(b, rPwd.view, rApps.view, rAcc.view, rMode.view);
        apkDiv = Ui.divider(this, 66);
        c.addView(apkDiv);
        c.addView(rApk.view);

        b.addView(Ui.label(this, "Actions"));
        Button run = Ui.filled(this, "Run now: back up and delete", Ui.RED);
        run.setOnClickListener(v -> confirmRun());
        LinearLayout.LayoutParams rlp = Ui.mw();
        rlp.bottomMargin = dp(10);
        b.addView(run, rlp);
        btnBackups = Ui.outline(this, "Backups", Ui.ACCENT);
        btnBackups.setOnClickListener(v -> showBackups());
        b.addView(btnBackups, Ui.mw());
    }

    private void buildSetup() {
        LinearLayout b = page(1, "Setup", "Do these once. Tap any step to change it.");
        b.addView(Ui.label(this, "Required"));
        sPwd = row(R.drawable.ic_key, "Backup password", v -> askNewPassword());
        sApps = row(R.drawable.ic_apps, "Apps to delete", v -> pickApps());
        sAcc = row(R.drawable.ic_bolt, "Button trigger service", v -> openAccessibility());
        sStorage = row(R.drawable.ic_folder, "Backup folder", v -> askStorage());
        cardOf(b, sPwd.view, sApps.view, sAcc.view, sStorage.view);

        b.addView(Ui.label(this, "Delete mode"));
        sRoot = row(R.drawable.ic_terminal, "Root access", v -> checkRoot(true));
        sShz = row(R.drawable.ic_link, "Shizuku", v -> connectShizuku());
        cardOf(b, sRoot.view, sShz.view);

        b.addView(Ui.label(this, "Privacy"));
        sDisguise = row(R.drawable.ic_eye, "Disguise", v -> pickDisguise());
        sLock = row(R.drawable.ic_lock, "App lock (PIN)", v -> manageLock());
        cardOf(b, sDisguise.view, sLock.view);
    }

    private void buildTrigger() {
        LinearLayout b = page(2, "Trigger", "Choose what starts a backup-and-delete run.");
        b.addView(Ui.label(this, "Buttons"));
        LinearLayout c1 = Ui.card(this);
        swVol = switchRow(c1, R.drawable.ic_volume, "Volume-down, hold 2 s", "Works on the lock screen too",
                "vol", true);
        swPower = switchRow(c1, R.drawable.ic_power, "Power button presses", "Press it quickly several times",
                "power", false);
        rPowerCount = row(R.drawable.ic_tune, "Presses needed", v -> pickPowerCount());
        c1.addView(Ui.divider(this, 66));
        c1.addView(rPowerCount.view);
        b.addView(c1);

        b.addView(Ui.label(this, "Safety"));
        LinearLayout c2 = Ui.card(this);
        swWarn = switchRow(c2, R.drawable.ic_warning, "Warning countdown",
                "5-second popup with Cancel before deleting", "warn", true);
        swApk = switchRow(c2, R.drawable.ic_delete, "Delete without data backup",
                "Without root only the app file is saved; its data is lost", "apkOnly", false);
        b.addView(c2);

        b.addView(Ui.label(this, "Open the app"));
        Row info = row(R.drawable.ic_volume, "Volume-up, hold 2 s", null);
        info.sub.setText("Opens OneKey from anywhere, even while disguised. In the Calculator you can also hold \"=\" for 2 s.");
        info.pill.setVisibility(View.GONE);
        cardOf(b, info.view);
    }

    private void buildLog() {
        LinearLayout b = page(3, "Activity log", "What OneKey has done recently.");
        LinearLayout c = Ui.card(this);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(12), dp(6), dp(4), 0);
        head.addView(Ui.text(this, "Recent", 15.5f, Ui.TEXT, true),
                new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        Button clear = Ui.outline(this, "Clear", Ui.ACCENT);
        clear.setMinHeight(dp(40));
        clear.setMinimumHeight(dp(40));
        clear.setOnClickListener(v -> {
            AppLog.clear(this);
            refresh();
        });
        head.addView(clear, new LinearLayout.LayoutParams(Ui.WRAP, dp(40)));
        c.addView(head);
        log = new TextView(this);
        log.setTextSize(12);
        log.setTextSize(14);
        log.setTextColor(Ui.TEXT);
        log.setLineSpacing(0, 1.1f);
        log.setTextIsSelectable(true);
        log.setPadding(dp(12), dp(10), dp(12), dp(12));
        c.addView(log);
        b.addView(c);
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
        if (!realBuilt || log == null) return; // only the real screen has these views
        syncing = true;
        try {
            Boolean r = rootState;
            boolean rooted = Boolean.TRUE.equals(r);
            boolean acc = accessibilityOn();
            boolean owner = Engine.isDeviceOwner(this);
            boolean shz = Shz.ready();
            boolean keys = Prefs.hasKeys(this);
            int n = Prefs.targets(this).size();
            boolean storage = Engine.hasAllFilesAccess(this);
            boolean armed = Prefs.armed(this);
            boolean silent = rooted || shz || owner;
            boolean apk = Prefs.sp(this).getBoolean("apkOnly", false);
            boolean canDelete = rooted || apk;
            String nApps = n + (n == 1 ? " app" : " apps");

            // --- home: readiness
            setRow(rPwd, keys ? Ui.OK : Ui.TODO,
                    keys ? "Set. Backups are encrypted" : "Needed to encrypt backups", keys ? "Ready" : "To do");
            setRow(rApps, n > 0 ? Ui.OK : Ui.TODO,
                    n > 0 ? nApps + " selected" : "Pick the apps to delete", n > 0 ? "Ready" : "To do");
            setRow(rAcc, acc ? Ui.OK : Ui.TODO,
                    acc ? "Service is running" : "Turn on in Accessibility settings", acc ? "On" : "To do");
            setRow(rMode, silent ? Ui.OK : Ui.TODO,
                    rooted ? "Root: silent, with app data" : shz ? "Shizuku: silent delete"
                            : owner ? "Device owner: silent delete" : "Each delete must be confirmed on screen",
                    silent ? "Silent" : "Manual");
            boolean showApk = r != null && !rooted;
            rApk.view.setVisibility(showApk ? View.VISIBLE : View.GONE);
            apkDiv.setVisibility(showApk ? View.VISIBLE : View.GONE);
            setRow(rApk, apk ? Ui.INFO : Ui.BAD,
                    apk ? "On. App data is not saved without root" : "Off. Nothing is deleted without root",
                    apk ? "On" : "Off");

            // --- home: hero
            int ready = (keys ? 1 : 0) + (n > 0 ? 1 : 0) + (acc ? 1 : 0) + (silent ? 1 : 0) + (canDelete ? 1 : 0);
            StringBuilder how = new StringBuilder();
            if (Prefs.volEnabled(this)) how.append("hold volume-down 2 s");
            if (Prefs.powerEnabled(this)) {
                if (how.length() > 0) how.append(" or ");
                how.append("press power ").append(Prefs.powerCount(this)).append("x");
            }
            heroTitle.setText(armed ? "Armed" : "Not armed");
            heroSub.setText(!armed ? "Buttons are inactive. Switch on to arm."
                    : how.length() == 0 ? "No button is enabled. Open Trigger."
                    : "To delete " + nApps + ": " + how + ".");
            heroSwitch.setChecked(armed);
            heroProgress.setText(ready == 5 ? "All set" : "Setup: " + ready + " of 5 ready");
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    armed ? new int[]{0xFF1B5E20, 0xFF43A047} : new int[]{0xFF303F9F, 0xFF5C6BC0});
            g.setCornerRadius(dp(22));
            hero.setBackground(g);
            btnBackups.setText("Backups (" + Engine.listBackups(this).size() + ")");

            // --- setup
            setRow(sPwd, keys ? Ui.OK : Ui.TODO,
                    keys ? "Set. Tap to change" : "Needed to encrypt backups", keys ? "Ready" : "To do");
            setRow(sApps, n > 0 ? Ui.OK : Ui.TODO,
                    n > 0 ? nApps + " selected" : "Pick the apps to delete", n > 0 ? "Ready" : "To do");
            setRow(sAcc, acc ? Ui.OK : Ui.TODO,
                    acc ? "Running" : "Turn on in Accessibility settings", acc ? "On" : "To do");
            setRow(sStorage, storage ? Ui.OK : Ui.TODO,
                    storage ? Engine.backupDir(this).getAbsolutePath()
                            : "Allow it so backups survive uninstalling OneKey", storage ? "Ready" : "To do");
            if (r == null) setRow(sRoot, Ui.INFO, "Checking…", "…");
            else setRow(sRoot, rooted ? Ui.OK : Ui.INFO,
                    rooted ? "Rooted: app data is backed up too" : "Not rooted: only the app file is backed up",
                    rooted ? "Yes" : "No");
            if (rooted) setRow(sShz, Ui.INFO, "Not needed, the phone is rooted", "—");
            else if (shz) setRow(sShz, Ui.OK, "Connected: silent delete", "Ready");
            else if (owner) setRow(sShz, Ui.OK, "Device owner: silent delete", "Ready");
            else if (Shz.running()) setRow(sShz, Ui.TODO, "Running. Tap to allow access", "To do");
            else setRow(sShz, Ui.INFO, "Not running: each delete needs a tap", "Off");
            boolean real = "real".equals(Disguise.current(this).key);
            setRow(sDisguise, Ui.INFO, real ? "Shows as OneKey Uninstaller" : "Shows as a working Calculator",
                    real ? "Off" : "Calculator");
            boolean lock = Prefs.isLockSet(this);
            setRow(sLock, lock ? Ui.OK : Ui.INFO,
                    lock ? "PIN asked every time the app opens" : "Anyone with the phone can open it",
                    lock ? "On" : "Off");

            // --- trigger
            swVol.setChecked(Prefs.volEnabled(this));
            swPower.setChecked(Prefs.powerEnabled(this));
            swWarn.setChecked(Prefs.warn(this));
            swApk.setChecked(apk);
            setRow(rPowerCount, Ui.INFO, "Quick presses that start a run", String.valueOf(Prefs.powerCount(this)));

            // --- log
            String l = AppLog.read(this);
            log.setText(l == null || l.trim().isEmpty() ? "No activity yet." : formatLog(l));
        } finally {
            syncing = false;
        }
    }

    /** Newest first; small grey time line, bold run headers, red failures. */
    private CharSequence formatLog(String raw) {
        String[] lines = raw.split("\n");
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        int start = raw.startsWith("\n") || lines.length < 2 ? 0 : 1; // first line may be cut mid-way
        if (lines.length > 0 && lines[0].length() > 16 && lines[0].charAt(2) == '-' && lines[0].charAt(14) == ' ') start = 0;
        for (int i = lines.length - 1; i >= start; i--) {
            String ln = lines[i];
            if (ln.trim().isEmpty()) continue;
            String time = "", msg = ln;
            if (ln.length() > 16 && ln.charAt(14) == ' ' && ln.charAt(15) == ' ') {
                time = ln.substring(0, 14);
                msg = ln.substring(16);
            }
            boolean header = msg.startsWith("===");
            if (header) msg = msg.replace("=", "").trim();
            String low = msg.toLowerCase(Locale.US);
            boolean bad = low.contains("fail") || low.contains("wrong password") || low.contains("not deleted")
                    || low.contains("could not");
            if (sb.length() > 0) sb.append("\n\n");
            if (!time.isEmpty()) {
                int a = sb.length();
                sb.append(time).append('\n');
                sb.setSpan(new android.text.style.ForegroundColorSpan(0xFF9AA0AA), a, sb.length(), 0);
                sb.setSpan(new android.text.style.RelativeSizeSpan(0.82f), a, sb.length(), 0);
            }
            int b = sb.length();
            sb.append(msg);
            int color = bad ? Ui.RED : header ? Ui.ACCENT : Ui.TEXT;
            sb.setSpan(new android.text.style.ForegroundColorSpan(color), b, sb.length(), 0);
            if (header || bad) sb.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), b, sb.length(), 0);
        }
        return sb;
    }

    // ------------------------------------------------------------ calculator decoy

    /** A working calculator shown instead of the real app while disguised. */
    @SuppressWarnings("deprecation")
    private void buildDecoy() {
        getWindow().setStatusBarColor(0xFF1C1C1C);
        getWindow().setNavigationBarColor(0xFF1C1C1C);
        getWindow().getDecorView().setSystemUiVisibility(0);
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
        if ("=".equals(key)) {
            // Backup way in: hold "=" for 2 s opens the real app (the PIN still applies), so the
            // disguise can never trap the user if the volume-up trigger is switched off.
            final Runnable reveal = () -> {
                if (Prefs.isLockSet(this)) promptLock();
                else buildRealUi();
            };
            b.setOnTouchListener((v, ev) -> {
                int a = ev.getActionMasked();
                if (a == android.view.MotionEvent.ACTION_DOWN) v.postDelayed(reveal, 2000);
                else if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL)
                    v.removeCallbacks(reveal);
                return false;
            });
        }
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
        styleWindow();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(dp(32), dp(96), dp(32), dp(32));

        root.addView(Ui.badge(this, R.drawable.ic_lock, Ui.ACCENT, Ui.ACCENT_SOFT, 76));
        TextView t = Ui.text(this, "Enter PIN", 24, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(22), 0, dp(4));
        root.addView(t);
        TextView lockSub = Ui.text(this, "OneKey is locked", 14, Ui.SUB, false);
        lockSub.setGravity(Gravity.CENTER);
        root.addView(lockSub);

        final EditText pin = new EditText(this);
        pin.setHint("• • • •");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setGravity(Gravity.CENTER);
        pin.setTextSize(24);
        pin.setLetterSpacing(0.3f);
        pin.setTextColor(Ui.TEXT);
        pin.setBackground(Ui.stroke(Ui.CARD, Ui.DIVIDER, dp(2), dp(16)));
        pin.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(260), Ui.WRAP);
        plp.topMargin = dp(28);
        root.addView(pin, plp);

        final TextView err = Ui.text(this, "", 14, Ui.RED, true);
        err.setGravity(Gravity.CENTER);
        err.setPadding(0, dp(10), 0, dp(10));
        root.addView(err);

        Button go = Ui.filled(this, "Unlock", Ui.ACCENT);
        go.setOnClickListener(v -> {
            if (Prefs.checkLock(this, pin.getText().toString())) {
                realBuilt = false;
                buildRealUi();
            } else {
                err.setText("Wrong PIN");
                pin.setText("");
            }
        });
        pin.setOnEditorActionListener((v, id, ev) -> {
            go.performClick();
            return true;
        });
        root.addView(go, new LinearLayout.LayoutParams(dp(260), Ui.WRAP));
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
                .setMessage("Your app will look and work like a real Calculator. To get back to OneKey:\n\n"
                        + "• HOLD the VOLUME-UP button for 2 seconds (works even on the lock screen), or\n"
                        + "• in the calculator, HOLD the \"=\" key for 2 seconds.\n\n"
                        + "Your PIN (if set) is still asked."
                        + (acc ? "" : "\n\nThe button trigger is OFF, so only the \"=\" key works until you "
                               + "turn it on (Setup › Button trigger service)."))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (d, w) -> doDisguise(m))
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
