#!/bin/bash
set -euo pipefail
export SOURCE_DATE_EPOCH=1794873600
export LC_ALL=C.UTF-8 TZ=UTC
umask 022
mkdir -p /image /output
root=/image
iso=/output/iso
mkdir -p "$iso/live" "$iso/boot/grub/i386-pc" "$iso/boot/grub/x86_64-efi" "$iso/boot/grub/i386-efi"
tar -xf - -C "$root" --exclude='./.dockerenv' --exclude='.dockerenv' --exclude='dev/*' --exclude='proc/*' --exclude='sys/*'
while IFS= read -r -d '' source; do
    relative=${source#/src/rootfs/}
    if [[ -d "$source" ]]; then
        mkdir -p -m 0755 "$root/$relative"
    elif [[ -f "$source" ]]; then
        mode=0644
        [[ ! -x "$source" ]] || mode=0755
        install -D -m "$mode" "$source" "$root/$relative"
    fi
done < <(find /src/rootfs -mindepth 1 -print0)
chmod 0755 "$root"
cp /src/boot.cfg "$iso/boot/grub/grub.cfg"
touch "$iso/BRICKLIVE"
printf 'Brick v1\n' > "$root/etc/brick-release"
# NetworkManager writes resolv.conf when the live system connects.
truncate -s 0 "$root/etc/resolv.conf"
truncate -s 0 "$root/etc/machine-id"
if [[ -e "$root/var/lib/dbus/machine-id" && ! -L "$root/var/lib/dbus/machine-id" ]]; then
    unlink "$root/var/lib/dbus/machine-id"
    ln -s /etc/machine-id "$root/var/lib/dbus/machine-id"
fi
chroot "$root" useradd -M -u 1000 -s /bin/bash -G audio,video,render,input player
chroot "$root" passwd -d player >/dev/null
chroot "$root" chown -R player:player /home/player
mkdir -p "$root/etc/systemd/system/multi-user.target.wants"
ln -s ../brick-prepare.service "$root/etc/systemd/system/multi-user.target.wants/brick-prepare.service"
# User space only requires the confirmed 7265 firmware. Keep both hardware revisions.
find "$root/usr/lib/firmware" -maxdepth 1 -type f -name 'iwlwifi-*.ucode' \
    ! -name 'iwlwifi-7265-17.ucode' ! -name 'iwlwifi-7265D-29.ucode' -delete
for file in "$root"/var/log/*.log "$root"/var/log/apt/*; do
    [[ ! -f "$file" ]] || truncate -s 0 "$file"
done
mkdir -p "$root/opt/prism" "$root/opt/java"
tar -xzf /work/prism.tar.gz -C "$root/opt/prism"
tar -xzf /work/java.tar.gz -C "$root/opt/java" --strip-components=1
cd /output
mkdir classes
(cd classes; jar xf /usr/share/java/asm.jar; find META-INF -maxdepth 1 -name MANIFEST.MF -delete; find . -name module-info.class -delete)
javac --release 8 -cp /usr/share/java/asm.jar -d classes /src/bench/Benchmark.java /src/bench/Telemetry.java
# Use the runtime Python to produce the world with a pinned implementation.
cp /src/bench/make-world.py "$root/tmp/brick-world.py"
chroot "$root" python3 /tmp/brick-world.py /tmp/brick-level.dat
mv "$root/tmp/brick-level.dat" classes/level.dat
unlink "$root/tmp/brick-world.py"
printf 'Premain-Class: brick.Benchmark\n' > Manifest
jar --create --date=2026-11-17T00:00:00Z --file /output/brick-bench.jar --manifest Manifest -C classes .
cp /output/brick-bench.jar "$root/usr/share/brick/"
cp /output/brick-bench.jar /work/brick-bench.jar
kernel=$(basename "$(find "$root/boot" -maxdepth 1 -name 'vmlinuz-*' -print -quit)" | cut -c9-)
cp "$root/boot/vmlinuz-$kernel" "$iso/live/vmlinuz"
cp "$root/boot/initrd.img-$kernel" "$iso/live/initrd.img"
for path in var/cache/ldconfig/aux-cache var/log/lastlog var/log/btmp var/log/wtmp; do
    [[ ! -f "$root/$path" ]] || truncate -s 0 "$root/$path"
done
find "$root/boot" -mindepth 1 -maxdepth 1 -type f -delete
find "$root/var/lib/systemd" -type f -name random-seed -delete
env -u SOURCE_DATE_EPOCH mksquashfs "$root" "$iso/live/filesystem.squashfs" -noappend -comp zstd -Xcompression-level 12 -processors 4 -no-progress -all-time "$SOURCE_DATE_EPOCH" -mkfs-time "$SOURCE_DATE_EPOCH"
modules=(part_msdos part_gpt iso9660 fat search search_fs_file configfile normal linux echo test all_video)
grub-mkimage -O i386-pc-eltorito -p /boot/grub -c /src/early.cfg -o "$iso/boot/grub/i386-pc/eltorito.img" biosdisk "${modules[@]}"
grub-mkimage -O x86_64-efi -p /boot/grub -c /src/early.cfg -o /output/BOOTX64.EFI "${modules[@]}"
grub-mkimage -O i386-efi -p /boot/grub -c /src/early.cfg -o /output/BOOTIA32.EFI "${modules[@]}"
for platform in i386-pc x86_64-efi i386-efi; do
    cp /usr/lib/grub/"$platform"/*.lst "$iso/boot/grub/$platform/"
done
truncate -s 12M /output/efi.img
mkfs.fat --invariant -n BRICKEFI /output/efi.img
mmd -i /output/efi.img ::EFI ::EFI/BOOT
touch -d "@$SOURCE_DATE_EPOCH" /output/BOOTX64.EFI /output/BOOTIA32.EFI
mcopy -m -i /output/efi.img /output/BOOTX64.EFI /output/BOOTIA32.EFI ::EFI/BOOT/
mkdir -p "$iso/EFI/BOOT"
cp /output/BOOTX64.EFI /output/BOOTIA32.EFI "$iso/EFI/BOOT/"
truncate -s 640M /output/data.img
E2FSPROGS_FAKE_TIME="$SOURCE_DATE_EPOCH" mkfs.ext4 -q -F -m 0 -L BRICKDATA \
    -U b8cc1890-b48d-45dc-aab0-71eaf9f080f1 \
    -E hash_seed=143108ad-a5a0-4f95-82bd-dbd6270ad7e7,lazy_itable_init=0,lazy_journal_init=0 /output/data.img
find "$iso" -exec touch -h -d "@$SOURCE_DATE_EPOCH" {} +
xorriso -as mkisofs -R -J -V BRICKLIVE --modification-date=2026111700000000 \
    --grub2-mbr /usr/lib/grub/i386-pc/boot_hybrid.img --grub2-boot-info \
    -partition_offset 16 -iso_mbr_part_type 0x17 \
    -append_partition 2 0xef /output/efi.img -append_partition 3 0x83 /output/data.img \
    -c boot/grub/boot.cat -b boot/grub/i386-pc/eltorito.img -no-emul-boot -boot-load-size 4 -boot-info-table \
    -eltorito-alt-boot -e '--interval:appended_partition_2:all::' -no-emul-boot \
    -o /work/brick-v1.iso.partial "$iso"
mv /work/brick-v1.iso.partial /work/brick-v1.iso
cp "$root/usr/share/brick/packages.tsv" /work/packages.tsv
(cd /work; sha256sum brick-v1.iso > brick-v1.iso.sha256)
