package com.onekey.uninstaller;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

final class Prefs {
    private Prefs() {}

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("onekey", Context.MODE_PRIVATE);
    }

    static Set<String> targets(Context c) {
        return new HashSet<>(sp(c).getStringSet("targets", new HashSet<String>()));
    }

    static void setTargets(Context c, Set<String> t) {
        sp(c).edit().putStringSet("targets", new HashSet<>(t)).apply();
    }

    static boolean armed(Context c) { return sp(c).getBoolean("armed", false); }
    static boolean warn(Context c) { return sp(c).getBoolean("warn", true); }
    static boolean volEnabled(Context c) { return sp(c).getBoolean("vol", true); }
    static boolean powerEnabled(Context c) { return sp(c).getBoolean("power", false); }
    static int powerCount(Context c) { return sp(c).getInt("powerCount", 6); }
    static boolean showSystem(Context c) { return sp(c).getBoolean("showSystem", false); }

    static void set(Context c, String key, boolean v) { sp(c).edit().putBoolean(key, v).apply(); }
    static void set(Context c, String key, int v) { sp(c).edit().putInt(key, v).apply(); }

    static boolean hasKeys(Context c) { return sp(c).contains("pub") && sp(c).contains("priv"); }

    static byte[] pub(Context c) { return Base64.getDecoder().decode(sp(c).getString("pub", "")); }
    static byte[] privBlob(Context c) { return Base64.getDecoder().decode(sp(c).getString("priv", "")); }

    static void setKeys(Context c, Crypto.Keys k) {
        sp(c).edit()
                .putString("pub", Base64.getEncoder().encodeToString(k.pub))
                .putString("priv", Base64.getEncoder().encodeToString(k.privBlob))
                .commit();
    }
}
