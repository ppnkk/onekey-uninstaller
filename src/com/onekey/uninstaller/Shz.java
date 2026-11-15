package com.onekey.uninstaller;

import android.content.pm.PackageManager;

import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;

/** Thin wrapper around Shizuku, which lets this app run "pm" commands with adb-shell rights (no root). */
final class Shz {
    static final int REQUEST_CODE = 7701;

    private Shz() {}

    /** Shizuku service is started and reachable. */
    static boolean running() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Running and this app was allowed to use it. */
    static boolean ready() {
        try {
            return Shizuku.pingBinder() && !Shizuku.isPreV11()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    static void requestPermission() {
        Shizuku.requestPermission(REQUEST_CODE);
    }

    /** Starts "sh -c cmd" with shell rights. */
    static Process start(String cmd) throws Exception {
        Method m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
        m.setAccessible(true);
        return (Process) m.invoke(null, new String[]{"sh", "-c", cmd}, null, null);
    }
}
