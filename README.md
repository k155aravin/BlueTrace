# BlueTrace

BlueTrace is an experimental Android safety tool that helps you notice recurring Bluetooth devices across multiple locations.

The idea is simple: if the same nearby device appears where you are, then appears again after you move, and appears again at a third location, that pattern deserves attention. BlueTrace turns invisible Bluetooth signals into a simple sweep result you can review.

## Why It Exists

Most people carry Bluetooth devices every day: headphones, smartwatches, trackers, car devices, laptops, tags, and phones. These devices can broadcast signals nearby, often without the user thinking about it.

BlueTrace is built around a practical safety question:

> "Is the same unknown Bluetooth device showing up everywhere I go?"

It is not meant to create panic. It is meant to give regular people a calm way to check their surroundings, spot patterns, and make better safety decisions.

## Core Idea

BlueTrace uses a 3-location sweep:

1. Scan at location one.
2. Move somewhere else and scan again.
3. Move again and scan a third time.

After the third scan, BlueTrace checks which devices appeared repeatedly.

- Seen once: normal background noise.
- Seen twice: watch item.
- Seen three times: alert pattern.

The app also tracks a 15-minute sweep window because some modern devices rotate Bluetooth identifiers over time. Finishing the sweep quickly makes recurring patterns more meaningful.

## Current Features

- Real Bluetooth scanning on Android.
- 3-location sweep workflow.
- Watch and alert logic based on repeated appearances.
- 15-minute clean sweep timer.
- Trusted Devices screen for your own devices.
- Quiet-place baseline profile for normal devices around you.
- Bluetooth-off warning so the app does not silently pretend it is scanning.
- Clear feedback when a trusted device is added.
- Custom BlueTrace app icon.
- Dark, serious security-style UI.

## Trusted Devices

Your own devices should not constantly trigger alerts.

BlueTrace lets you scan nearby devices in a quiet place and mark your own devices as trusted. Trusted devices are ignored during security sweeps.

Examples:

- Your headphones
- Your smartwatch
- Your laptop
- Your second phone
- Team or work devices you already know

## Baseline Profile

The baseline profile helps BlueTrace learn what is normal around you.

Use it somewhere controlled, like at home, in your car, or around your regular work devices. BlueTrace can then treat matching devices as expected background instead of suspicious.

## What Makes BlueTrace Interesting

BlueTrace is not just a simple Bluetooth list.

The product direction includes:

- Recurring device review across different places.
- Location-based pattern thinking.
- Bluetooth signal timing research.
- Confidence scoring.
- Trusted device filtering.
- Baseline matching.
- Future scan history and reports.

The long-term goal is a safety tool that regular people can carry quietly, without needing to understand Bluetooth, MAC addresses, or technical signal data.

## Important Safety Note

BlueTrace is an experimental learning and safety-awareness project.

It does **not** guarantee:

- threat detection
- identity confirmation
- exact distance measurement
- stalking detection
- legal proof
- personal safety

Bluetooth signals can be noisy, hidden, randomized, duplicated, or blocked. BlueTrace should be treated as an awareness tool, not as a replacement for judgment, emergency services, law enforcement, or professional safety support.

If you believe you are in immediate danger, leave the area and contact local emergency services.

## Current Status

BlueTrace is currently a working Android prototype.

It can be installed and tested on an Android phone through Android Studio. The app is being built slowly and carefully, with focus on reliability, simple user experience, and safety-first design.

## Tech Stack

- Android
- Kotlin
- Jetpack Compose
- Android Bluetooth LE scanning APIs
- SharedPreferences for local trusted/baseline data

## Running The Project

1. Open the project in Android Studio.
2. Let Gradle sync.
3. Enable Developer Options and USB Debugging on an Android phone.
4. Connect the phone with USB.
5. Press Run in Android Studio.

The app package is:

```text
com.k155aravin.bluetrace
```

## Roadmap

Near-term:

- More real-world testing.
- Cleaner final result screen after the third scan.
- Better explanation for watch vs alert.
- Scan history.
- Simple share/export report.

Later:

- Map view.
- Stronger device fingerprinting.
- Smarter confidence scoring.
- Safer evidence-style reports.
- Public landing page and controlled release.

## Brand

BlueTrace is part of the AMS Software project family by Aran Multi Services Inc.

Website:

```text
https://aranmultiservices.com/bluetrace
```
