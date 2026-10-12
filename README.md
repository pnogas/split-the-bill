# split-the-bill

Mobile app for figuring out how to split a bill at a restaurant under complicated scenarios.

## Overview

Android-first Kotlin Multiplatform project. A photo of a receipt will be read with OCR, then the items, tax, and tip are split across the people at the table. The iOS app comes later; its target is already declared on the shared module.

- `shared` — bill model, `split`, and the `OcrEngine` interface. Targets: `commonMain`, `androidMain`, `iosMain`.
- `androidApp` — Compose Multiplatform shell with a placeholder screen.

Money is integer cents (`Cents`). Each line total (`price * quantity`) is split evenly across the people assigned to it. Tax and tip follow each person's share of the item subtotal. Leftover cents use the largest-remainder method, with ties broken by person id, so the shares add up to the bill total. An item assigned to nobody is an error the UI can show.

## Build

Requires JDK 17 or newer and an Android SDK with platform 37.

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew build
```

`local.properties` is gitignored. `./gradlew build` compiles the shared module, runs its tests on the Android host, and builds the Android app.

iOS targets (`iosArm64`, `iosSimulatorArm64`, `iosX64`) compile on macOS. Other hosts skip them (`kotlin.native.ignoreDisabledTargets`).

Shared tests alone:

```bash
./gradlew :shared:testAndroidHostTest
```
