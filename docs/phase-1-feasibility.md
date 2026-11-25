# Phase 1 — Feasibility

Date: 2026-11-25. This note records what was known before any on-watch run. Device results, once they exist, belong in a later test log. Do not treat this file as evidence that the Galaxy Watch 8 already passed.

## Environment

The development Mac is Apple Silicon, macOS Darwin 27.2. At the start of Phase 1 it had Git, Homebrew, and the GitHub CLI, and it did not have a JDK, Android Studio, an Android SDK, or `adb`. The watch was not reachable. About 53 GB was free after a disk cleanup, which is enough for Android Studio, SDK Platforms 36 and 37, and Wear OS emulator images.

The GitHub remote for this project is the `haritpanchal` account. The CLI also has `haritpanchal-md`; that account is not used here.

Galaxy Watch 8 shipped on Wear OS 6 (Android 16, API 36). One UI 9 Watch, based on Wear OS 7 (Android 17, API 37), began rolling out to Watch 8 in November 2026. The app uses `minSdk` 34 and `targetSdk` 36 so it runs on both without opting into Android 17 behavior changes. `compileSdk` is 37 because Wear Compose 1.7 requires it. Confirm the watch with:

```bash
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.version.release
```

## Verdict

A watch-only morning alarm is conditionally feasible. `AlarmManager.setAlarmClock()` is the correct API: the system holds the alarm, it wakes the device for delivery, and it can start a foreground service while the app is in the background. A separate app's alarms do not read or edit Samsung Clock.

Two behaviors are not proven by documentation and have to be measured on this watch:

1. The alarm fires while the app is force-stopped, the screen is off, and the watch is in its normal overnight configuration, including Bedtime mode.
2. The accelerometer keeps delivering samples for about five minutes after that, with the screen off.

If either fails, stop and write down the limitation before trying a workaround.

## Alarm scheduling

Use `setAlarmClock()`. It implies `RTC_WAKEUP` and is delivered in Doze. `setExactAndAllowWhileIdle()` is the fallback only if this watch delays `setAlarmClock()`. Do not use an in-process timer or WorkManager for the wake-up time.

Declare `USE_EXACT_ALARM`. For an alarm-clock app it is granted at install, including sideload. Check `canScheduleExactAlarms()` before scheduling. `SCHEDULE_EXACT_ALARM` is the fallback if the watch refuses the use-permission; that permission is not granted by default and needs the Alarms & reminders setting.

Cancel and reset one `PendingIntent` request code whenever the alarm changes so a restart cannot stack duplicates.

What is still unproven on this hardware:

- Delivery after the app is swiped away.
- Delivery after reboot. `RECEIVE_BOOT_COMPLETED` is the standard hook, but One UI can delay it, and a locked watch will not start credential-encrypted apps until the first unlock.
- Clock and time-zone changes. Later phases should listen for `ACTION_TIME_CHANGED`, `ACTION_TIMEZONE_CHANGED`, and `ACTION_BOOT_COMPLETED`, then rewrite the alarm from local storage.
- Older Samsung builds delayed third-party exact alarms unless the package name contained "alarm" or "alert". The package stays `com.haritpanchal.wakeguard`. If Phase 2 shows a multi-minute delay, report it before any rename.

Galaxy Wearable has a per-app **Allow background activity** switch. Turn it on for WakeGuard and record whether delivery depends on it.

## Opening an alarm screen

Wear OS does not support `setFullScreenIntent()` or `USE_FULL_SCREEN_INTENT`. Android also blocks `startActivity()` from a background receiver. `setAlarmClock()` is not an exception to that rule.

The reliable surface is vibration from a short-lived foreground service, plus a high-priority `CATEGORY_ALARM` notification with a Stop action. The proof of concept also tries to open an activity and logs whether that launch was allowed. A posted notification often lights a Wear OS screen. That is common behavior, not a promise under Bedtime mode or Theater mode. Third-party reports say Bedtime mode can suppress vibrations from apps that are not Samsung Clock.

If the activity never opens, Stop on the notification still satisfies the requirement that the wearer can always end the alarm.

## Motion sensing

The accelerometer needs no runtime permission. Declare `android.hardware.sensor.accelerometer`. Do not request heart rate or Samsung Health.

Listen only after the alarm fires. Unregister on awake, stop, or the end of the window. Overnight listening is out of scope.

Non-wake-up sensors stop when the application processor sleeps. Request `getDefaultSensor(TYPE_ACCELEROMETER, true)` first. If that returns null, use the normal accelerometer and a partial wake lock held only for the window.

`foregroundServiceType="shortService"` is capped at about three minutes, so it cannot cover a five-minute window. The proof of concept uses `specialUse` with subtype `wakeguard-alarm`. If the OS rejects that type, log the `SecurityException` and stop for a decision. Sideloading does not require a Play declaration, but the platform can still refuse the service type.

Five minutes at `SENSOR_DELAY_NORMAL` is a bounded cost. Battery impact is measured later, on the watch, not assumed.

## Heuristic

Implemented in `WakefulnessDetector`. Samples are processed in memory and discarded.

- Estimate gravity with an exponential low-pass.
- Linear acceleration is the sample minus that gravity estimate.
- A sample is active when linear acceleration exceeds 0.8 m/s² or the gravity-vector angle changes faster than 30 deg/s.
- Gaps under 1.5 s stay in the same burst. Bursts shorter than 0.6 s are ignored.
- Awake when Stop is reported, or there are at least 3 valid bursts spanning at least 40 s with at least 12 s of active time, or one burst lasts at least 25 s.
- Not awake for silence, a single burst under 8 s, or total active time under 8 s.

Snooze is specified for the later app and is not part of this proof of concept. A future snooze cancels the window, schedules one new alarm, and resets counters. It is not an awake decision. The later app also needs a hard cap: 8 repeats after the first alarm, or 45 minutes from the first fire, whichever comes first.

False positives: restless sleep. False negatives: awake but lying still. The manual stop exists because of that.

## Permissions

Requested now:

- `USE_EXACT_ALARM`
- `POST_NOTIFICATIONS`
- `VIBRATE`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_SPECIAL_USE`
- `WAKE_LOCK`
- `RECEIVE_BOOT_COMPLETED` (declared only; no boot receiver in the proof of concept)
- `android.hardware.type.watch` and accelerometer as required features

Not requested: body sensors, activity recognition, overlay, storage, network, battery-optimization exemption.

Manual settings to test, not assume: notification permission, Allow background activity, Bedtime mode, Theater mode, wireless debugging.

## Architecture of the proof of concept

One Wear module, `:app`. No phone module and no network.

- `MainActivity` — one button schedules an alarm two minutes ahead. The tester then force-stops the app and turns the screen off.
- `AlarmScheduler` — cancel-then-set a single `setAlarmClock()` pending intent.
- `AlarmReceiver` — starts `AlarmSessionService`.
- `AlarmSessionService` — vibrates, posts Stop, tries to open `AlarmActivity`, samples motion for five minutes, writes `files/wakeguard-poc.log`, schedules one repeat two minutes after the window, then stops. Stop cancels the repeat.
- `MotionSampler` — sensor registration and gravity removal.
- `WakefulnessDetector` — pure Kotlin. Unit tests call this directly.

The later app adds DataStore, a boot receiver, snooze, and a daily schedule. Those are intentionally absent so this build can fail fast.

## Proof-of-concept pass bar

Each case starts from a force-stopped app.

1. Screen off, app not open.
2. Watch idle for 10 minutes beforehand.
3. Bedtime mode on, then the same case with Bedtime mode off.
4. Screen off for the whole five-minute sensor window.
5. Stop from the notification cancels the repeat.
6. The repeat fires without reopening the app.

Pass: the alarm fires within a few seconds of the target in cases 1–3, vibration runs, at least one motion sample arrives with the screen off, Stop cancels the repeat, and the service is gone afterward.

Any miss stops the project for a written limitation and a proposed alternative. No workaround is implemented in the same step.

## Risks

- One UI background limits, Bedtime mode, or Theater mode can delay or silence the alarm even when `setAlarmClock()` is used correctly.
- The alarm UI may be notification-only.
- If the wake-up accelerometer and a partial wake lock both fail to deliver samples, motion detection is not viable. The fallback to discuss, not to build immediately, is repeating the alarm on a timer and treating only Stop as awake.
- After reboot, reschedule can lag several minutes. A lock screen blocks it until unlock. The proof of concept does not test that path.
- The heuristic cannot reliably separate sleep from wakefulness.
