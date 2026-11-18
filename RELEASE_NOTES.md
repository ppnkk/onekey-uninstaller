# OneKey Uninstaller v1.4

One hardware-button press makes an encrypted backup of the apps you chose, then uninstalls them — even from the lock screen. Restore any app later from its encrypted backup.

## Install

1. Download `OneKeyUninstaller.apk` below.
2. On the phone, tap the file and allow **Install unknown apps**.
3. If Play Protect warns about an unknown developer, choose **Install anyway**.

Android 8.0 or newer. See the [README](README.md) for full setup.

## What's new in 1.4

- **Real Calculator disguise.** Disguised as Calculator, the app now opens a genuinely working calculator. The real OneKey screen appears only when you **hold volume-up for 2 seconds** — so the disguise hides the app in plain sight.
- **App lock (PIN).** New step 8: set a PIN and OneKey asks for it every time the real screen opens, including after a volume-up reveal.
- **App icons in the picker.** "Choose apps to delete" now shows each app's icon next to its name and package.
- **Removed the unreliable "hide the icon" option.** On Samsung One UI a disabled launcher icon can't be removed by any app, which caused confusion. Use the Calculator disguise, or Samsung's own Settings › Home screen › Hide apps.
- **Restore installs to the main profile only (`--user 0`).** Prevents a restored app from being cloned into a Dual App / Secure Folder profile (which looked like a duplicate icon).

## Highlights

- One-press trigger: volume-down held 2 s, or power pressed several times. Works on the lock screen.
- Encrypted backup before every delete (AES-256-GCM + RSA-2048, password-protected). No backup, no delete.
- Silent uninstall with root or with Shizuku (no root).
- Restore apps from backup (with data, if rooted). Backups are self-contained — they still restore with their password even if you reinstall OneKey.
- Optional 5-second warning popup with Cancel.
- Calculator disguise (reveal with volume-up held 2 s) and an optional PIN app lock.

## Known limits

- Without root, only the app file is backed up (Android does not allow reading another app's data). App data is lost on delete unless you are rooted.
- Shizuku must be restarted after each reboot.
- The Calculator disguise needs the button trigger (Accessibility) on, since volume-up is the only way back to the real screen.

## Verify the download

SHA-256 of `OneKeyUninstaller.apk`:

```
A02D5D46251C93BE7D691A22F1E69C16E1D2A833D73B055245DF761100BCF2D4
```
(Rebuilding the app produces a new APK with a different hash; update this if you rebuild.)
