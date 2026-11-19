# OneKey Uninstaller v1.5

One hardware-button press makes an encrypted backup of the apps you chose, then uninstalls them — even from the lock screen. Restore any app later from its encrypted backup.

## Install

1. Download `OneKeyUninstaller.apk` below.
2. On the phone, tap the file and allow **Install unknown apps**.
3. If Play Protect warns about an unknown developer, choose **Install anyway**.

Android 8.0 or newer. Updating from an earlier version keeps your settings. See the [README](README.md) for full setup.

## What's new in 1.5

- **New design.** Four tabs (Home, Setup, Trigger, Log) instead of one long page. Rounded cards, icons, and colored **Ready / To do / On / Off** labels replace the old ✔/✘ symbols.
- **Status card on Home.** Shows **Armed** (green) or **Not armed** at a glance, with its own on/off switch and a "Setup: x of 5 ready" progress tag.
- **Real switches** for the buttons and safety options on the Trigger tab.
- **Easier-to-read log.** Newest entry first, times in grey, run starts highlighted, failures in red.
- **Restyled PIN screen**, and the Calculator disguise now has a dark status bar so it looks like a real calculator.
- **No more lockout.** In the Calculator disguise, **holding the "=" key for 2 seconds** opens OneKey (your PIN is still asked). The disguise can no longer trap you if the volume-up trigger is switched off.

## Known limits

- Without root, only the app file is backed up (Android does not allow reading another app's data). App data is lost on delete unless you are rooted.
- Shizuku must be restarted after each reboot.

## Verify the download

SHA-256 of `OneKeyUninstaller.apk`:

```
16DED4E2F86F6768EF2E31E4EDB6571E9F013A2D6F3958F3F5EF3F41C8A9F916
```
(Rebuilding the app produces a new APK with a different hash; update this if you rebuild.)
