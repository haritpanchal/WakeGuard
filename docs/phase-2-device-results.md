# Phase 2 device results

Date: 2026-11-25.

The six physical cases were not run. `adb devices -l` listed no devices, and `adb mdns services` found no wireless-debugging advertisement. The Galaxy Watch 8 API level is therefore unknown. No alarm delivery, vibration, or screen-off accelerometer result exists yet.

This is a stop. The proof of concept is built and its unit tests pass, but the watch has not confirmed that `setAlarmClock()` or screen-off motion works under One UI Watch. Do not treat the emulator as that confirmation. No workaround was added.

## What is ready on the Mac

- JDK 17.0.21.1 at `/opt/homebrew/opt/openjdk@17`
- Android Studio at `/Applications/Android Studio.app`
- Android SDK at `~/Library/Android/sdk`
- Platforms 36 and 37.0, build-tools 37.0.0, platform-tools, emulator
- Wear OS 6.0 and Wear OS 7.0 arm64 system images
- AVDs `wakeguard_wear_36` and `wakeguard_wear_37`, both Wear OS Large Round
- `./gradlew :app:assembleDebug :app:testDebugUnitTest` succeeded
- `WakefulnessDetectorTest`: 6 tests, 0 failures

`adb` is `~/Library/Android/sdk/platform-tools/adb`.

## How to run the cases once the watch is paired

On the watch, enable Developer options and Wireless debugging. Pair from the Mac:

```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
adb pair <watch-ip>:<pair-port>
adb connect <watch-ip>:<debug-port>
adb shell getprop ro.build.version.sdk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then, for each case, open WakeGuard, tap **Fire in 2 minutes**, and leave the app. Swipe it away. Do not use Settings → Force stop for the delivery test: Android clears `AlarmManager` alarms for a force-stopped package and will not run it again until the next manual launch. That would fail a correct alarm app. The closed-app test is swipe-away, with the screen off.

After a run:

```bash
adb exec-out run-as com.haritpanchal.wakeguard cat files/wakeguard-poc.log
```

Cases still required, in order:

1. Screen off, app not in the foreground.
2. Watch idle for 10 minutes beforehand.
3. Bedtime mode on, then the same case with Bedtime mode off.
4. Screen off for the whole five-minute sensor window.
5. Stop from the notification cancels the repeat.
6. The repeat fires without reopening the app.

Pass bar: the alarm fires within a few seconds in cases 1–3, vibration runs, at least one motion sample arrives with the screen off, Stop cancels the repeat, and the service is not running afterward. A miss stops further product work until the limitation is written down.
