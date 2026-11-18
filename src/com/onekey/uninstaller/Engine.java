package com.onekey.uninstaller;

import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Backup, uninstall and restore logic. */
final class Engine {
    static final String EXT = ".okb";
    private static final Pattern SAFE_PKG = Pattern.compile("[A-Za-z0-9_.]+");
    private static final AtomicBoolean running = new AtomicBoolean(false);

    private Engine() {}

    static boolean isRunning() { return running.get(); }

    // ---------------------------------------------------------------- helpers

    static boolean isInstalled(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static boolean isDeviceOwner(Context c) {
        DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isDeviceOwnerApp(c.getPackageName());
    }

    static boolean hasAllFilesAccess(Context c) {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return c.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Public folder if allowed (survives uninstalling this app), else the app's own folder. */
    static File backupDir(Context c) {
        if (hasAllFilesAccess(c)) {
            File d = new File(Environment.getExternalStorageDirectory(), "OneKeyBackups");
            if (d.isDirectory() || d.mkdirs()) return d;
        }
        File d = c.getExternalFilesDir("backups");
        if (d == null) d = new File(c.getFilesDir(), "backups");
        d.mkdirs();
        return d;
    }

    static List<File> listBackups(Context c) {
        List<File> out = new ArrayList<>();
        List<File> dirs = new ArrayList<>();
        dirs.add(backupDir(c));
        File own = c.getExternalFilesDir("backups");
        if (own != null && !dirs.contains(own)) dirs.add(own);
        for (File d : dirs) {
            File[] fs = d.listFiles();
            if (fs == null) continue;
            for (File f : fs) if (f.getName().endsWith(EXT)) out.add(f);
        }
        out.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return out;
    }

    private static void vibrate(Context c, long[] pattern) {
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } catch (Exception ignored) {
        }
    }

    private static void checkPkg(String pkg) {
        if (pkg == null || !SAFE_PKG.matcher(pkg).matches())
            throw new IllegalArgumentException("Bad package name: " + pkg);
    }

    // ---------------------------------------------------------------- panic run

    /** Called by the hardware-button trigger or the "Run now" button. */
    static void runPanic(final Context ctx, final String source) {
        final Context c = ctx.getApplicationContext();
        if (!running.compareAndSet(false, true)) return;
        new Thread(() -> {
            PowerManager.WakeLock wl = null;
            try {
                PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
                wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "onekey:run");
                wl.acquire(30 * 60 * 1000L);
                vibrate(c, new long[]{0, 150});
                doPanic(c, source);
            } catch (Throwable t) {
                AppLog.add(c, "Run failed: " + t);
            } finally {
                if (wl != null && wl.isHeld()) wl.release();
                running.set(false);
                AppLog.add(c, "Finished.");
            }
        }, "onekey-run").start();
    }

    private static void doPanic(Context c, String source) {
        AppLog.add(c, "=== Triggered by " + source + " ===");
        List<String> targets = new ArrayList<>(Prefs.targets(c));
        targets.remove(c.getPackageName());
        if (targets.isEmpty()) {
            AppLog.add(c, "No apps selected. Nothing to do.");
            return;
        }
        if (!Prefs.hasKeys(c)) {
            AppLog.add(c, "No backup password set, so backups cannot be encrypted. Nothing was deleted.");
            return;
        }
        boolean root = Shell.hasRoot(20);
        int mode = privMode(root);
        boolean owner = isDeviceOwner(c);
        boolean apkOnlyAllowed = Prefs.sp(c).getBoolean("apkOnly", false);
        AppLog.add(c, "Mode: " + (root ? "ROOT (full data backup, silent delete)"
                : mode == Shell.SHIZUKU ? "Shizuku (APK-only backup, silent delete)"
                : owner ? "device owner (APK-only backup, silent delete)"
                : "no root and no Shizuku (APK-only backup, you must confirm each delete on screen)"));
        if (!root && !apkOnlyAllowed) {
            AppLog.add(c, "No root: app data cannot be backed up. Nothing was deleted. Tick "
                    + "\"Allow delete without data backup\" in the app if you accept losing app data.");
            vibrate(c, new long[]{0, 80, 80, 80, 80, 80});
            return;
        }
        int done = 0;
        for (String pkg : targets) {
            if (!isInstalled(c, pkg)) {
                AppLog.add(c, pkg + ": not installed, skipped");
                continue;
            }
            try {
                File f = backup(c, pkg, root);
                AppLog.add(c, pkg + ": backup saved " + f.getName() + " (" + (f.length() / 1024) + " KB)");
            } catch (Throwable t) {
                AppLog.add(c, pkg + ": BACKUP FAILED, app was NOT deleted: " + t);
                continue;
            }
            if (uninstall(c, pkg, mode, owner)) {
                done++;
                AppLog.add(c, pkg + ": uninstalled");
            } else {
                AppLog.add(c, pkg + ": uninstall FAILED (backup is kept)");
            }
        }
        AppLog.add(c, "Deleted " + done + " of " + targets.size() + " selected apps.");
        vibrate(c, new long[]{0, 120, 120, 120});
    }

    // ---------------------------------------------------------------- backup

    static File backup(Context c, String pkg, boolean root) throws Exception {
        checkPkg(pkg);
        PackageManager pm = c.getPackageManager();
        PackageInfo pi = pm.getPackageInfo(pkg, 0);
        ApplicationInfo ai = pi.applicationInfo;
        List<File> apks = new ArrayList<>();
        apks.add(new File(ai.sourceDir));
        if (ai.splitSourceDirs != null) for (String s : ai.splitSourceDirs) apks.add(new File(s));

        JSONObject meta = new JSONObject();
        meta.put("package", pkg);
        meta.put("label", String.valueOf(pm.getApplicationLabel(ai)));
        meta.put("versionName", pi.versionName);
        meta.put("versionCode", pi.versionCode);
        meta.put("time", System.currentTimeMillis());
        meta.put("hasData", root);
        meta.put("sdk", Build.VERSION.SDK_INT);
        JSONArray names = new JSONArray();
        for (File f : apks) names.put(f.getName());
        meta.put("apks", names);

        File dir = backupDir(c);
        String ts = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File part = new File(dir, pkg + "_" + ts + ".part");
        File fin = new File(dir, pkg + "_" + ts + EXT);
        boolean ok = false;
        try {
            OutputStream raw = new BufferedOutputStream(new FileOutputStream(part), 256 * 1024);
            try (BackupFormat.Writer w = new BackupFormat.Writer(
                    Crypto.encrypt(raw, Prefs.pub(c), Prefs.privBlob(c)))) {
                w.put("meta.json", meta.toString().getBytes(StandardCharsets.UTF_8));
                for (File f : apks) {
                    try (InputStream in = new FileInputStream(f)) {
                        long n = w.put("apk/" + f.getName(), in);
                        if (n != f.length()) throw new java.io.IOException("Short read of " + f.getName());
                    }
                }
                if (root) tarData(c, pkg, w);
                w.finish();
            }
            if (!part.renameTo(fin)) throw new java.io.IOException("Could not rename backup file");
            ok = true;
            return fin;
        } finally {
            if (!ok) part.delete();
        }
    }

    /** Streams a tar of all data folders of the app (private, device-protected, external, obb). */
    private static void tarData(Context c, String pkg, BackupFormat.Writer w) throws Exception {
        String cmd = "am force-stop " + pkg + " >/dev/null 2>&1; cd / && L=''; for p in"
                + " data/data/" + pkg
                + " data/user_de/0/" + pkg
                + " data/media/0/Android/data/" + pkg
                + " data/media/0/Android/obb/" + pkg
                + "; do [ -e \"$p\" ] && L=\"$L $p\"; done;"
                + " if [ -z \"$L\" ]; then exit 3; fi; tar -cf - $L 2>/dev/null";
        Process p = new ProcessBuilder("su", "-c", cmd).start();
        p.getOutputStream().close();
        final InputStream err = p.getErrorStream();
        new Thread(() -> {
            try {
                byte[] b = new byte[1024];
                while (err.read(b) != -1) { /* drain */ }
            } catch (Exception ignored) {
            }
        }).start();
        long n;
        try (InputStream in = new BufferedInputStream(p.getInputStream(), 256 * 1024)) {
            n = w.put("data.tar", in);
        }
        int code = p.waitFor();
        if (code == 3) {
            AppLog.add(c, pkg + ": app has no data folders");
            return;
        }
        if (code != 0 && !(code == 1 && n >= 10240)) {
            throw new java.io.IOException("tar failed (exit " + code + ", " + n + " bytes)");
        }
        if (n < 1024) throw new java.io.IOException("tar produced no data");
        if (code == 1) AppLog.add(c, pkg + ": tar reported warnings (some special files skipped)");
    }

    // ---------------------------------------------------------------- uninstall

    private static boolean waitGone(Context c, String pkg, long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (!isInstalled(c, pkg)) return true;
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return !isInstalled(c, pkg);
    }

    /** ROOT if rooted, else SHIZUKU if it is running and allowed, else NONE. */
    static int privMode(boolean root) {
        return root ? Shell.ROOT : Shz.ready() ? Shell.SHIZUKU : Shell.NONE;
    }

    static boolean uninstall(Context c, String pkg, int mode, boolean owner) {
        checkPkg(pkg);
        if (mode != Shell.NONE) {
            Shell.Result r = Shell.run(mode, "pm uninstall " + pkg);
            if (waitGone(c, pkg, 3000)) return true;
            // system apps can only be removed for the current user
            r = Shell.run(mode, "pm uninstall --user 0 " + pkg);
            if (waitGone(c, pkg, 3000)) return true;
            AppLog.add(c, pkg + ": pm said: " + r.out);
            return false;
        }
        if (owner) {
            try {
                Intent i = new Intent(c, StatusReceiver.class).putExtra("what", "Uninstall " + pkg);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                PendingIntent pi = PendingIntent.getBroadcast(c, pkg.hashCode(), i, flags);
                c.getPackageManager().getPackageInstaller().uninstall(pkg, pi.getIntentSender());
                if (waitGone(c, pkg, 20000)) return true;
            } catch (Exception e) {
                AppLog.add(c, pkg + ": device-owner uninstall error: " + e);
            }
        }
        // Last resort: the system uninstall dialog, which the user must confirm.
        try {
            Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Context starter = TriggerService.instance != null ? TriggerService.instance : c;
            starter.startActivity(i);
            return waitGone(c, pkg, 25000);
        } catch (Exception e) {
            AppLog.add(c, pkg + ": could not open uninstall dialog: " + e);
            return false;
        }
    }

    // ---------------------------------------------------------------- restore

    /** Reads only the description of a backup. Also verifies the password. */
    static JSONObject readMeta(File f, char[] password) throws Exception {
        try (BackupFormat.Reader r = new BackupFormat.Reader(
                Crypto.decrypt(new BufferedInputStream(new FileInputStream(f)), password))) {
            String name = r.next();
            if (!"meta.json".equals(name)) throw new java.io.IOException("Backup has no description");
            return new JSONObject(new String(readAll(r.stream()), StandardCharsets.UTF_8));
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) != -1) o.write(b, 0, n);
        return o.toByteArray();
    }

    private static void deleteDir(File d) {
        File[] fs = d.listFiles();
        if (fs != null) for (File f : fs) {
            if (f.isDirectory()) deleteDir(f);
            else f.delete();
        }
        d.delete();
    }

    static void restoreAsync(final Context ctx, final File f, final char[] password) {
        final Context c = ctx.getApplicationContext();
        if (!running.compareAndSet(false, true)) {
            AppLog.add(c, "Busy, try again in a moment.");
            return;
        }
        new Thread(() -> {
            try {
                restore(c, f, password);
            } catch (Crypto.BadPasswordException e) {
                AppLog.add(c, "Restore failed: wrong password.");
            } catch (Throwable t) {
                AppLog.add(c, "Restore failed: " + t);
            } finally {
                running.set(false);
            }
        }, "onekey-restore").start();
    }

    private static void restore(Context c, File f, char[] password) throws Exception {
        AppLog.add(c, "=== Restoring " + f.getName() + " ===");
        // Pass 1: make sure the whole file decrypts and is complete before touching anything.
        try (BackupFormat.Reader r = new BackupFormat.Reader(
                Crypto.decrypt(new BufferedInputStream(new FileInputStream(f), 256 * 1024), password))) {
            while (r.next() != null) { /* next() skips and verifies each entry */ }
        }
        AppLog.add(c, "Backup verified (password correct, file complete).");

        boolean root = Shell.hasRoot(20);
        int mode = privMode(root);
        File tmp = new File(c.getCacheDir(), "restore");
        deleteDir(tmp);
        tmp.mkdirs();
        try (BackupFormat.Reader r = new BackupFormat.Reader(
                Crypto.decrypt(new BufferedInputStream(new FileInputStream(f), 256 * 1024), password))) {
            JSONObject meta = null;
            List<File> apks = new ArrayList<>();
            boolean installed = false;
            String name;
            while ((name = r.next()) != null) {
                if (name.equals("meta.json")) {
                    meta = new JSONObject(new String(readAll(r.stream()), StandardCharsets.UTF_8));
                    checkPkg(meta.getString("package"));
                } else if (name.startsWith("apk/")) {
                    String n = new File(name.substring(4)).getName();
                    if (!n.matches("[A-Za-z0-9_.\\-]+")) n = "split" + apks.size() + ".apk";
                    File out = new File(tmp, n);
                    try (OutputStream o = new BufferedOutputStream(new FileOutputStream(out))) {
                        InputStream in = r.stream();
                        byte[] b = new byte[64 * 1024];
                        int k;
                        while ((k = in.read(b)) != -1) o.write(b, 0, k);
                    }
                    apks.add(out);
                } else if (name.equals("data.tar")) {
                    if (meta == null) throw new java.io.IOException("Backup has no description");
                    String pkg = meta.getString("package");
                    if (!root) {
                        AppLog.add(c, "This backup contains app data, but restoring data needs root. "
                                + "Only the app itself will be installed.");
                        continue;
                    }
                    if (!installed) {
                        installPriv(c, mode, pkg, apks);
                        installed = true;
                    }
                    restoreData(c, pkg, r.stream());
                }
            }
            if (meta == null) throw new java.io.IOException("Backup has no description");
            String pkg = meta.getString("package");
            if (!installed) {
                if (mode != Shell.NONE) installPriv(c, mode, pkg, apks);
                else {
                    installWithSystemDialog(c, pkg, apks);
                    return; // temp files are needed until the user confirms; cleaned on next restore
                }
            }
            AppLog.add(c, "Restore complete: " + meta.optString("label") + " (" + pkg + ")");
            AppLog.add(c, "If a Samsung launcher shows a second icon for this app, it is only a stray "
                    + "shortcut (the app is installed once) — long-press it and choose \"Remove from Home\", "
                    + "or reboot, to clear it.");
        } finally {
            if (mode != Shell.NONE) deleteDir(tmp);
        }
    }

    /** Silent install through "pm" as root or through Shizuku. */
    private static void installPriv(Context c, int mode, String pkg, List<File> apks) throws Exception {
        if (apks.isEmpty()) throw new java.io.IOException("Backup contains no APK");
        long total = 0;
        for (File a : apks) total += a.length();
        // --user 0 keeps the restore in the main profile only, so it can never create a
        // clone in a Dual App / Secure Folder profile.
        String flags = "-r --user 0" + (Build.VERSION.SDK_INT >= 34 ? " --bypass-low-target-sdk-block" : "");
        Shell.Result r = Shell.run(mode, "pm install-create " + flags + " -S " + total);
        Matcher m = Pattern.compile("\\[(\\d+)\\]").matcher(r.out);
        if (!r.ok() || !m.find()) throw new java.io.IOException("pm install-create: " + r.out);
        String id = m.group(1);
        int i = 0;
        for (File a : apks) {
            try (InputStream in = new FileInputStream(a)) {
                Shell.Result w = Shell.run(mode, "pm install-write -S " + a.length() + " " + id + " s" + (i++) + ".apk -", in);
                if (!w.ok() || !w.out.contains("Success")) {
                    Shell.run(mode, "pm install-abandon " + id);
                    throw new java.io.IOException("pm install-write: " + w.out);
                }
            }
        }
        r = Shell.run(mode, "pm install-commit " + id);
        if (!r.out.contains("Success")) throw new java.io.IOException("pm install-commit: " + r.out);
        AppLog.add(c, pkg + ": app installed");
    }

    private static void restoreData(Context c, String pkg, InputStream tar) throws Exception {
        Shell.Result r = Shell.su("stat -c %u /data/data/" + pkg);
        String uid = r.out.trim();
        if (!r.ok() || !uid.matches("\\d+")) throw new java.io.IOException("Could not find app uid: " + r.out);
        Shell.su("am force-stop " + pkg);
        r = Shell.su("tar -xf - -C /", tar);
        if (!r.ok()) AppLog.add(c, pkg + ": tar extract reported: " + r.out);
        boolean modern = Build.VERSION.SDK_INT >= 30;
        String extGid = modern ? "1078" : "1023";
        String obbGid = modern ? "1079" : "1023";
        String extUid = modern ? uid : "1023";
        String fix = "for p in /data/data/" + pkg + " /data/user_de/0/" + pkg + "; do"
                + " [ -e $p ] && chown -R " + uid + ":" + uid + " $p && restorecon -RF $p; done;"
                + " p=/data/media/0/Android/data/" + pkg + "; [ -e $p ] && chown -R " + extUid + ":" + extGid
                + " $p && restorecon -RF $p;"
                + " p=/data/media/0/Android/obb/" + pkg + "; [ -e $p ] && chown -R " + extUid + ":" + obbGid
                + " $p && restorecon -RF $p; true";
        r = Shell.su(fix);
        if (!r.out.isEmpty()) AppLog.add(c, pkg + ": permission fix said: " + r.out);
        Shell.su("am force-stop " + pkg);
        AppLog.add(c, pkg + ": app data restored");
    }

    private static void installWithSystemDialog(Context c, String pkg, List<File> apks) throws Exception {
        if (apks.isEmpty()) throw new java.io.IOException("Backup contains no APK");
        PackageInstaller inst = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams sp =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        sp.setAppPackageName(pkg);
        int id = inst.createSession(sp);
        try (PackageInstaller.Session s = inst.openSession(id)) {
            int i = 0;
            for (File a : apks) {
                try (InputStream in = new FileInputStream(a);
                     OutputStream o = s.openWrite("s" + (i++) + ".apk", 0, a.length())) {
                    byte[] b = new byte[64 * 1024];
                    int k;
                    while ((k = in.read(b)) != -1) o.write(b, 0, k);
                    s.fsync(o);
                }
            }
            Intent i2 = new Intent(c, StatusReceiver.class).putExtra("what", "Install " + pkg);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT
                    | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            s.commit(PendingIntent.getBroadcast(c, id, i2, flags).getIntentSender());
        }
        AppLog.add(c, pkg + ": please confirm the install on screen (no root, so data is not restored)");
    }
}
