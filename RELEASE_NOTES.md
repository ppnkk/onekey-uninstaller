# OneKey Uninstaller v1.3

One hardware-button press makes an encrypted backup of the apps you chose, then uninstalls them — even from the lock screen. Restore any app later from its encrypted backup.

## Install

1. Download `OneKeyUninstaller.apk` below.
2. On the phone, tap the file and allow **Install unknown apps**.
3. If Play Protect warns about an unknown developer, choose **Install anyway**.

Android 8.0 or newer. See the [README](README.md) for full setup.

## What's new in 1.3

- **Reliable hiding on Samsung (One UI).** On Samsung, a disabled launcher icon can't be removed by any app — a greyed "App info" tile stays in the app list even after a reboot (and Samsung blocks Shizuku from changing it). The **Hidden — no icon** option now explains this honestly and offers a one-tap **Samsung Hide apps** shortcut (Settings › Home screen › Hide apps), which hides the icon while keeping the app running, so volume-up still reopens it.
- **Disguise** (Calculator / Clock / Weather / Notes) is the recommended way to hide in plain sight — it shows one clean, ordinary-looking icon on every phone, Samsung included.
- Removed the Shizuku-based launcher refresh that only produced a permission error on Samsung.
- Hardened the Windows build script so a harmless `javac` deprecation note no longer aborts the build.

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
- **Hiding the icon with zero launcher entries does not work on Samsung One UI** (a greyed "App info" tile remains, un-removable by any app). Use the built-in **Samsung Hide apps** shortcut or a Disguise instead. A copy installed in **Secure Folder** or a second profile is separate and must be hidden or removed from inside that profile.

## Verify the download

SHA-256 of `OneKeyUninstaller.apk`:

```
1C0E20B29EBCAF29BAFCE3505FDCCFC660E5244442406DF118E298203DB3CC18
```
(Rebuilding the app produces a new APK with a different hash; update this if you rebuild.)
