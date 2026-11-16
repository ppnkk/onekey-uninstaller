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

    /**
     * Applies the chosen mask by enabling the new launcher-alias and disabling the others,
     * through the app's own PackageManager. An app may always change its own component state,
     * so this needs neither root nor Shizuku.
     *
     * Note on Samsung One UI: enabling an alias updates the drawer live and shows exactly one
     * clean icon (disguise works). But disabling ALL aliases ("Hidden") leaves the package with
     * zero launcher activities, and One UI keeps showing a greyed, non-launchable "App info" tile
     * that no app — and not even Shizuku, which Samsung blocks from changing another app's
     * components — can remove; it survives a reboot. On Samsung, real hiding is done with
     * Settings > Home screen > Hide apps, or by using a disguise instead.
     */
    static void apply(Context c, String key) {
        Mask chosen = null;
        for (Mask m : MASKS) if (m.key.equals(key)) chosen = m;
        if (chosen == null) chosen = MASKS[0];

        if (chosen.alias != null) setAlias(c, chosen.alias, true);
        for (Mask m : MASKS) {
            if (m.alias != null && !m.key.equals(chosen.key)) setAlias(c, m.alias, false);
        }
        Prefs.sp(c).edit().putString("mask", chosen.key).commit();

        if (chosen.alias == null) {
            AppLog.add(c, "Disguise set to: " + chosen.label
                    + ". The app has no launcher icon now. On Samsung One UI a greyed \"App info\" tile "
                    + "may remain that no app can remove - use Settings > Home screen > Hide apps, or pick "
                    + "a disguise instead. Hold VOLUME-UP for 2s to reopen the app.");
        } else {
            AppLog.add(c, "Disguise set to: " + chosen.label + ".");
        }
    }
}
