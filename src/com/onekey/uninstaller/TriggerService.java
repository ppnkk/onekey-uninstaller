package com.onekey.uninstaller;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayDeque;

/**
 * Hardware button trigger. Works on the lock screen too.
 *  - Volume-down held for 2 seconds (screen on, also while locked).
 *  - Power button pressed N times quickly (counted through screen on/off changes, also while locked).
 * If "warning" is on, a 5 second countdown with a Cancel button is shown first (also over the lock screen).
 */
public class TriggerService extends AccessibilityService {
    static volatile TriggerService instance;

    private static final long HOLD_MS = 2000;
    private static final int WARN_SECONDS = 5;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Long> powerEvents = new ArrayDeque<>();
    private boolean receiverRegistered;

    private View warnView;
    private TextView warnText;
    private int warnLeft;
    private String warnSource;

    private final Runnable holdFired = () -> {
        if (Prefs.armed(this) && Prefs.volEnabled(this)) trigger("volume-down long press");
    };

    /** Volume-up held 2s opens the REAL app, past any Calculator disguise (reveal flag). */
    private final Runnable openFired = () -> {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra(MainActivity.EXTRA_REVEAL, true);
            startActivity(i);
        } catch (Exception e) {
            AppLog.add(this, "Open from volume-up failed: " + e.getMessage());
        }
    };

    private final Runnable warnTick = new Runnable() {
        @Override
        public void run() {
            if (warnView == null) return;
            if (warnLeft <= 0) {
                String src = warnSource;
                hideWarning();
                Engine.runPanic(TriggerService.this, src);
                return;
            }
            warnText.setText("Backing up and deleting " + Prefs.targets(TriggerService.this).size()
                    + " app(s) in " + warnLeft + " s");
            warnLeft--;
            handler.postDelayed(this, 1000);
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!Prefs.armed(context) || !Prefs.powerEnabled(context)) {
                powerEvents.clear();
                return;
            }
            int need = Prefs.powerCount(context);
            long now = System.currentTimeMillis();
            long window = need * 900L + 1000L;
            powerEvents.addLast(now);
            while (!powerEvents.isEmpty() && now - powerEvents.peekFirst() > window) powerEvents.removeFirst();
            if (powerEvents.size() >= need) {
                powerEvents.clear();
                trigger("power button x" + need);
            }
        }
    };

    /** Button pattern recognised: warn first (if enabled), then run. */
    private void trigger(String source) {
        if (Engine.isRunning()) return;
        if (Prefs.warn(this)) showWarning(source);
        else Engine.runPanic(this, source);
    }

    private void showWarning(String source) {
        if (warnView != null) return;
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(VibrationEffect.createWaveform(new long[]{0, 200, 100, 200}, -1));
        } catch (Exception ignored) {
        }
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#B71C1C"));
        bg.setCornerRadius(pad);
        box.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("OneKey Uninstaller");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(title);

        warnText = new TextView(this);
        warnText.setTextColor(Color.WHITE);
        warnText.setTextSize(16);
        warnText.setPadding(0, pad / 2, 0, pad / 2);
        box.addView(warnText);

        Button cancel = new Button(this);
        cancel.setText("CANCEL");
        cancel.setOnClickListener(x -> {
            hideWarning();
            AppLog.add(this, "Cancelled from warning popup.");
        });
        box.addView(cancel);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.92f),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        try {
            ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).addView(box, lp);
        } catch (Exception e) {
            AppLog.add(this, "Could not show warning popup (" + e.getMessage() + "), running without it.");
            Engine.runPanic(this, source);
            return;
        }
        warnView = box;
        warnSource = source;
        warnLeft = WARN_SECONDS;
        handler.post(warnTick);
    }

    private void hideWarning() {
        handler.removeCallbacks(warnTick);
        if (warnView != null) {
            try {
                ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).removeView(warnView);
            } catch (Exception ignored) {
            }
            warnView = null;
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenReceiver, f);
        receiverRegistered = true;
        AppLog.add(this, "Button trigger service is running.");
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    handler.removeCallbacks(holdFired);
                    handler.postDelayed(holdFired, HOLD_MS);
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(holdFired);
            }
        } else if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    handler.removeCallbacks(openFired);
                    handler.postDelayed(openFired, HOLD_MS);
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(openFired);
            }
        }
        return false; // never swallow the key: volume keeps working normally
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used: this service only listens to hardware buttons.
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        instance = null;
        handler.removeCallbacks(holdFired);
        handler.removeCallbacks(openFired);
        hideWarning();
        if (receiverRegistered) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }
}
