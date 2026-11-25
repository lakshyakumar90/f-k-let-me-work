# flmw — Focus & Distraction Blocker

flmw blocks distracting websites and apps during focus sessions. The desktop app includes a local dashboard, CLI, and system tray. The Android app runs independently and includes schedules and a Quick Settings tile.

## Install on Android

1. Use the signed **flmw-1.0.0.apk** from `dist/flmw-1.0.0.apk` on the release build machine, or from GitHub Releases once a maintainer publishes it.
2. Transfer the APK to a phone running Android 8.0 or later and open it. If Android prompts you to allow installs from the app that opened the file, follow the prompt.
3. Open flmw. Grant VPN access for website filtering and enable the accessibility service for app blocking. The Focus tab shows which permissions are needed.

The package ID is `com.lakshyakumar.flmw`. If you installed a debug build with a different signature, uninstall it before installing the release. Uninstalling removes its local settings. Future releases can update this APK in place when signed with the same key and built with a higher version code.

## Run on Windows

Install Go 1.26 or later. In the repository root, build with:

```powershell
go build -o flmw.exe ./cmd/flmw
```

Double-click `flmw.exe` in File Explorer and approve the Windows administrator prompt. flmw runs in the background and opens the embedded HTML dashboard in your default browser at [http://127.0.0.1:8787/](http://127.0.0.1:8787/). Double-click it again to reopen the dashboard. Use **Stop focus** in the dashboard to release the website and app blocks.

To run the daemon in a terminal instead, open Administrator PowerShell and run:

```powershell
$env:FLMW_HTTP_PORT = '8787'
.\flmw.exe daemon
```

Leave that terminal open and visit [the dashboard](http://127.0.0.1:8787/). The app picker lists apps from Start menu shortcuts and visible running apps. Set the same environment variable in another terminal to run `.\flmw.exe status`, `.\flmw.exe on 25m`, `.\flmw.exe off`, or `.\flmw.exe tray`.

The HTTP port defaults to 80. Use 8787 for the dashboard if IIS or another server owns port 80. Browser traffic to blocked sites still uses the standard web ports. Your browser may show a certificate warning for blocked HTTPS sites.

Use **Ctrl+C** to stop the foreground daemon and remove its website redirects. If a forced stop or power loss leaves sites blocked, make sure the daemon is stopped, then run `.\flmw.exe restore-hosts` from Administrator PowerShell to remove only the FLMW-managed entries.

## Run on macOS or Linux

Build with `go build -o flmw ./cmd/flmw`, run `sudo ./flmw daemon`, and open [the dashboard](http://127.0.0.1/). Use `sudo ./flmw install` to register the background service and `sudo ./flmw uninstall` to remove it. Linux builds require the system development libraries for the tray backend.

Use `flmw site`, `flmw app`, and `flmw schedule` to manage blocklists and recurring focus sessions. Run `flmw skill` to print the embedded assistant instructions.

## Build and publish an Android release

Open `mobile/` in Android Studio with JDK 17, Android SDK 36, and Gradle 8.13. Select **Build → Generate Signed Bundle / APK → APK → release** and use the project's release signing key.

For command line builds, run `gradle assembleRelease` inside `mobile/`. This generates `app/build/outputs/apk/release/app-release-unsigned.apk`. Use Android SDK Build Tools to align, sign, and verify it:

```text
zipalign -P 16 -f 4 app-release-unsigned.apk flmw-aligned.apk
apksigner sign --ks /path/to/release-keystore.p12 --ks-key-alias flmw-release --out flmw-1.0.0.apk flmw-aligned.apk
apksigner verify --verbose flmw-1.0.0.apk
```

Attach the signed APK to the matching GitHub Release. Keep the release key and password backed up securely outside the repo. Git tracks source and build configuration. APKs, signing keys, executables, and local build caches are ignored.

See the [Android README](mobile/README.md) for more details. flmw is built by Lakshya Kumar and distributed under the [MIT License](LICENSE).
