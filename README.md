# OneKey Uninstaller

**One hardware-button press makes an encrypted backup of the apps you chose, then uninstalls them — even from the lock screen.** A privacy / panic tool for your own Android phone. You can restore any app later from its encrypted backup.

> Not on Google Play (its icon-hiding and accessibility-trigger features are against Play policy). Install the APK directly, see **Download** below.

---

## Features

- **One-press trigger** — hold volume-down for 2 seconds, or press power several times quickly. Works on the lock screen.
- **Encrypted backup before delete** — every app is backed up first; AES-256-GCM, each backup locked with your password. If a backup fails, that app is **not** deleted.
- **Silent uninstall** — with root, or with [Shizuku](https://shizuku.rikka.app/) (no root needed).
- **Restore** — reinstall any app from its backup (with root, the app's data comes back too).
- **Optional warning popup** — a 5-second countdown with Cancel before anything is deleted. Turn it off for an instant, silent run.
- **Calculator disguise** — the app can appear and work as a real, fully functional calculator. Reveal the real OneKey screen by holding **volume-up for 2 seconds**.
- **App lock (PIN)** — require a PIN every time the real screen opens, including after a volume-up reveal.
- **App icons** — the "choose apps" list shows each app's icon, name and package.

## Requirements

- Android 8.0 (API 26) or newer.
- For silent, one-press delete without root: the free [Shizuku](https://shizuku.rikka.app/) app.
- For full app-data backup: a rooted phone (without root, only the app file is backed up).

## Download

1. Go to the [**Releases**](../../releases) page and download `OneKeyUninstaller.apk` from the latest release.
2. On the phone, tap the file and allow **Install unknown apps** when asked.
3. If Play Protect warns about an unknown developer, choose **Install anyway**.

## Setup (once)

1. **Set backup password.** Needed to restore. It cannot be recovered. Changing it later does not change the password of backups already made.
2. **Choose apps to delete.** Only the apps you tick here are ever deleted — check this list carefully.
3. **Turn on the button trigger.** In Accessibility settings, switch on "OneKey Uninstaller button trigger". On Android 13+, if it says "Restricted setting": Settings > Apps > OneKey Uninstaller > ⋮ menu > "Allow restricted settings", then try again.
4. **Check root access** (only if your phone is rooted).
5. **Allow backup folder.** Backups then go to `/sdcard/OneKeyBackups` and survive uninstalling this app.
6. **Connect Shizuku** (no root): install and start Shizuku, then tap step 6 and "Allow all the time". Shizuku must be started again after each reboot.
7. Tick **ARMED** and choose your trigger.

## How it works

| Phone | Backup contains | Delete |
|---|---|---|
| Rooted | App file **+ all app data** | Silent |
| Shizuku (no root) | App file only | Silent |
| Neither | App file only | Android's own "Uninstall?" dialog — you tap OK (not possible on the lock screen) |

Android does not let one app read another app's data without root, so without root the app's data is lost when it is deleted. Tick "Allow delete without data backup" to accept this.

## Restore

In the app: **Backups: restore or delete** > pick a backup > **Restore** > enter the password. With root the app and its data come back; otherwise the app is reinstalled (silently with Shizuku) without data.

## Disguise and app lock

In the app: **7. Disguise** > pick one > **Apply**.

- **OneKey Uninstaller (normal)** — the usual name and icon.
- **Calculator (disguise)** — the icon and name become a Calculator, and tapping it opens a **real, working calculator**. To reach the real OneKey screen, **hold volume-up for 2 seconds** (works even on the lock screen). The button trigger (step 3) must be on, since that is the only way back in.

**App lock (step 8)** — set a PIN and OneKey asks for it every time the real screen opens, including after a volume-up reveal. If you forget the PIN you must clear the app's data to get back in (your backups on storage are kept).

> Note: an older "hide the icon completely" option was removed. On Samsung One UI a disabled launcher icon can't be removed by any app (it leaves a greyed tile that survives a reboot), so it caused confusion. The Calculator disguise is the reliable way to hide in plain sight; for a literal no-icon, use Samsung's own Settings › Home screen › Hide apps.

## Build from source

No Android Studio or Gradle needed — the project builds with the Android command-line tools.

- **Windows:** run [`build.ps1`](build.ps1) in PowerShell.
- **Linux / macOS:** run [`build.sh`](build.sh).

Both scripts download nothing of their own; set `ANDROID_SDK` (and a JDK 17 on your PATH) as described at the top of each script, then run it. The build compiles the code, runs the encryption unit tests, and produces a signed `OneKeyUninstaller.apk`. The signing key is generated locally on first run and is **not** part of this repository.

## Security notes

- **Backup format:** each backup uses a fresh random AES-256 key (GCM) wrapped with an RSA-2048 public key. The RSA private key is encrypted with your password using PBKDF2-HMAC-SHA256 (150,000 rounds). Without the password, backups cannot be read.
- **Your signing key and backup password are secrets** — never commit them or share them.
- Shizuku bundled bindings are the official [`dev.rikka.shizuku`](https://github.com/RikkaApps/Shizuku-API) API libraries.

## Disclaimer

This tool permanently uninstalls apps and can hide its own icon. Use it only on devices you own. Always keep your backup password and verify your backups. The authors are not responsible for lost apps, lost data, or any other consequence of using this software. No warranty of any kind.

## License

MIT — see [LICENSE](LICENSE).
