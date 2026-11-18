package com.onekey.uninstaller;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/** Switches the app's launcher name and icon between the real app and a Calculator disguise. */
final class Disguise {
    static final class Mask {
        final String key;     // stored id
        final String alias;   // activity-alias short name
        final String label;   // what the user sees in the chooser
        Mask(String key, String alias, String label) {
            this.key = key;
            this.alias = alias;
            this.label = label;
        }
    }

    static final Mask[] MASKS = {
            new Mask("real", "LauncherReal", "OneKey Uninstaller (normal)"),
            new Mask("calc", "LauncherCalc", "Calculator (disguise)"),
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

    /**
     * Enables the chosen launcher-alias and disables the other, through the app's own
     * PackageManager (an app may always change its own components, so this needs no root
     * or Shizuku). Enabling an alias updates the drawer live and shows exactly one icon.
     */
    static void apply(Context c, String key) {
        Mask chosen = null;
        for (Mask m : MASKS) if (m.key.equals(key)) chosen = m;
        if (chosen == null) chosen = MASKS[0];
        setAlias(c, chosen.alias, true);
        for (Mask m : MASKS) if (!m.key.equals(chosen.key)) setAlias(c, m.alias, false);
        Prefs.sp(c).edit().putString("mask", chosen.key).commit();
        AppLog.add(c, "Disguise set to: " + chosen.label + ".");
    }
}
