# Care Management System — Android App

Native Android APK that wraps the [Care Management System PWA](https://github.com/Dracon420/care-schedule) and includes a **true home screen widget** — both bundled in one install.

---

## What's inside the APK

| Component | What it does |
|---|---|
| **Main App** | Fullscreen WebView loading your Netlify URL — identical to the browser experience, including Google Calendar sync, notifications, and offline support |
| **Home Screen Widget** | 3×3 native Android widget — shows **START** when idle, switches to **RUNNING** + next task + countdown once a schedule is active |
| **JavaScript Bridge** | `window.AndroidBridge` injected into the WebView — when you tap START or STOP in the app, it instantly updates the widget via Android SharedPreferences |
| **Boot Receiver** | Restores widget state after the phone restarts |

---

## One-time setup before building

Open `app/src/main/java/com/dracon420/caremanagement/MainActivity.kt` and set the URL in `NETLIFY_URL` (near the top of the class):

```kotlin
const val NETLIFY_URL = "https://YOUR-APP.netlify.app"
```

Replace `YOUR-APP` with your actual Netlify subdomain (e.g. `https://my-care-app.netlify.app`).

That is the **only change** needed. Everything else is ready to build.

---

## How to build the APK

### Step 1 — Install Android Studio
Download free from [developer.android.com/studio](https://developer.android.com/studio).  
During setup, let it install the default SDK (Android 14 / API 34).

### Step 2 — Open the project
- Launch Android Studio
- Click **Open** → select this `CareManagementApp` folder
- Wait for Gradle to sync (first time downloads dependencies, ~2–5 min)

### Step 3 — Build the APK
- Menu → **Build → Build Bundle(s) / APK(s) → Build APK(s)**
- Android Studio will show a notification when done: **"Build successful"**
- Click **locate** in that notification to find the `.apk` file at:
  ```
  app/build/outputs/apk/debug/app-debug.apk
  ```

### Step 4 — Install on your phone
1. Copy `app-debug.apk` to your phone (USB, email, Google Drive — any method)
2. Open the file on your phone
3. Android will ask to **"Allow from this source"** — tap Allow
4. Tap **Install**

---

## Adding the widget to your home screen

1. Long-press an empty spot on your home screen
2. Tap **Widgets**
3. Find **Care Management System** in the list
4. Long-press it and drag it to your home screen
5. Release to place it (resize handles appear — drag to fill a 3×3 area)

The widget starts in **START** mode. Tap it to open the app and launch a schedule. Once started, the widget switches to **RUNNING** and shows the next upcoming task.

---

## How the bridge works

```
PWA (JavaScript)                    Android (Kotlin)
─────────────────                   ─────────────────────────────
launchDay() saves state
  → window.AndroidBridge            WebAppInterface.updateSchedule()
      .updateSchedule(json)    →      saves to SharedPreferences
                                       triggers widget redraw
                                         ↓
                                    CareScheduleWidget.updateWidget()
                                       reads SharedPreferences
                                       updates RemoteViews on screen
```

The widget always reflects the current schedule state within seconds of a change. After a phone restart, the `BootReceiver` redraws the widget from the persisted SharedPreferences — no need to reopen the app.

---

## Updating the app

Because the app loads from Netlify, **you never need to rebuild the APK** for feature updates.

1. Edit `Index.html` locally
2. Re-upload folder to Netlify
3. Open the app on your phone — it fetches the latest version automatically

Only rebuild the APK if you change something in the native Android layer (widget layout, bridge methods, etc.).

---

## File structure

```
CareManagementApp/
├── app/src/main/
│   ├── java/com/dracon420/caremanagement/
│   │   ├── MainActivity.kt        ← WebView host + Netlify URL config
│   │   ├── WebAppInterface.kt     ← JS bridge (window.AndroidBridge)
│   │   ├── CareScheduleWidget.kt  ← Widget provider + rendering logic
│   │   └── BootReceiver.kt        ← Restores widget after reboot
│   ├── res/
│   │   ├── layout/
│   │   │   ├── activity_main.xml  ← Full-screen WebView layout
│   │   │   └── widget_layout.xml  ← Widget UI (START / RUNNING)
│   │   ├── xml/widget_info.xml    ← Widget size + update interval
│   │   └── drawable/              ← Widget background + button shapes
│   └── AndroidManifest.xml
├── build.gradle
├── settings.gradle
└── README.md                      ← This file
```

---

## License

[PolyForm Noncommercial 1.0.0](LICENSE.md). You're free to use, study and change it for personal, hobby, educational and other **noncommercial** purposes. **Commercial use, including selling it or running it as a paid service, isn't allowed** without a separate licence from the author; open an issue to ask.

> This app is a scheduling aid, not a medical device. Always follow the care plan from the patient's medical team.
