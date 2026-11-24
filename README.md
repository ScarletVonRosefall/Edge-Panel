# Edge Panel

A lightweight edge panel for **Android 9 / ColorOS 6**. Tap a small handle on the side of the screen to get volume sliders, screenshot, lock screen, and the system power menu (restart / power off).

I built it because my phone's physical volume and power buttons broke and I couldn't afford a repair. It's made for entry-level devices (tested on a Helio P35 / 4 GB RAM phone), so it's designed to stay out of the way: no dependencies, no foreground service, and the panel only exists in memory while it is open.

<!-- Add a screenshot or short screen recording here: ![Edge Panel](docs/screenshot.png) -->

## Features

- **Volume sliders** for Media, Ring and Alarm, styled after ColorOS.
- **Screenshot** without the power + volume combo.
- **Lock screen** button.
- **Power menu** button that opens the system menu with Restart and Power off.
- **Movable handle**: long-press the handle and drag it up or down. The position is saved.

## How it works

Everything runs inside a single `AccessibilityService`:

- The handle and the panel are overlay windows (`TYPE_ACCESSIBILITY_OVERLAY`), so no "draw over other apps" permission is needed.
- Screenshot, lock screen and power menu use the accessibility global actions (`GLOBAL_ACTION_TAKE_SCREENSHOT`, `GLOBAL_ACTION_LOCK_SCREEN`, `GLOBAL_ACTION_POWER_DIALOG`).
- Volume uses `AudioManager`. After each change the app reads back the value the system applied, and falls back to key-style stepping if the first method didn't take.
- The service registers for no accessibility events and has no window-content access, so it uses almost no CPU while idle.
- Plain `android.view` views and a custom pill slider: no AndroidX, no Compose.

## Install

1. Download the APK from the [Releases](../../releases) page.
2. Open it on your phone and allow installs from that source.
3. Open **Edge Panel** and tap **Open Accessibility settings**, then turn the service on.
4. Optional: tap **Grant Do Not Disturb access** so the Ring slider can reach silent.
5. Tap the handle on the right edge of the screen.

> Updating the app can switch the accessibility service off. If the handle disappears, re-enable it in Settings > Accessibility.

## ColorOS tips

- Allow auto-start for the app, exclude it from battery optimization, and lock it in the recents screen so ColorOS doesn't kill it.
- If your power button is broken, make sure **double-tap to wake** is on before using Lock screen.

## Build from source

Requires JDK 17, the Android SDK (platform 34), and Gradle (the wrapper is included).

```
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Configuration

Constants at the top of `PanelService.kt`:

| Setting | Purpose |
|---|---|
| `onRight` | `false` puts the handle on the left edge |
| `edgeInsetDp` | Gap between the screen edge and the handle (0 = flush) |
| `handleWidthDp` / `handleHeightDp` | Size of the touch area |
| `openOnSwipe` | Also open on an inward swipe (may trigger back) |
| `panelEdgePadDp` | Edge-side padding that keeps sliders clear of the back-gesture zone |
| `color*` | Panel, slider, button and handle colors |

## Known limitations

- **Back gesture.** Android 9 has no API for excluding screen regions from the system back gesture (that arrived in Android 10). Because the handle sits on the very edge, it opens on a tap rather than a swipe.
- **Power menu.** It shows whatever the manufacturer's long-press menu offers, so the contents depend on the ROM.
- Tested on ColorOS 6.1.2 (Android 9) only.

## Contributing

If you hit a problem on your device, please open an issue with your phone model and ROM version.

## License

MIT, see [LICENSE](LICENSE).
