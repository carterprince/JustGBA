| Emulator | Engine | Time (1M Loops) | it/s | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **gpSP** *(RetroArch)* | JIT | 19.4s | ~51,546 | |
| **JustGBA** | JIT | 19.4s | ~51,546 | |
| **ClassicBoy** | Interpreter | 1m 54.6s | ~8,726 | |
| **NooDS** | Interpreter | 2m 12.8s | ~7,530 | |
| **GBA.emu** | Interpreter | 2m 38.0s | ~6,329 | Speed hard-capped at 20x |
| **mGBA** *(RetroArch)* | Interpreter | 3m 31.6s | ~4,725 | |
| **SkyEmu** | Interpreter | 8m 22.6s | ~1,989 | |
| **mGBA** *(Lemuroid)* | Interpreter | *N/A* | *N/A* | Speed cannot be uncapped |
| **Pizza Boy** | Interpreter | *N/A* | *N/A* | Speed cannot be uncapped |
| **My Boy!** | JIT | *N/A* | *N/A* | Failed to count past 9 |

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
