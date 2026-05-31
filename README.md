# BlueTrace

BlueTrace is an experimental Android safety and counter-surveillance tool that helps you notice recurring Bluetooth devices across multiple locations.

Software development: Aran Kumar and Nawshad Syed.

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

The app also tracks a 15-minute sweep window because some modern devices rotate Bluetooth identifiers over time. Finishing the sweep quickly makes recurring patterns more meaningful. BlueTrace also keeps the workflow simple: scan, move, scan, move, scan, then review the result.

## Current Features

- Real Bluetooth scanning on Android.
- 3-location sweep workflow.
- Watch and alert logic based on repeated appearances.
- 15-minute clean sweep timer.
- Movement mode selector for walking, driving, indoor, or manual sweeps.
- Collapsible sweep controls so the main scan screen does not feel crowded.
- Location detail screen for reviewing devices seen at each scan point.
- Final results screen after the third scan.
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

It combines a few ideas into one practical workflow:

- Recurring device review across different places.
- Location-based pattern thinking.
- A 15-minute sweep window for stronger short-term matching.
- Bluetooth signal timing research.
- Confidence scoring.
- Trusted device filtering.
- Baseline matching.
- A clear final result instead of a raw technical device dump.

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

The current main version includes the newer location-detail sweep flow. The previous stable version is saved in Git as:

```text
stable-before-location-detail
```

That checkpoint makes it possible to return to the older layout if needed.

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

## Documentation

- [User Guide](docs/USER_GUIDE.md)
- [FAQ](docs/FAQ.md)
- [Privacy Policy](docs/PRIVACY_POLICY.md)
- [Safety Disclaimer](docs/SAFETY_DISCLAIMER.md)
- [Support](docs/SUPPORT.md)
- [Google Play Store Prep](docs/PLAY_STORE_PREP.md)
- [License Notice](LICENSE.md)

## Roadmap And Future Ideas

Near-term:

- More real-world testing.
- Better explanation for watch vs alert.
- Scan history.
- Simple share/export report improvements.
- Store release polish.

Later:

- Map view.
- Stronger device fingerprinting.
- Smarter confidence scoring.
- Safer evidence-style reports.
- Trusted contact safety check-in, such as sending a preset SMS or location share when the user chooses to ask for help.
- Public landing page and controlled release.

These are possible future directions, not promised release features. Safety-related ideas need careful design, permissions review, and clear user control.

Have an idea or feedback?

```text
support@aranmultiservices.com
```

## Brand

BlueTrace is part of the AMS Software project family by Aran Multi Services Inc.

The public website will be updated later.
