# JustGBA

<img src="gba.svg" width="128" height="128" alt="JustGBA icon" align="left" style="margin-right: 16px;">

Standalone [gpSP](https://github.com/libretro/gpsp)-based GBA emulator for Android (arm64-v8a). Built to be efficient, fast, and minimal.

JustGBA leverages gpSP's JIT compiler to achieve exceptional execution speed and very low power consumption, even when fast-forwarding. This makes it well-suited for mobile scenarios where efficiency is paramount.

## Features

- Fast Forwarding (1.25x, 1.5x, 2x, up to 8x and Max)
- Optional Fast Forward toggle and press to Fast Forward buttons
- Portrait/Landscape mode
- Recently played library in main menu
- FPS Counter
- Saves

## TODO

- Export saves
- Save states
- Rebinding inputs
- Verify support for other controllers

## Build

```bash
./build.sh
```

Requires Android SDK, NDK 26.1.10909125. Output: `JustGBA.apk`.

## License

JustGBA is and will always be completely free and open-source software, under the GPL v2 license (inheriting from gpSP).
