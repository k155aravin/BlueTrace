# BlueTrace - Full Project Context for Codex

Start every BlueTrace session by reading this file first.

---

## What Is BlueTrace?

BlueTrace is an Android safety-awareness app that helps regular people notice recurring Bluetooth devices across multiple locations.

The core question is simple:

> Is the same unknown Bluetooth device showing up everywhere I go?

BlueTrace scans nearby Bluetooth devices at three different locations. If the same unknown device appears repeatedly during the sweep, the app surfaces it as a watch or alert pattern.

Tagline:

> You can change your Bluetooth ID. You cannot change your heartbeat.

Owner: Aran Kumar / k155aravin / Montreal, QC  
GitHub: https://github.com/k155aravin/BlueTrace  
Company: Aran Multi Services Inc. / AMS Software  
Package: `com.k155aravin.bluetrace`  
Language: Kotlin + Jetpack Compose  
Min SDK: 26

---

## Product Philosophy

BlueTrace should feel like a calm safety tool for normal people.

The app should not feel like a hacker console, cockpit, or technical lab. The engine can be smart, but the user experience should stay simple:

1. Open app.
2. Scan here.
3. Move.
4. Scan again.
5. Move.
6. Scan a third time.
7. App says clearly: all clear, watch, or alert.

Regular users should not need to understand MAC addresses, BLE packets, RSSI, advertising intervals, or confidence math.

---

## Core Concept

### 3-Location Sweep

1. User scans at Location A.
2. User moves and scans at Location B.
3. User moves again and scans at Location C.
4. Devices seen at 2 locations become watch items.
5. Devices seen at all 3 locations become alert patterns, unless trusted or baseline-matched.

### Why 15 Minutes Matters

Modern phones and some Bluetooth devices rotate identifiers over time. Finishing the 3-location sweep inside about 15 minutes makes repeated matches more meaningful.

BlueTrace currently tracks a 15-minute clean sweep window and warns when the sweep passes that window.

### Heartbeat Fingerprinting

Every BLE device broadcasts at an interval. This rhythm can act like a soft signal fingerprint.

The long-term idea:

- MAC address can rotate.
- Device name can be generic.
- But advertising rhythm, manufacturer data, TX power, and packet behavior can help identify patterns.

Known example intervals discussed during design:

- Tile tracker: about 498 ms
- AirTag: about 2000 ms
- AirPods Pro: about 999 ms
- Cheap BLE tag: about 100 ms
- Modern iPhone: intentionally randomized and difficult to fingerprint

Current app status: heartbeat measurement exists in the prototype, but the product should present it carefully and avoid overclaiming certainty.

---

## Confidence Score

The app displays a confidence-style score based on available signals.

Signals include:

- location match count
- heartbeat measurement
- signal count
- TX power availability
- manufacturer data

Important implementation note:

The current alert logic is primarily driven by location matching:

- 2 locations = Watch
- 3 locations = Alert

The confidence score helps explain the pattern, but it is not the only trigger for alerts.

---

## Current App State

Built and working as of May 2026:

- Real BLE scanning on Android
- Manual 3-location sweep workflow
- Watch/Alert logic based on repeated appearances
- 15-minute sweep timer
- Movement mode selector: walking, driving, indoor, manual
- Collapsible sweep controls to keep the main scan screen usable
- Location detail screen for reviewing one scan location at a time
- Final results screen after the third scan
- Trusted Devices screen
- Scan nearby devices to trust
- Clear feedback after tapping Trust
- Trusted devices are hidden from main security scan results
- Quiet-place baseline profile feature
- Clear baseline profile
- Bluetooth-off warning
- Custom BlueTrace app icon
- Dark security UI
- Heartbeat interval measurement
- Distance estimation via RSSI
- UI capped to avoid crashes from too many live devices
- GitHub repo and README

Not built yet:

- Device detail screen
- Evidence export
- Map view
- Onboarding flow
- Background auto-scanning
- Push notifications

---

## Design System

Colors:

```kotlin
DarkBg      = Color(0xFF0D0D0F)
DarkCard    = Color(0xFF111114)
DarkBorder  = Color(0xFF1E1E24)
GreenColor  = Color(0xFF4ADE80)
RedColor    = Color(0xFFEF4444)
YellowColor = Color(0xFFFACC15)
BlueColor   = Color(0xFF60A5FA)
TextPrimary = Color(0xFFE0E0E0)
TextMuted   = Color(0xFF8A8A92)
```

Style:

- dark, serious, safety/security feel
- flat dark cards
- red for alert
- yellow for watch/caution
- green for safe/trusted/scanning
- blue for info/baseline/heartbeat
- monospace for MAC addresses
- cards around 12 dp radius

---

## Current File Structure

```text
app/src/main/java/com/k155aravin/bluetrace/
├── MainActivity.kt
├── TrustedDevicesScreen.kt
└── ui/theme/
    ├── Color.kt
    ├── Theme.kt
    └── Type.kt

app/src/main/
├── AndroidManifest.xml
└── res/
    ├── mipmap/
    └── values/

README.md
BLUETRACE_CONTEXT.md
build.gradle.kts
gradle.properties
settings.gradle.kts
```

Important files:

- `MainActivity.kt`: scan engine, sweep state, main UI, trusted/baseline wiring
- `TrustedDevicesScreen.kt`: trusted devices UI and baseline/trust setup UI
- `README.md`: public GitHub-facing project explanation
- `BLUETRACE_CONTEXT.md`: internal working context for future sessions

---

## Technical Notes

Permissions:

```xml
BLUETOOTH
BLUETOOTH_ADMIN
BLUETOOTH_SCAN
BLUETOOTH_CONNECT
ACCESS_FINE_LOCATION
ACCESS_COARSE_LOCATION
FOREGROUND_SERVICE
```

Scanning settings currently used:

```text
MAX_TIMESTAMPS_PER_DEVICE = 80
MAX_VISIBLE_DEVICES = 25
UI_REFRESH_MS = 1000 ms
SCAN_DURATION_MS = 30000 ms
SWEEP_WINDOW_MS = 15 minutes
```

Manufacturer detection:

```text
76  = Apple
6   = Microsoft
117 = Samsung
343 = Tile
```

Distance estimation uses RSSI and TX power when available. Treat distance as approximate only.

---

## Known Lessons / Recent Fixes

- Bluetooth-off state now shows a clear warning instead of pretending to scan.
- Trusted devices should be added only from the Trusted screen, not the main scan.
- Trusted devices are now filtered out of the main security results.
- After tapping Trust, the user gets visible confirmation.
- UI caps visible devices to avoid list overload and crashes.
- Distance calculation was made defensive to avoid invalid values.
- The location detail experiment is now accepted as the main UI flow.
- The old stable layout is tagged as `stable-before-location-detail`.
- Final results needed a scroll fix after the third scan and is now scrollable.
- Live scan feedback was added so the app does not feel frozen during a scan.

---

## Next Build Priorities

Keep the app simple. Do not add large new systems until the core flow is tested.

Recommended order:

1. Real-world testing of trusted devices and 3-location sweep.
2. Device detail screen.
3. Simple export/share report.
4. Onboarding.
5. Map/history later.
6. Background scanning later.

### 1. Device Detail Screen

Tap a device card to show:

- name
- MAC
- type
- manufacturer
- confidence score
- locations detected
- first seen / last seen
- heartbeat interval
- RSSI / distance estimate
- trust button if appropriate

### 2. Evidence Export

Future export should generate a simple report with:

- date/time
- scan locations
- alert/watch devices
- confidence score
- safety disclaimer

PDF can come later. Text export is enough first.

### 3. Final Results Screen Polish

After the third scan, show a clear result:

- All clear
- Watch
- Alert
- trusted devices ignored
- baseline matches ignored
- locations scanned
- start new sweep button

---

## Google Play Checklist

Technical:

- final results screen
- onboarding
- evidence/export
- privacy policy
- stability testing on multiple Android devices
- permissions explained clearly

Store:

- short description
- long description
- screenshots
- feature graphic
- content rating
- Google Play Developer account

---

## Working Agreement For Codex

Codex should:

1. Read this file at the start of BlueTrace work.
2. Read `MainActivity.kt` and `TrustedDevicesScreen.kt` before edits.
3. Keep the UI simple and safety-focused.
4. Avoid overclaiming detection certainty.
5. Build/test before finalizing changes.
6. Push to GitHub after meaningful stable checkpoints, not after every tiny experiment.

Last updated: May 2026
