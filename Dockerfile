FROM debian:trixie-slim@sha256:a72b4433de1cf7d6bae0c4cee74d0547f58601a643b3375f070c51600d63b6b2 AS base
ARG DEBIAN_ARCHIVE=http://deb.debian.org/debian
ARG SNAPSHOT=20261118T000000Z
ENV LC_ALL=C.UTF-8 DEBIAN_FRONTEND=noninteractive
RUN rm -f /etc/apt/sources.list.d/* && \
    printf 'deb [check-valid-until=no] %s trixie main contrib non-free-firmware\n' "$DEBIAN_ARCHIVE" >/etc/apt/sources.list && \
    printf 'APT::Install-Recommends "false";\nAcquire::Retries "3";\n' > /etc/apt/apt.conf.d/99brick
FROM base AS runtime
COPY packages.txt /tmp/packages.txt
RUN apt-get -o APT::Update::Error-Mode=any update && xargs -r apt-get install -y </tmp/packages.txt && \
    mkdir -p /usr/share/brick && dpkg-query -W -f='${Package}\t${Version}\t${source:Package}\t${source:Version}\n' > /usr/share/brick/packages.tsv && \
    apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/packages.txt
FROM base AS tools
RUN apt-get -o APT::Update::Error-Mode=any update && apt-get install -y xorriso grub-pc-bin grub-efi-amd64-bin grub-efi-ia32-bin \
    mtools dosfstools squashfs-tools e2fsprogs openjdk-21-jdk-headless libasm-java unzip && \
    apt-get clean && rm -rf /var/lib/apt/lists/*
FROM base AS test
RUN apt-get -o APT::Update::Error-Mode=any update && apt-get install -y qemu-system-x86 seabios ovmf && apt-get clean && rm -rf /var/lib/apt/lists/*
