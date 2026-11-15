package com.onekey.uninstaller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** Receives install / uninstall results from the system package installer. */
public class StatusReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String what = intent.getStringExtra("what");
        if (what == null) what = "Operation";
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(confirm);
                } catch (Exception e) {
                    AppLog.add(context, what + ": could not open the system confirmation: " + e.getMessage());
                }
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            AppLog.add(context, what + ": success");
        } else {
            AppLog.add(context, what + ": failed (" + status + ") "
                    + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
        }
    }
}
