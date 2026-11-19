if [ "$(tty)" = /dev/tty1 ]; then
    if grep -qw brick.mode=install /proc/cmdline; then
        printf '%s\n' 'Installer is running on tty2. Press Alt+F2 to view it.'
        exec sleep infinity
    fi
    exec startx /usr/local/bin/brick-session -- :0 vt1 -keeptty -nolisten tcp -noreset
fi
