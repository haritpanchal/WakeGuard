# WakeGuard

WakeGuard is a personal Wear OS morning alarm for a Samsung Galaxy Watch 8. It rings on the watch, then keeps ringing until a conservative motion heuristic says the wearer is likely awake, or until the wearer stops it. It is not a general alarm clock, timer, or reminder app, and it does not read or change Samsung Clock alarms.

This repository starts with a feasibility note and a small on-watch proof of concept. The proof of concept exists to find out whether an exact alarm and a few minutes of screen-off accelerometer data actually work under One UI Watch. It is not the finished app.

## Features in the proof of concept

- Schedule one exact alarm with `AlarmManager.setAlarmClock()`.
- Fire that alarm after the app process has been force-stopped.
- Vibrate and post a notification with a Stop action.
- Sample the accelerometer for five minutes and write a local log.
- Schedule one repeat, then exit. Stop cancels that repeat.
- Unit tests for the wakefulness heuristic.

The finished app, including a daily wake-up time, snooze, persistence, and reboot recovery, is later work. It is not in this tree yet.

## Supported device

- Samsung Galaxy Watch 8.
- Wear OS 6 (Android 16, API 36) or Wear OS 7 (Android 17, API 37).
- `minSdk` 34, `targetSdk` 36. `compileSdk` is 37 because current Wear Compose requires it.
- No phone companion, account, or network service.

A Wear OS emulator can build and show the test screen. It does not reproduce Samsung background limits. Alarm delivery and screen-off motion are judged on the physical watch.

## Development setup

- macOS on Apple Silicon.
- JDK 17 (`brew install openjdk@17`).
- Android Studio current stable.
- Android SDK with platforms 36 and 37, platform-tools, build-tools, the emulator, and Wear OS arm64 system images for those platforms.

`local.properties` points at the SDK on this machine and is not committed.

## Build

```bash
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

## Install on the watch

Enable Developer options and Wireless debugging on the watch, then:

```bash
adb pair <watch-ip>:<pair-port>
adb connect <watch-ip>:<debug-port>
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

After a test run, pull the log:

```bash
adb exec-out run-as com.haritpanchal.wakeguard cat files/wakeguard-poc.log
```

## Permissions

| Permission | Why |
| --- | --- |
| `USE_EXACT_ALARM` | Schedule the alarm while the process is not running. Granted at install for an alarm clock. |
| `POST_NOTIFICATIONS` | Show the alarm notification and the Stop action. |
| `VIBRATE` | Alarm haptics. |
| `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the process alive only during the five-minute monitoring window. |
| `WAKE_LOCK` | Partial wake lock only if the wake-up accelerometer is missing or goes silent with the screen off. |
| `RECEIVE_BOOT_COMPLETED` | Declared for the later reboot path. The proof of concept does not register a boot receiver. |

Not requested: body sensors, Health Connect, activity recognition, overlay, network, or a battery-optimization exemption.

On the watch, also allow notifications for WakeGuard. In Galaxy Wearable, open Watch settings, Apps, WakeGuard, and turn on Allow background activity. Test Bedtime mode and Theater mode separately. Those modes can suppress third-party vibrations.

## Background execution

The alarm is held by the system. The app does not stay open overnight and does not listen to sensors until the alarm fires. Wear OS does not support full-screen intents, and Android may block opening an activity from the alarm receiver. Vibration plus the notification Stop action is the path that has to work. Whether the alarm screen opens by itself is recorded in the log.

## Wakefulness heuristic

A still wrist already measures about 9.8 m/s² of gravity, so magnitude alone misses a rollover. The detector high-passes gravity. A sample is active when linear acceleration exceeds 0.8 m/s² or the gravity vector turns faster than 30 deg/s. Twitches shorter than 0.6 s are ignored. Bursts separated by less than 1.5 s merge.

The detector calls the wearer awake only when:

- Stop is pressed, or
- at least three valid bursts span at least 40 seconds and total at least 12 seconds of activity, or
- one burst stays active for at least 25 seconds.

One short burst, such as turning over, is not enough. Silence is not enough. This is a heuristic. Restless sleep can look awake, and lying still while awake will not. The proof of concept logs the decision. It does not claim to know that the wearer is awake.

## Testing

Unit tests cover silence, a twitch, one rollover, three separated bursts, one sustained burst, and Stop:

```bash
./gradlew :app:testDebugUnitTest
```

Physical cases, each after force-stopping the app:

1. Screen off, app not open.
2. Watch idle for 10 minutes beforehand.
3. Bedtime mode on, then off.
4. Screen off for the whole five-minute sensor window.
5. Stop from the notification.
6. One repeat fires without reopening the app.

Pass bar: the alarm fires within a few seconds in cases 1–3, vibration runs, at least one motion sample arrives with the screen off, Stop cancels the repeat, and the service is not running afterward.

## Known limitations

- Exact delivery under One UI Watch Bedtime mode is unproven until the watch tests pass.
- The alarm activity may not open from the background. Stop on the notification is the guaranteed control in this phase.
- Screen-off accelerometer delivery depends on a wake-up sensor or a short partial wake lock. If neither delivers samples, motion detection is not viable and work stops for a decision.
- Reboot recovery, time-zone changes, snooze, and a daily schedule are not implemented yet.
- Motion samples are not stored. The log keeps counters and a few diagnostic lines.

See [docs/phase-1-feasibility.md](docs/phase-1-feasibility.md) for the investigation that led to this shape.

## Roadmap

1. Feasibility note and this proof of concept.
2. Tune the heuristic on the watch and record battery impact.
3. Daily alarm configuration, alarm screen, snooze, persistence, and cancellation.
4. Reboot, clock changes, and duplicate-alarm handling.
5. Wear OS UI polish.
6. Sideload distribution only, and only with explicit approval. No public release.
