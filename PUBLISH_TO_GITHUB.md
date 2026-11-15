# How to publish OneKey Uninstaller on GitHub

This repo is ready to publish. Follow these steps once. Anything that needs your
GitHub login (creating the repo, pushing, making the release) you do yourself —
every file and command is prepared for you below.

---

## 0. Before you start

- Make sure the app is tested and working on your phones.
- You need a free GitHub account: https://github.com/signup
- Install Git on this PC if you don't have it: https://git-scm.com/download/win
  (During install, accept the defaults.)

## 1. Create an empty repository on GitHub

1. Go to https://github.com/new
2. **Repository name:** `onekey-uninstaller`
3. **Description:** `One-press encrypted backup + uninstall for Android.`
4. Choose **Public**.
5. Do **NOT** tick "Add a README", ".gitignore", or "license" — this repo already has them.
6. Click **Create repository**.
7. Copy the URL it shows, e.g. `https://github.com/YOUR_NAME/onekey-uninstaller.git`

## 2. Push this project (run these in PowerShell)

Run them from the project folder. Replace the URL in the last two lines with yours.

```powershell
cd "C:\Users\kmn18\OneDrive\AI Workflow\Claude\OneKeyUninstaller"
git init
git add .
git commit -m "OneKey Uninstaller v1.1"
git branch -M main
git remote add origin https://github.com/YOUR_NAME/onekey-uninstaller.git
git push -u origin main
```

The first push opens a browser window to sign in to GitHub — approve it. Your
source code, README and license are now public. The APK and your signing key are
**not** pushed (the `.gitignore` keeps them out).

## 3. Publish the APK as a Release

The APK is shared as a downloadable Release asset, not inside the repo.

First, get its checksum so people can verify the download:

```powershell
Get-FileHash "C:\Users\kmn18\OneDrive\AI Workflow\Claude\OneKeyUninstaller\OneKeyUninstaller.apk" -Algorithm SHA256
```

Paste that value into `RELEASE_NOTES.md` where it says "(fill in ...)", then:

1. On your repo page, click **Releases** (right side) > **Create a new release**.
2. **Tag:** `v1.1`  (click "Create new tag").
3. **Title:** `OneKey Uninstaller v1.1`
4. **Description:** paste the contents of `RELEASE_NOTES.md`.
5. Under **Attach binaries**, drag in `OneKeyUninstaller.apk`.
6. Click **Publish release**.

People can now download the APK from your Releases page. The README's "Download"
link already points there.

## 4. Updating later

When you change the app:

```powershell
cd "C:\Users\kmn18\OneDrive\AI Workflow\Claude\OneKeyUninstaller"
git add .
git commit -m "Describe what changed"
git push
```

Then make a new Release (e.g. tag `v1.2`) and attach the new APK.

---

## Important: keep your signing key

The APK is signed with `onekey-release.jks` (currently at
`C:\Users\kmn18\AndroidBuild\onekey-release.jks`). **Back this file up and keep it
private.** Every future update must be signed with the *same* key, or phones will
refuse to install the update over the old one. It is deliberately **not** in the
repo. If you lose it, users would have to uninstall before installing a new build.

## A note on what you are sharing

This app can uninstall apps with one button and hide its own icon. On your own
phone that is a privacy tool; the README is written to be honest about what it
does and what it needs (Shizuku, Accessibility). Keeping it open-source and
clearly described is the right way to share a tool like this.
