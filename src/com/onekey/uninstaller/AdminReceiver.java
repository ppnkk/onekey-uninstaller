package com.onekey.uninstaller;

import android.app.admin.DeviceAdminReceiver;

/** Only needed for the optional "device owner" mode, which allows silent uninstall without root. */
public class AdminReceiver extends DeviceAdminReceiver {
}
