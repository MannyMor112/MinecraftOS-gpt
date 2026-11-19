# Live USB, backup and recovery

Use a separate USB for the game image and one for backups, or store the backup in a location independent of both the Chromebook's internal drive and the game USB. Keep the power adapter connected for the benchmark.

## Back up from Lubuntu

Stop Minecraft. In the file manager, open your home directory, press Ctrl+H to show hidden files, open `.minecraft`, and copy `saves` to a separate backup drive. If your launcher uses per-instance directories, open the instance's game directory and copy its `saves` there too. Verify the backup contains world folders with `level.dat` files. Use the file manager's eject action before removing the backup drive.

## Write and boot a live USB

1. Download `brick-v1.iso` and `brick-v1.iso.sha256` from the same release. From that download directory, run `sha256sum -c brick-v1.iso.sha256` and confirm `brick-v1.iso: OK`.
2. Before inserting the game USB, run `lsblk -dpno NAME,SIZE,MODEL,TRAN`. Insert the USB and run the same command again. Identify the newly appeared whole USB disk, such as `/dev/sdb`, by size and `usb` transport. Never select the existing internal eMMC. If uncertain, stop and send the two listings.
3. Unmount any mounted partitions on that same USB using the file manager or `udisksctl unmount -b /dev/sdX1` (repeat for each mounted partition, using its actual path). Keep the USB connected.
4. **The following overwrites the entire selected USB.** Replace `/dev/sdX` with the confirmed whole USB disk. Run from the download directory.

   ```sh
   sudo dd if=brick-v1.iso of=/dev/sdX bs=4M status=progress conv=fsync
   sync
   ```

5. Shut down with the written USB plugged in. Power on and tap Esc as soon as the MrChromebox splash or firmware screen appears. Select **Boot Menu**, then the entry for the USB stick, typically labeled **UEFI** or by its manufacturer. If your firmware instead shows a ChromeOS developer screen with an enabled legacy payload, use its alternate-firmware shortcut (commonly Ctrl+L), then the SeaBIOS boot menu (Esc) and select the USB. Firmware menus vary with the installed payload. Leave firmware settings as they are for the first test. [MrChromebox OS boot guide](https://docs.mrchromebox.tech/docs/firmware/booting.html)
6. Choose **Minecraft 1.8.9 (live)**, or select **Minecraft 1.8.9 benchmark (live)** for a benchmark. In the setup menu use option 1 to pick Wi-Fi and enter the password, then return to the menu. Use option 2 to add a Microsoft account in Prism and use its device code on a phone. Close Prism after signing in, then use option 3. Leave the benchmark alone until Minecraft closes.
7. Power off through the setup menu. Return to Lubuntu without booting the game USB, then plug it in. Open the `BRICKDATA` volume in the file manager and copy the newest `minecraft-benchmark-*.txt` from `results`. The account and Wi-Fi files are separate from reports.

## Returning to Lubuntu

After a live test, shut down, remove the USB and power on. Use Esc and select the internal Lubuntu/eMMC entry if the firmware shows a boot menu. If a hard freeze prevents shutdown, hold the power button until the Chromebook turns off, remove the USB, and restart. If the USB fails to boot, return to Lubuntu and re-check the ISO hash and disk selection before writing again.

An internal install is optional. The separate GRUB installer requires a USB boot, one internal non-removable eMMC target, a backup confirmation, and typing its full erase confirmation. It writes the same appliance layout and creates fresh account and game storage. Installation destroys the former internal partition layout and data. If you later want normal Linux again, write a supported 64-bit Lubuntu or Debian installer to a different USB, boot it from the same firmware menu, and reinstall to the eMMC. Restore your saved worlds into the correct launcher instance after Minecraft is installed. Leave the currently working firmware in place. [Lubuntu downloads](https://lubuntu.me/downloads/) · [Debian installer](https://www.debian.org/distrib/)
