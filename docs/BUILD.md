# Build and corresponding sources

On an x86-64 Linux build machine with Docker, Bash, curl and roughly 10 GiB free, run:

```sh
./build.sh
```

The build writes the ISO, SHA-256 file, standalone benchmark agent, and Debian package manifest under `build/`. Pass an absolute directory as the first argument to put outputs elsewhere. The build uses a pinned Debian container digest, checks the exact signed Debian repository index, and checks the SHA-256 digests of Prism and Temurin before extracting them. It normalizes distribution timestamps where supported. Byte-for-byte reproducibility has not been established across independent Docker builds.

The release's `packages.tsv` lists every Debian package with binary version, source package and source version. Corresponding Debian source packages are available in the same frozen repository as the binaries through the `deb-src` index and apt. Use this entry in a clean Debian environment and run `apt-get update` before downloading named source versions with `apt-get source --download-only SOURCE=VERSION`.

```
deb-src [check-valid-until=no] https://snapshot.debian.org/archive/debian/20261118T000000Z/ trixie main contrib non-free-firmware
```

The signed Linux image source version includes the corresponding Linux kernel source reference in its source package. The GRUB boot binaries used by the build are from the signed Debian archive above. Debian copyright files remain in `/usr/share/doc` on the ISO. [Debian archive](https://snapshot.debian.org/archive/debian/20261118T000000Z/) · [Debian GRUB source](https://sources.debian.org/src/grub2/)

- Prism Launcher 11.1.1 is distributed as the upstream Linux portable build, including its upstream packaging recipe and dependency references in the tagged source. [Prism source release](https://github.com/PrismLauncher/PrismLauncher/releases/tag/11.1.1) · [Prism Linux build dependencies](https://github.com/PrismLauncher/PrismLauncher/tree/11.1.1/.github/actions/setup-dependencies)
- The Prism portable build uses Qt 6.10.2. [Qt source archives](https://download.qt.io/archive/qt/6.10/6.10.2/submodules/) · [Sharun source](https://github.com/pkgforge-dev/sharun)
- The Temurin 8u512-b09 JRE preserves its license files. [Temurin source archive](https://github.com/adoptium/temurin8-binaries/releases/tag/jdk8u512-b09) · [OpenJDK source tree](https://github.com/adoptium/jdk8u)
- The benchmark embeds Debian's ASM library. Its source is available in the same Debian snapshot. [Debian ASM sources](https://sources.debian.org/src/asm/)
- Intel firmware files and microcode are distributed under the redistribution terms in the respective Debian packages. No Microsoft binaries, game code, game assets, or OptiFine are packaged in the image.

The ISO is a hybrid layout with a read-only ISO9660 filesystem, a FAT EFI System Partition, and a 640 MiB ext4 data partition. The ISO and EFI boot paths share the same GRUB menu. Legacy BIOS boots from the GRUB hybrid MBR or the El Torito BIOS entry. The game session uses Xorg modesetting and Mesa's stock driver selection.

The upstream [Prism license](../third-party/prism/LICENSE) and [notices](../third-party/prism/COPYING.md) accompany the ISO.
