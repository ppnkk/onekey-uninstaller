# OneKey Uninstaller v1.1

One hardware-button press makes an encrypted backup of the apps you chose, then uninstalls them — even from the lock screen. Restore any app later from its encrypted backup.

## Install

1. Download `OneKeyUninstaller.apk` below.
2. On the phone, tap the file and allow **Install unknown apps**.
3. If Play Protect warns about an unknown developer, choose **Install anyway**.

Android 8.0 or newer. See the [README](README.md) for full setup.

## Highlights

- One-press trigger: volume-down held 2 s, or power pressed several times. Works on the lock screen.
- Encrypted backup before every delete (AES-256-GCM + RSA-2048, password-protected). No backup, no delete.
- Silent uninstall with root or with Shizuku (no root).
- Restore apps from backup (with data, if rooted).
- Optional 5-second warning popup with Cancel.
- Disguise the app as Calculator / Clock / Weather / Notes, or hide its icon (reopen with volume-up held 2 s).

## Known limits

- Without root, only the app file is backed up (Android does not allow reading another app's data). App data is lost on delete unless you are rooted.
- Shizuku must be restarted after each reboot.
- Some launchers leave a greyed-out "ghost" icon after hiding until you reboot; use volume-up to open.

## Verify the download

SHA-256 of `OneKeyUninstaller.apk`:

```
DD893668944F042CD744FC9568F5D33707E5C8F9E91D63A76A33B8CBDC880F77
```
(Rebuilding the app produces a new APK with a different hash; update this if you rebuild.)
