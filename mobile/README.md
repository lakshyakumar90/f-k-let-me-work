# flmw Android (Kotlin + Jetpack Compose)

Native Android companion app for **flmw**, built for **Lakshya Kumar** (`com.lakshyakumar.flmw`) with modern non-deprecated Android APIs.

## Tech Stack
- **UI:** Jetpack Compose (BOM `2025.12.00`) + Material 3 (`HorizontalDivider`, `SingleChoiceSegmentedButtonRow`, `Icons.AutoMirrored.Rounded.*`).
- **Edge-to-Edge & System Bars:** `enableEdgeToEdge()` with dynamic `SystemBarStyle`, `android:windowSoftInputMode="adjustResize"`, `WindowInsets.statusBars`, `safeDrawingPadding()`, and `imePadding()`.
- **State & Storage:** `Preferences DataStore` + `kotlinx.serialization.json` + `collectAsStateWithLifecycle()`.
- **Website Blocking (No Root):** Local loopback `VpnService` (`FlmwVpnService`) with `foregroundServiceType="specialUse"` that intercepts UDP/53 DNS queries on-device, returns `NXDOMAIN` for blocked domains & subdomains, and resolves allowed queries through the active network’s DNS.
- **App Blocking (No Root):** `AccessibilityService` (`FlmwAccessibilityService`) detecting foreground blocked apps and immediately launching the full-screen editorial **"Not now."** `ShieldActivity` with deliberate-friction unlock.
- **Quick Settings Tile:** `FocusTileService` for one-tap 25m focus right from the notification shade.

## Running in Android Studio
1. Open `mobile/` in Android Studio with JDK 17, Android SDK 36, and Gradle 8.13.
2. Let Gradle sync (`libs.versions.toml` version catalog).
3. Run on a physical Android device (API 26+) or emulator and grant the 3 native permissions on the **Focus** tab.

For the signed APK and release instructions, see the [project README](../README.md#install-on-android).
