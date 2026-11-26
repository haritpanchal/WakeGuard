# Contributing to WakeGuard

Thanks for your interest in contributing! WakeGuard is a small Wear OS project, so the process is lightweight.

## Getting Started

1. **Fork** the repo and clone your fork
2. **Pick an issue** — check the [Issues](https://github.com/haritpanchal/WakeGuard/issues) tab for open tasks. Issues labeled `good first issue` are great starting points.
3. **Comment on the issue** to let others know you're working on it. The maintainer will assign it to you.
4. **Create a branch** from `main` — name it something descriptive like `feature/multiple-alarms` or `fix/volume-ramp`

## Development Setup

- Android Studio (current stable)
- JDK 17+
- Android SDK with platforms 36 and 37
- A Wear OS device is strongly recommended — emulators don't reproduce Samsung background limits, sensor behavior, or alarm delivery quirks

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

## Making Changes

- Keep changes focused — one feature or fix per PR
- Follow existing code style (Kotlin, no wildcard imports, 4-space indent)
- Add unit tests where possible (see `WakefulnessDetectorTest.kt` for examples)
- Test on a real watch if your change involves alarms, sensors, notifications, or background execution
- Update the README if your change adds user-facing features or new permissions

## Submitting a Pull Request

1. **Push** your branch to your fork
2. **Open a PR** against `main` with a clear title and description
3. **Describe what you changed and why** — link the issue it closes (e.g., "Closes #4")
4. **Include test results** — what did you test? On what device? Screenshots are welcome for UI changes.
5. **Keep it small** — smaller PRs get reviewed faster

## PR Review Process

- The maintainer ([@haritpanchal](https://github.com/haritpanchal)) reviews all PRs
- Expect feedback within a few days
- PRs are squash-merged into `main` to keep history clean
- After merge, the maintainer may batch changes into a versioned release

## What Makes a Good PR

- ✅ Solves one issue
- ✅ Has a clear description
- ✅ Passes `./gradlew :app:testDebugUnitTest`
- ✅ Tested on a real Wear OS device (for runtime changes)
- ✅ No unrelated formatting or refactoring mixed in

## Code of Conduct

Be respectful and constructive. This is a hobby project — everyone here is volunteering their time.

## Questions?

Open a [Discussion](https://github.com/haritpanchal/WakeGuard/issues) or comment on the relevant issue. Don't hesitate to ask!
