package com.onekey.uninstaller;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class AppLog {
    interface Listener { void onLog(); }

    static volatile Listener listener;

    private AppLog() {}

    private static File file(Context c) { return new File(c.getFilesDir(), "log.txt"); }

    static synchronized void add(Context c, String msg) {
        Log.i("OneKey", msg);
        String line = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date()) + "  " + msg + "\n";
        try (FileOutputStream o = new FileOutputStream(file(c), true)) {
            o.write(line.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
        Listener l = listener;
        if (l != null) l.onLog();
    }

    static synchronized String read(Context c) {
        File f = file(c);
        if (!f.exists()) return "";
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            long len = r.length();
            int n = (int) Math.min(len, 12000);
            byte[] b = new byte[n];
            r.seek(len - n);
            r.readFully(b);
            return new String(b, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    static synchronized void clear(Context c) {
        file(c).delete();
    }
}
