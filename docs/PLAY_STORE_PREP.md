# BlueTrace Google Play Store Prep

Last updated: May 30, 2026

This document tracks what BlueTrace needs before a Google Play release.

## Current Build Status

- Package name: `com.k155aravin.bluetrace`
- Version code: `1`
- Version name: `1.0`
- Min SDK: `26`
- Target SDK: `35`
- Release bundle builds successfully at:

```text
app/build/outputs/bundle/release/app-release.aab
```

Important: the release bundle still needs a proper Google Play upload signing key before production submission.

## Store Listing Draft

### App Name

BlueTrace

### Short Description

Notice recurring nearby Bluetooth devices across multiple locations.

### Full Description

BlueTrace is a safety-awareness app that helps you notice recurring nearby Bluetooth devices across multiple locations.

Run a simple three-location sweep:

1. Scan where you are.
2. Move somewhere else and scan again.
3. Move again and scan a third time.

BlueTrace reviews whether the same unknown Bluetooth device appeared repeatedly during the sweep.

Key features:

- real Bluetooth nearby-device scanning
- three-location sweep workflow
- Watch and Alert pattern review
- trusted devices for your own headphones, watches, laptops, car devices, or second phone
- quiet-place baseline profile
- live baseline scan feedback
- device detail review
- shareable text report
- local-first privacy approach

BlueTrace is not a tracking detector, legal proof tool, identity confirmation system, emergency service, or guarantee of personal safety. Bluetooth signals can be noisy, randomized, blocked, duplicated, or misread.

Use BlueTrace as a calm awareness tool. If you believe you are in immediate danger, leave the area and contact local emergency services.

Software development: Aran Kumar and Nawshad Syed.

BlueTrace is part of the AMS Software project family by Aran Multi Services Inc.

### Category

Suggested category: Tools

Alternative category: Lifestyle

### Contact Email

support@aranmultiservices.com

### Privacy Policy

Temporary public URL:

```text
https://github.com/k155aravin/BlueTrace/blob/main/docs/PRIVACY_POLICY.md
```

Before production, prefer publishing this policy on an AMS-controlled website URL.

## Data Safety Draft

These answers should be reviewed inside Play Console before submission.

### Does the app collect or share user data?

Draft answer: No data is sent to a developer server in the current version.

The app processes nearby Bluetooth signal data locally on the device. Trusted devices, baseline entries, and scan results are stored locally unless the user chooses to share a text report through Android's share sheet.

### Data types handled locally

- Nearby Bluetooth device address shown by Android
- Device name, when available
- Signal strength
- Manufacturer data, when available
- Scan timing information
- Trusted device entries
- Baseline profile entries

### Is data encrypted in transit?

No app server transmission in the current version.

### Can users request data deletion?

Local app data can be cleared by clearing app storage. The app also includes controls to clear trusted devices and baseline profile data.

### Is location collected?

BlueTrace does not build a user location history or upload location data.

On Android 11 and older, Android requires location permission for Bluetooth Low Energy scanning. On Android 12 and newer, BlueTrace uses nearby Bluetooth permissions and marks Bluetooth scan as not used for location.

## Permissions Review

Declared permissions:

- `BLUETOOTH`
- `BLUETOOTH_ADMIN`
- `BLUETOOTH_SCAN` with `neverForLocation`
- `BLUETOOTH_CONNECT`
- `ACCESS_FINE_LOCATION` with `maxSdkVersion="30"`
- `ACCESS_COARSE_LOCATION` with `maxSdkVersion="30"`

Not currently declared:

- background location
- foreground service
- SMS sending
- contacts

If trusted-contact SMS/location sharing is added later, permissions and privacy policy must be reviewed again before release.

## Required Store Assets

Need to prepare:

- app icon: already present in app resources
- feature graphic
- at least two phone screenshots
- short description
- full description
- privacy policy URL
- support email
- content rating questionnaire
- data safety questionnaire
- target audience questionnaire

Recommended screenshots:

1. Main three-location sweep screen
2. Live scan / movement mode screen
3. Trusted devices screen
4. Live baseline scan screen
5. Final result screen
6. Device detail screen

## Release Signing

Before production upload, create or choose a Google Play upload key.

Do not commit signing keys or passwords to GitHub.

The repo `.gitignore` excludes:

- `*.jks`
- `*.keystore`
- `keystore.properties`

## Remaining Release Steps

1. Create or confirm Google Play Developer account.
2. Create new app in Play Console.
3. Create/upload signing key configuration.
4. Build signed release AAB.
5. Upload AAB to internal testing first.
6. Complete store listing.
7. Complete content rating.
8. Complete Data Safety form.
9. Add privacy policy URL.
10. Add screenshots and feature graphic.
11. Run internal test on real devices.
12. Promote to closed/open testing or production when ready.
