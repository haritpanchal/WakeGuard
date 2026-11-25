# Session handover

Date: 2026-11-25. Private repo `https://github.com/haritpanchal/WakeGuard` on `main`. Do not publish it. Do not commit unless asked.

The last three commits are on `origin/main`: `449d35b` feasibility, `a653649` proof of concept, `e8d8e0d` watch-not-reachable note. Everything since then is uncommitted, including this file.

## Watch

Galaxy Watch 8, SM-L330, `fresh8blue`, Wear OS 7 / One UI 9, Android 17, API 37. App `minSdk` 34, `targetSdk` 36, `compileSdk` 37. Package `com.haritpanchal.wakeguard`.

Last wireless serial: `192.168.1.7:46057`. The port changes when wireless debugging restarts. `adb` is `~/Library/Android/sdk/platform-tools/adb` (not on the default PATH). `JAVA_HOME` is `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`.

macOS Local Network privacy blocks `adb` started from Cursor until Cursor is allowed. `adb install` has hung with an empty error; the reliable install is:

```bash
adb connect <ip:port>
adb -s <serial> push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/wakeguard.apk
adb -s <serial> shell pm install -r -t /data/local/tmp/wakeguard.apk
```

Log: `adb exec-out run-as com.haritpanchal.wakeguard cat files/wakeguard-poc.log`. Settings Force stop clears alarms. The closed-app test is swipe away, not Force stop.

## What the product does now

One daily time, stored in SharedPreferences `wakeguard-poc` (`enabled`, `hour`, `minute`). Default 07:00, off. The main screen steps hours by 1 and minutes by 5. Turn on schedules the next occurrence with `AlarmManager.setExactAndAllowWhileIdle`. `BootReceiver` reschedules on boot, `TIME_SET`, and timezone change.

`setAlarmClock` delivered once, then later alarms were accepted and expired without reaching the app. `setExactAndAllowWhileIdle` with a new request code each time is the path that then fired reliably. Do not switch back without a new failed delivery.

When an alarm fires, the app is not already running. The watch starts `AlarmSessionService` (`specialUse`, subtype `wakeguard-alarm`):

1. Vibrate for 45 seconds (`RING_MS`).
2. Keep a partial wake lock and read the LSM6DSV accelerometer. It is not a wake-up sensor.
3. After 5 minutes from the start, if the person still looks asleep, ring again about 1 second later.
4. Stop, or an awake decision, ends the chain and schedules the next day.
5. The chain also ends after 9 rings (the first plus 8 repeats) or 45 minutes.

Awake means Stop, one movement burst of at least 25 seconds, or 3 bursts over at least 40 seconds with at least 12 seconds of movement. One short rollover is not awake.

The launcher icon chosen by the user is option 5: warm hills (`#F0C9A0`) and a pale sun (`#F7EFE4`) on `#3A2A24`. That build is what is on the watch.

## What the watch proved on 2026-11-25

Times below are IST.

- 13:02: alarm with the app swiped away and the screen off. One motion sample with the screen off. Stop cleared it.
- 13:18 to 13:24: 45-second ring, quiet tracking while the watch lay still, second ring at about 5 minutes, Stop on the second ring. This is the behavior the user asked for.
- 15:15: daily alarm fired on time with the screen off. Vibration was scored as 40 seconds of movement (`SUSTAINED_BURST`) at 15:15:44, so the session ended as awake and there was no second ring. The alarm screen opened about 9 minutes late and still said “Monitoring movement.”
- 15:30, Bedtime mode on (`setting_bedtime_mode_running_state=1`), screen off: alarm and vibration started. The user stopped it at 15:30:21. Sleep mode did not silence the first ring. Stop rescheduled the next daily alarm (then 15:30 tomorrow). Confirm on the watch before assuming it is still armed.

Theater mode was not tested. A full overnight alarm was not tested. Reboot reschedule is implemented and not measured.

## Installed app versus this working tree

The watch **has** the latest source, installed at 15:33 IST on 2026-11-25. Changes over the previously-installed build:

- Samples taken while `ringing` are not passed to `WakefulnessDetector`. Scoring starts when vibration stops.
- `AlarmSettings.sessionActive` is true only during a live session.
- `AlarmActivity` says “Watching movement” with Stop only while that flag is set. If the screen opens late, it says “This ring already finished” and Close does not send Stop.

## Still-watch retest (2026-11-25, 15:45 IST)

Alarm set to 15:45, app swiped away, watch left still on a surface, screen off.

- 15:45:00 — alarm fired, vibration started.
- 15:45:45 — vibration stopped, scoring began. **0 bursts, 0 active ms** throughout.
- 15:50:00 — 5-minute window ended as "still asleep." Repeat scheduled.
- 15:50:05 — ring 2 started. User tapped Stop.

The motion-during-ring fix works: vibration no longer counts as awake.

## Do not assume

- Samsung Health runs on its own (`HealthSensorService` for days, plus an exercise service if a workout was left running). WakeGuard does not use it.
- There is no phone app, no cloud, and no Samsung Clock integration.
- Wear OS will not reliably open `AlarmActivity` from the background. Vibration and the notification Stop action are the reliable surface.
- The package stays `com.haritpanchal.wakeguard`. Do not rename it to chase Samsung’s old “alarm” package quirk unless a new delivery failure is measured first.

## Next

1. ~~Install the uncommitted motion-during-ring fix and retest.~~ Done — passed at 15:45 IST.
2. Snooze and a finer time picker are not built. Add them now.
3. Commit only if asked. Leave the repo private.
