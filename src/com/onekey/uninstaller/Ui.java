package com.onekey.uninstaller;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Small styling toolkit: colors, cards, badges, pills, buttons and switches (no XML layouts). */
final class Ui {
    private Ui() {}

    static final int BG = 0xFFF3F5F9, CARD = 0xFFFFFFFF, TEXT = 0xFF1B1F27, SUB = 0xFF6B7280,
            ACCENT = 0xFF3F51B5, ACCENT_SOFT = 0xFFE8EAF6,
            GREEN = 0xFF2E7D32, GREEN_SOFT = 0xFFE3F2E6,
            AMBER = 0xFFB26A00, AMBER_SOFT = 0xFFFFF1DB,
            RED = 0xFFC62828, RED_SOFT = 0xFFFDE8E8,
            GRAY = 0xFF6B7280, GRAY_SOFT = 0xFFEEF0F3,
            DIVIDER = 0xFFE7E9EE;

    /** Row states. */
    static final int OK = 0, TODO = 1, BAD = 2, INFO = 3;

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    static int dp(Context c, float v) { return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f); }

    static int color(int state) {
        switch (state) {
            case OK: return GREEN;
            case TODO: return AMBER;
            case BAD: return RED;
            default: return GRAY;
        }
    }

    static int soft(int state) {
        switch (state) {
            case OK: return GREEN_SOFT;
            case TODO: return AMBER_SOFT;
            case BAD: return RED_SOFT;
            default: return GRAY_SOFT;
        }
    }

    static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    static GradientDrawable stroke(int fill, int strokeColor, int widthPx, float radiusPx) {
        GradientDrawable g = round(fill, radiusPx);
        g.setStroke(widthPx, strokeColor);
        return g;
    }

    /** Touch ripple over {@code content}, clipped to {@code mask}. */
    static RippleDrawable ripple(Drawable content, Drawable mask) {
        return new RippleDrawable(ColorStateList.valueOf(0x1F000000), content,
                mask != null ? mask : new ColorDrawable(Color.WHITE));
    }

    static LinearLayout.LayoutParams mw() { return new LinearLayout.LayoutParams(MATCH, WRAP); }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(CARD, dp(c, 18)));
        l.setElevation(dp(c, 1));
        int p = dp(c, 4);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = mw();
        lp.bottomMargin = dp(c, 14);
        l.setLayoutParams(lp);
        return l;
    }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    /** Small uppercase section heading above a card. */
    static TextView label(Context c, String s) {
        TextView t = text(c, s.toUpperCase(), 12, SUB, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(c, 6), dp(c, 6), 0, dp(c, 8));
        return t;
    }

    static ImageView icon(Context c, int res, int tint, int sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setColorFilter(tint);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return iv;
    }

    /** A round tinted circle with an icon in the middle. */
    static FrameLayout badge(Context c, int res, int tint, int bg, int sizeDp) {
        FrameLayout f = new FrameLayout(c);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(bg);
        f.setBackground(g);
        f.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setColorFilter(tint);
        int is = dp(c, sizeDp * 0.52f);
        f.addView(iv, new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
        return f;
    }

    static void tintBadge(FrameLayout f, int tint, int bg) {
        ((GradientDrawable) f.getBackground()).setColor(bg);
        ((ImageView) f.getChildAt(0)).setColorFilter(tint);
    }

    static TextView pill(Context c) {
        TextView t = text(c, "", 12, GRAY, true);
        t.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        return t;
    }

    static void setPill(TextView t, int state, String s) {
        t.setText(s);
        t.setTextColor(color(state));
        t.setBackground(round(soft(state), dp(t.getContext(), 999)));
    }

    static Button filled(Context c, String s, int color) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(Color.WHITE);
        b.setStateListAnimator(null);
        b.setBackground(ripple(round(color, dp(c, 14)), round(Color.WHITE, dp(c, 14))));
        b.setMinHeight(dp(c, 52));
        b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        return b;
    }

    static Button outline(Context c, String s, int color) {
        Button b = filled(c, s, color);
        b.setTextColor(color);
        b.setBackground(ripple(stroke(CARD, DIVIDER, dp(c, 1.5f), dp(c, 14)), round(Color.WHITE, dp(c, 14))));
        return b;
    }

    static View divider(Context c, int insetDp) {
        View v = new View(c);
        v.setBackgroundColor(DIVIDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, Math.max(1, dp(c, 1) / 2 + 1));
        lp.leftMargin = dp(c, insetDp);
        v.setLayoutParams(lp);
        return v;
    }

    static void styleSwitch(Switch s, int on, int track) {
        int[][] st = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(st, new int[]{on, 0xFFFFFFFF}));
        s.setTrackTintList(new ColorStateList(st, new int[]{track, 0xFFC5CAD3}));
    }
}
