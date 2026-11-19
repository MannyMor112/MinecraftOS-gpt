#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
source_dir=$PWD
work_dir=$(realpath -m "${1:-$PWD/build}")
mkdir -p "$work_dir"
declare -A upstream=(
    [prism.tar.gz]=https://github.com/PrismLauncher/PrismLauncher/releases/download/11.1.1/PrismLauncher-Linux-Qt6-Portable-11.1.1.tar.gz
    [java.tar.gz]=https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u512-b09/OpenJDK8U-jre_x64_linux_hotspot_8u512b09.tar.gz
)
for archive in "${!upstream[@]}"; do
    if [[ ! -f "$work_dir/$archive" ]]; then
        curl -fL --retry 3 --output "$work_dir/$archive.partial" "${upstream[$archive]}"
        mv "$work_dir/$archive.partial" "$work_dir/$archive"
    fi
done
(cd "$work_dir"; sha256sum -c "$source_dir/downloads.sha256")
# Both URLs must serve the same signed, frozen package index.
expected=0584fba32e13e0ab8285fb16c27adea1ec03a73669c18702821094fd6ca86675
mirror=http://deb.debian.org/debian
snapshot=http://snapshot.debian.org/archive/debian/20261118T000000Z
actual=$(curl -fLsS --max-time 30 "https://${mirror#http://}/dists/trixie/InRelease" | sha256sum | cut -d' ' -f1) || actual=
if [[ "$actual" != "$expected" ]]; then
    mirror=$snapshot
    actual=$(curl -fLsS --retry 2 --max-time 30 "https://${mirror#http://}/dists/trixie/InRelease" | sha256sum | cut -d' ' -f1)
    [[ "$actual" == "$expected" ]] || { echo "Debian snapshot index digest does not match." >&2; exit 1; }
fi
docker build --build-arg DEBIAN_ARCHIVE="$mirror" --target runtime -t brick-runtime "$source_dir"
docker build --build-arg DEBIAN_ARCHIVE="$mirror" --target tools -t brick-tools "$source_dir"
archive=$(mktemp "$work_dir/.rootfs.XXXXXXXX")
trap 'unlink "$archive"' EXIT
runtime=$(docker create brick-runtime)
export_runtime() (
    trap 'docker rm "$runtime" >/dev/null' EXIT
    docker export "$runtime" | gzip -1 > "$archive"
)
export_runtime
gzip -dc "$archive" | docker run --rm -i -v "$source_dir:/src:ro" -v "$work_dir:/work" brick-tools bash /src/make-image.sh
