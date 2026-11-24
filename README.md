# 24 Hours

A deliberately simple Android daily planner built around one circular 24-hour clock.

The project is configured to build a debug APK automatically on every push to `main` through GitHub Actions.

## First version

- A circular clock showing midnight through the full 24-hour day.
- Tap a time on the ring to add an activity for that hour.
- Tap an existing segment or activity to edit it.
- Choose a start time, end time, and color.
- Activities can cross midnight and are drawn on both sides of the dial.
- Prevent overlapping activities, including blocks that run across midnight.
- Save the daily plan locally on the device.
- No account, cloud sync, reminders, or task-management clutter.

## Build

The easiest way to get an APK is from GitHub Actions:

1. Open the **Actions** tab in this repository.
2. Select **Android Build**.
3. Select **Run workflow** (or push a commit to `main`).
4. Download the `24-hours-debug-apk` artifact from the completed run.

To build locally, use Android Studio with JDK 17+, Android SDK Platform 36, and Gradle 9.6.0. Then run `gradle assembleDebug` from the project root. The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Project details

- App label: **24 Hours**
- Application ID: `com.twentyfourhours.app`
- Minimum Android version: Android 8.0 (API 26)
- Initial version: `0.1.0`
