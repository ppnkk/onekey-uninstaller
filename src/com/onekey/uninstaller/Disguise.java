package com.onekey.uninstaller;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/** Changes the app's launcher name and icon, or hides the icon, by switching activity-aliases. */
final class Disguise {
    static final class Mask {
        final String key;      // stored id
        final String alias;    // activity-alias short name, or null for "hidden"
        final String label;    // what the user sees in the chooser
        Mask(String key, String alias, String label) {
            this.key = key;
            this.alias = alias;
            this.label = label;
        }
    }

    static final Mask[] MASKS = {
            new Mask("real", "LauncherReal", "OneKey Uninstaller (real name and icon)"),
            new Mask("calc", "LauncherCalc", "Calculator"),
            new Mask("clock", "LauncherClock", "Clock"),
            new Mask("weather", "LauncherWeather", "Weather"),
            new Mask("notes", "LauncherNotes", "Notes"),
            new Mask("hidden", null, "Hidden - no icon in the app drawer"),
    };

    private Disguise() {}

    static Mask current(Context c) {
        String key = Prefs.sp(c).getString("mask", "real");
        for (Mask m : MASKS) if (m.key.equals(key)) return m;
        return MASKS[0];
    }

    private static void setAlias(Context c, String alias, boolean on) {
        ComponentName cn = new ComponentName(c, "com.onekey.uninstaller." + alias);
        int state = on ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        c.getPackageManager().setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP);
    }

    /** Applies the chosen mask. Enables the new launcher first, then disables the others. */
    static void apply(Context c, String key) {
        Mask chosen = null;
        for (Mask m : MASKS) if (m.key.equals(key)) chosen = m;
        if (chosen == null) chosen = MASKS[0];

        if (chosen.alias != null) setAlias(c, chosen.alias, true);
        for (Mask m : MASKS) {
            if (m.alias != null && !m.key.equals(chosen.key)) setAlias(c, m.alias, false);
        }
        Prefs.sp(c).edit().putString("mask", chosen.key).commit();
        AppLog.add(c, "Disguise set to: " + chosen.label);
    }
}
