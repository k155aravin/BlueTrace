# Bluetooth Heartbeat And Physical Signal Notes

Last updated: May 30, 2026

This document explains the Bluetooth "heartbeat" idea behind BlueTrace in plain language.

BlueTrace uses heartbeat-style timing as a supporting clue, not as proof of identity.

## What Bluetooth Heartbeat Means

Many Bluetooth Low Energy devices announce themselves by sending advertising packets over time.

The gap between these advertising events can create a rhythm. BlueTrace calls this rhythm a device heartbeat.

Examples:

- one device may advertise roughly every 100 ms
- another may advertise roughly every 500 ms
- another may advertise roughly every 1000-2000 ms

This timing can help describe the type of device and whether a similar signal pattern appears repeatedly.

## Why The Timing Is Not Perfect

Bluetooth LE advertising is not a perfectly steady metronome.

The Bluetooth specification allows advertising events to use a configured advertising interval, and the Link Layer adds a small random delay to advertising events. The Bluetooth SIG explains that this random `advDelay` is added so advertising events are perturbed in time and are less likely to collide with other devices.

That means a scanner should expect jitter. A measured heartbeat is approximate.

## Why Similar Devices Can Still Differ

Two phones from the same model, batch, and manufacturer can behave very similarly, but they are not physically identical radios.

Differences can come from:

- tiny manufacturing variations in radio hardware
- oscillator and clock tolerance
- RF front-end imperfections
- temperature and power conditions
- firmware behavior
- operating system scheduling
- random Bluetooth advertising delay
- surrounding radio noise and interference

Because of these effects, the measured timing and radio behavior of two similar devices may not be exactly the same.

## Physical-Layer Fingerprinting Background

Wireless research often discusses radio frequency fingerprinting or physical-layer fingerprinting.

The core idea is that real radio transmitters are not perfect. Manufacturing tolerances and analog component imperfections can create small signal differences that may be measured under the right conditions.

This does not mean a normal phone app can perfectly identify a specific device. Advanced physical-layer fingerprinting usually requires specialized measurement equipment, careful data collection, and statistical analysis.

For BlueTrace, the important takeaway is more modest:

Bluetooth behavior can contain soft physical and timing clues, even when high-level identifiers such as MAC addresses are randomized.

## How BlueTrace Uses This

BlueTrace currently treats heartbeat as a supporting signal.

The main BlueTrace logic is still location repetition:

- seen once: background noise
- seen twice: Watch
- seen at all three scan points: Alert pattern

Heartbeat can help explain a pattern, but it should not be treated as legal proof, identity confirmation, or a guarantee that two readings are the exact same physical device.

## Plain-English Summary

Bluetooth devices broadcast in rhythms.

Those rhythms are influenced by software settings, Bluetooth rules, random timing delay, and physical radio behavior.

Even two devices of the same model can have small differences because physical components are never perfectly identical.

BlueTrace uses these patterns carefully as awareness signals, not as absolute identification.

## References

- Bluetooth SIG, Bluetooth LE Primer: explains Bluetooth LE advertising and advertising delay.
- Bluetooth Core Specification, Low Energy Controller Link Layer: defines advertising interval and random advertising delay.
- MDPI Sensors, "Considerations for Radio Frequency Fingerprinting across Multiple Frequency Channels": discusses hardware imperfections as a basis for RF fingerprinting.
- UC San Diego / IEEE S&P, "Practical Obfuscation of BLE Physical-Layer Fingerprints on Mobile Devices": discusses BLE physical-layer fingerprints from radio manufacturing imperfections.
- NIST, "Robust Measurements for RF Fingerprinting with Constellation Patterns of Radiated Waveforms": discusses RF fingerprinting for device identification research.
