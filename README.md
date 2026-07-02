# FlipToFocus

**A 100% offline, serverless Android app blocker that turns a physical routine into your unlock key.**

FlipToFocus intercepts the distracting apps *you* choose (Instagram, TikTok, YouTube, …) and, before letting you in, asks you to complete a short **offline focus challenge**: place your phone flat and face-down and keep it still for a timer you set. Move it, and the timer restarts. When the timer finishes, the app opens.

Everything runs on-device. There is **no internet permission, no account, no analytics, no ads, and no payments.** Your usage data never leaves your phone.

---

## ✨ Features

- **Pick your own blocklist** from the apps installed on your device.
- **Physical unlock challenge** using the accelerometer + gyroscope (flat & face-down + hold still).
- **Configurable** challenge duration (1–60 min), face-down requirement, and motion sensitivity.
- **Focus overlay** drawn over the blocked app with a live countdown and position feedback.
- **Session history** stored locally (completed / abandoned).
- **Material 3 UI** with light/dark themes and Android 12+ dynamic color.
- **Privacy by construction** — offline-only, no tracking SDKs, scoped package visibility.

---

## 🧱 Tech stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Architecture | Clean Architecture + MVVM |
| Async | Coroutines / Flow |
| DI | Hilt |
| Persistence | Room (SQLite) |
| Foreground detection | `UsageStatsManager` (query-events polling) |
| Overlay | `WindowManager` + `TYPE_APPLICATION_OVERLAY` hosting a `ComposeView` |
| Min / Target SDK | 26 / 34 (Android 14) |
| JDK | 17 |

---

## 🗂️ Project structure

```
FlipToFocus/
├── gradlew · gradlew.bat · gradle/wrapper/        # Gradle 8.6 wrapper (committed)
├── settings.gradle.kts · build.gradle.kts
├── gradle/libs.versions.toml                      # Version catalog
└── app/
    ├── build.gradle.kts · proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/                                    # strings, themes (light+night), colors, launcher icons
        └── java/com/fliptofocus/
            ├── FlipToFocusApp.kt · MainActivity.kt
            ├── di/          # DatabaseModule · RepositoryModule · AppModule
            ├── domain/      # model/ + repository/ (interfaces)
            ├── data/        # local/{RoomData, Mappers} · repository/ impls · InstalledAppsProvider
            ├── service/     # AppBlockerService · OverlayManager · ForegroundAppMonitor · BootReceiver
            ├── sensor/      # SensorChallengeManager · ChallengeState
            ├── overlay/     # FocusOverlayUI
            ├── ui/          # onboarding · home · blocklist · settings · navigation · theme
            └── util/        # Constants · PermissionUtils
```

---

## 🔐 Permissions & the grant flow

FlipToFocus uses two permissions that Android intentionally gates behind a Settings screen (they cannot be granted by a normal runtime dialog). The app shows a **prominent disclosure** explaining *why* **before** sending you to the OS screen — this is both good UX and a Google Play requirement.

The onboarding flow (`ui/onboarding/`) walks the user through this in order:

1. **Welcome** — what the app does.
2. **Usage Access** — disclosure card, then a **Grant Permission** button that opens
   `Settings.ACTION_USAGE_ACCESS_SETTINGS`. The user enables *FlipToFocus* in the
   "Usage access" list. Used only to read the current foreground app package on-device.
3. **Draw over other apps** — disclosure card, then a **Grant Permission** button that opens
   `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`. Enables the focus overlay.
4. **All set** — start blocking.

On Android 13+ the app also requests the runtime **notification** permission (`POST_NOTIFICATIONS`) so the foreground service can show its required persistent notification.

| Permission | Manifest name | Why |
|---|---|---|
| Usage Access | `PACKAGE_USAGE_STATS` | Detect which app is in the foreground (on-device only). |
| Draw over other apps | `SYSTEM_ALERT_WINDOW` | Show the focus overlay on top of a blocked app. |
| Foreground service | `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the monitoring loop alive reliably. |
| Notifications | `POST_NOTIFICATIONS` | Persistent notification for the foreground service. |
| Boot completed | `RECEIVE_BOOT_COMPLETED` | Re-arm blocking after reboot *only if* the user left blocking enabled. |

> There is **no `INTERNET` permission** and **no `QUERY_ALL_PACKAGES`** — the installed-app list is built with a scoped `queryIntentActivities(ACTION_MAIN / CATEGORY_LAUNCHER)` query.

---

## ⚙️ How it works

- `AppBlockerService` (a `specialUse` foreground service) polls the foreground package roughly every 400 ms via `ForegroundAppMonitor` (which reads recent `UsageStatsManager` events — no aggregation, low overhead).
- When a **blocked** app comes to the foreground and blocking is enabled, the service starts `SensorChallengeManager` and shows the `FocusOverlayUI` bound to its live `StateFlow<ChallengeState>`.
- The sensor manager marks the position valid only when the phone is flat & face-down and still; any significant motion (accelerometer jerk or gyroscope rotation) **resets the countdown**.
- When the countdown completes, the session is marked `COMPLETED`, the overlay is removed, and that app is usable until it leaves the foreground.
- A low-emphasis **"End session early"** control lets the user abandon honestly (logged as `ABANDONED`).

---

## 🚀 Build & run (this machine or a fresh clone)

**Prerequisites**
- Android Studio (Koala 2024.1+ recommended)
- JDK 17 (Android Studio bundles a compatible JDK)
- Android SDK Platform 34 + Build-Tools 34 (install via Android Studio → SDK Manager)

**Steps**
1. Open the project folder in Android Studio (**File → Open**), or clone it first (below).
2. Let Gradle sync. The committed wrapper pins **Gradle 8.6**; Android Studio downloads it automatically.
3. Select a device/emulator running **Android 8.0 (API 26) or newer** — API 34 recommended.
4. Click **Run ▶**.
5. Complete onboarding to grant **Usage Access** and **Draw over other apps**, then add apps to your blocklist and enable blocking.

> **Testing the overlay:** the challenge fires when a *blocked* app is opened, so add e.g. a browser or a social app to your blocklist, enable blocking, then open it.

---

## 📦 Cloning to another device (step by step)

On the new machine:

```bash
# 1. Clone your repository
git clone <your-repo-url>
cd FlipToFocus

# 2. (macOS/Linux only) make the Gradle wrapper executable
chmod +x gradlew

# 3. Build from the command line (optional sanity check) ...
./gradlew assembleDebug          # macOS/Linux
gradlew.bat assembleDebug        # Windows

# ... or just open the folder in Android Studio and press Run.
```

`local.properties` (which points to the local Android SDK) is intentionally **git-ignored** — Android Studio regenerates it on first open. If you build purely from the CLI, create it with:

```
sdk.dir=/absolute/path/to/Android/Sdk
```

---

## 🛡️ Google Play compliance notes

- **No hostile anti-uninstall / evasion behavior.** FlipToFocus never overlays or blocks the system Settings app, never uses Device Admin / Accessibility lock, and never prevents uninstalling or revoking permissions. The overlay appears **only** over user-selected apps, Home/Recents always work, and the "End session early" control preserves user autonomy. (This is a deliberate design choice: trapping the user in Settings violates the [Device & Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/9888379) and is a common stalkerware pattern — it would cause rejection.)
- **Prominent disclosure** is shown before requesting `PACKAGE_USAGE_STATS` and `SYSTEM_ALERT_WINDOW`.
- **Offline-only:** no `INTERNET`, no analytics/networking dependencies.

### Foreground-service "special use" declaration

The app declares:

```xml
<service
    android:name=".service.AppBlockerService"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="app_focus_blocking_overlay" />
</service>
```

Android 14 requires a Play Console justification for `FOREGROUND_SERVICE_SPECIAL_USE`. In
**Play Console → App content → Foreground service permissions**, submit text such as:

> *FlipToFocus runs a persistent foreground service to continuously detect, on-device, when a user-selected "distracting" app enters the foreground, so it can immediately present the focus overlay and enforce the offline unlock challenge the user configured. This requires a long-lived foreground service that is not covered by the standard foreground-service types (it is neither media playback, location, data sync, nor any other predefined category). All processing is local; no data is transmitted.*

---

## 🧩 Configuration

Defaults live in `util/Constants.kt` (poll interval, default challenge minutes, default suggested blocklist, notification IDs, service actions). Runtime settings (duration, face-down requirement, motion sensitivity, blocking on/off) are user-editable in the **Settings** screen and persisted in Room (`AppConfig`).

---

## 📄 License

Add your preferred license here (e.g. MIT/Apache-2.0) before publishing.
