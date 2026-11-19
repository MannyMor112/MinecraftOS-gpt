# Benchmark

Choose the benchmark entry in GRUB. First boot may still ask for Wi-Fi, Microsoft sign-in, and a game download. Start Minecraft and leave the foreground game untouched. The agent opens a fresh peaceful Overworld with seed 8675309, position 8.5 / 100 / 8.5, yaw 0°, pitch 24°, noon and no natural mob spawning or random block ticks. It warms up for 30 seconds and measures for 120 seconds. It closes the game and writes a text report to `BRICKDATA/results/`. Run on AC power with identical native display resolution, ambient conditions, and brightness across trials.

The report measures completed LWJGL buffer swaps and includes average FPS, 1% low FPS (reciprocal of the mean of the slowest 1% of frame intervals), P99 and maximum frame time, counts over 33.3 ms, 50 ms and 100 ms, and chronological intervals. Telemetry includes Linux-exported per-core frequency, i915 actual GPU frequency, thermal zones, exported CPU/GPU throttling counters, RAM/swap totals and availability, compressed swap statistics and Java heap use. Counters are cumulative. A missing sensor is unavailable, and falling clocks alone cannot distinguish thermal throttling from workload and power policy. Treat a failed fixed camera check as an invalid run.

The static scene measures steady-state single-player performance. Chunk streaming, multiplayer, busy bases, shaders and menus can differ substantially. A practical FPS estimate needs a first test on this device.

## Lubuntu baseline

Use a separate launcher instance or a copy of the existing 1.8.9 + OptiFine M5 game directory so the test settings leave normal play and saves intact. Put the release's `options.txt` and `optionsof.txt` in that copy's game directory, and keep your usual Java 8 runtime, 1 GiB maximum heap, and zram configuration. Add this JVM argument to that copy's launch profile, replacing both paths with your actual absolute paths. Use paths without spaces.

```
-javaagent:/home/USER/Downloads/brick-bench.jar=/home/USER/brick-baseline-results
```

Launch the copied instance with your existing legitimate account. The agent creates a fresh benchmark save, runs the same scene and writes the same report schema. Recopy the options files before each run for a like-for-like comparison. Keep the two benchmark runs at the same framebuffer dimensions. For the baseline, leave the normal Lubuntu compositor/desktop configuration as you actually play.

Run three repetitions per configuration. Use medians and record the range of measurements. If the observed difference is within repeat-to-repeat variation, retain the simpler or safer setting and stop adjusting that factor. After the initial OS comparison, change one factor per iteration. Candidate factors are governor, Mesa/driver version, JVM GC, and zram. Tune OptiFine and other mods separately, with exact versions and instructions listed in that build's notes.
