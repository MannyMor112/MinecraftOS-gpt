# Brick v1

Brick is a live Minecraft Java 1.8.9 appliance for the HP Chromebook 11 G5 (SETZER). It boots from a raw-written USB on x86 UEFI or BIOS and uses the USB's writable partition for Wi-Fi, Prism's Microsoft session, downloaded game files, saves and reports. On first boot, use the setup screen to connect Wi-Fi and sign in. Prism shows a device code and QR link for a phone or another computer. Subsequent boots with a valid account take the selected boot mode straight into Minecraft.

A raw write erases the selected USB. Normal live modes use the boot drive only for writes. The internal eMMC installer is a separate GRUB entry and requires two confirmations.

- [Write, boot and recover](docs/USAGE.md)
- [Operating system and hardware comparison](docs/DESIGN.md)
- [Benchmark and Lubuntu baseline](docs/BENCHMARK.md)
- [Results log](docs/RESULTS.md)
- [Build and corresponding sources](docs/BUILD.md)

Game code, assets, the official Minecraft launcher and OptiFine are fetched when needed and are absent from the ISO. OptiFine HD U M5 and its bundled LaunchWrapper are fetched from optifine.net and checked against a fixed digest. Prism manages account eligibility and downloads the game and libraries.

Microsoft session tokens and saved Wi-Fi credentials live on the USB. Keep the stick with you and remove the saved account before sharing it.
