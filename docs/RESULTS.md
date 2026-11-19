# Results log

| Build | Settings or change | Runs | Average FPS | 1% low | Spikes >50 ms |
|---|---|---:|---:|---:|---:|
| Lubuntu 20.04 baseline | Minecraft 1.8.9 + OptiFine M5, Fast, six chunks, 1 GiB heap and zram (reported working) | 0 recorded | — | — | — |
| Brick v1 | Debian Linux, i915/Mesa Iris, non-composited X11, Temurin 8, zram LZ4, same scene and gameplay settings | 0 on target | — | — | — |

There are no measured performance deltas, failed tuning experiments, or defensible FPS estimates yet. The first useful comparison is the benchmark above on the current Lubuntu installation and the same Chromebook booted into Brick. Record raw reports before changing the configuration.

## v1 verification

- The raw ISO booted as a virtual USB drive with SeaBIOS in play mode and OVMF UEFI in benchmark mode. Both reached the setup screen and mounted the writable partition.
- The setup rejected QEMU's llvmpipe renderer. Prism opened its Microsoft account setup screen.
- A full synthetic benchmark using Java 8 and the actual OptiFine LaunchWrapper classloader exercised the benchmark hooks, fixed-camera check, timed report and normal shutdown. Its timings are excluded from the performance table.
- The first real run still needs to verify Microsoft authentication, Minecraft launch, Intel hardware rendering, Wi-Fi, speakers, touchpad, keyboard and brightness on the Chromebook. Internal eMMC installation remains untested on the target.
