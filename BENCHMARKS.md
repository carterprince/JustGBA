# Contents

1. [Power Draw Comparison](#power-draw-comparison)
2. [Performance Comparison](#performance-comparison)

## Power Draw Comparison

| Metric | gpSP (RetroArch) | gpSP (JustGBA) | Linkboy | mGBA (RetroArch) | Pizza Boy | SkyEmu | ClassicBoy |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| Emulator App CPU | 2.40 mAh | 2.19 mAh | 2.83 mAh | 4.71 mAh | 5.48 mAh | 18.20 mAh | 148.00 mAh |
| Emulator App GPU | 0.57 mAh | 0.00 mAh | 0.41 mAh | 0.54 mAh | 0.47 mAh | 1.53 mAh | 1.30 mAh |
| **Total Emulator Power Draw** | **2.97 mAh** | **2.19 mAh** | **3.24 mAh** | **5.25 mAh** | **5.95 mAh** | **19.73 mAh** | **149.30 mAh** |
| Screen (Global) | 19.70 mAh | 20.10 mAh | 21.10 mAh | 19.90 mAh | 23.60 mAh | 17.70 mAh | 19.90 mAh |
| Audio (Global) | 12.50 mAh | 12.50 mAh | 12.50 mAh | 12.50 mAh | 12.50 mAh | 12.50 mAh | 12.50 mAh |
| Other Device Draw (OS, Radio, etc.)| 7.24 mAh | 5.66 mAh | 6.18 mAh | 7.42 mAh | 6.39 mAh | 20.05 mAh | 13.84 mAh |
| **Device Total Power Draw** | **42.41 mAh** | **40.45 mAh** | **43.02 mAh** | **45.07 mAh** | **48.44 mAh** | **69.98 mAh** | **195.54 mAh** |
| **Implied Battery Life (5000mAh)** | **19h 39m** | **20h 36m** | **19h 22m** | **18h 29m** | **17h 12m** | **11h 54m** | **4h 16m** |

### Methodology

Power draw was measured by running the *Sonic Advance* title screen demo loop at a forced 2x fast-forward speed for exactly 10 minutes on a Pixel 9 Pro. Android's built-in battery tracking was wiped immediately before each test and automatically dumped after 600 seconds using the following ADB command:

```bash
adb shell dumpsys batterystats --reset && sleep 600 && adb shell dumpsys batterystats > stats.txt
```

This isolated the exact milliamp-hour (mAh) consumption of the active test window, allowing for an accurate breakdown of power usage across the app's CPU/GPU threads, the physical screen, and background OS processes.

## Performance Comparison

| Emulator | Engine Type | Time (1M Loops) | it/s | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **[gpSP](https://github.com/libretro/gpsp)** *(via [RetroArch](https://www.retroarch.com/))* | JIT | 19.4s | ~51,546 | |
| **[gpSP](https://github.com/libretro/gpsp)** *(via [JustGBA](https://github.com/carterprince/JustGBA))* | JIT | 19.4s | ~51,546 | |
|[**Linkboy**](https://play.google.com/store/apps/details?id=com.pixelrespawn.linkboy&hl=en_US)|JIT|20.4s| ~49,020||
| **[ClassicBoy](https://play.google.com/store/apps/details?id=com.portableandroid.classicboyLite)** | Interpreter | 1m 54.6s | ~8,726 | |
| **[NooDS](https://github.com/Hydr8gon/NooDS)** | Interpreter | 2m 12.8s | ~7,530 | |
| **[GBA.emu](https://www.explusalpha.com/contents/gba-emu)** | Interpreter | 2m 38.0s | ~6,329 | Speed hard-capped at 20x |
| **[mGBA](https://mgba.io/)** *(via [RetroArch](https://www.retroarch.com/))* | Interpreter | 3m 31.6s | ~4,725 | |
| **[SkyEmu](https://skyemu.app/)** | Interpreter | 8m 22.6s | ~1,989 | |
| **[mGBA](https://mgba.io/)** *(via [Lemuroid](https://github.com/Swordfish90/Lemuroid))* | Interpreter | *N/A* | *N/A* | Speed cannot be uncapped |
| **[Pizza Boy](https://pizzaemulators.com/)** | Interpreter | *N/A* | *N/A* | Speed cannot be uncapped |
| **[My Boy!](https://play.google.com/store/apps/details?id=com.fastemulator.gba)** | JIT | *N/A* | *N/A* | Failed to count past 9 |

### Methodology

Because internal GBA timers scale with emulation speed, real-world performance was measured using a custom GBA ROM. The ROM waits for the user to press 'A', executes a 1,000,000-iteration loop, and prints "DONE!". During each iteration, it updates a counter on the screen; writing to VRAM ensures that JIT compilers must process the instructions rather than optimizing away an empty loop.

Testing was conducted on a Google Pixel 9 Pro. Each emulator was configured to run at its maximum uncapped fast-forward speed. Wall-clock time was measured with a stopwatch on a separate device from the initial button press until the completion screen appeared.

### Benchmark ROM Source Code

```c
#include <gba_console.h>
#include <gba_video.h>
#include <gba_interrupt.h>
#include <gba_systemcalls.h>
#include <gba_input.h>
#include <stdio.h>

#define TARGET_LOOPS 1000000 

int main(void) {
    irqInit();
    irqEnable(IRQ_VBLANK);
    consoleDemoInit();

    while(1) {
        iprintf("\x1b[2J"); // Clear screen
        iprintf("  GBA Benchmark\n\n");
        iprintf("  Press A to start\n");
        iprintf("  %d loops\n", TARGET_LOOPS);

        // Wait for A button to be pressed
        while(1) {
            scanKeys();
            u16 keys = keysDown();
            if(keys & KEY_A) break;
            VBlankIntrWait();
        }

        iprintf("\x1b[2J");
        iprintf("\x1b[H  RUNNING...\n\n  Start your stopwatch!");
        
        // Force the text to render to the screen BEFORE the loop starts
        VBlankIntrWait(); 

        unsigned int counter = 0;
        
        // Benchmark loop
        while(counter < TARGET_LOOPS) {
            iprintf("\x1b[H\n  RUNNING...\n\n  Counter: %u", counter);
            counter++;
        }

        // Benchmark finished
        iprintf("\x1b[2J");
        iprintf("\x1b[H  DONE!\n\n  Stop your stopwatch.\n\n  Press B to reset.");

        // Wait for B button to reset the test
        while(1) {
            scanKeys();
            u16 keys = keysDown();
            if(keys & KEY_B) break;
            VBlankIntrWait();
        }
    }
}
