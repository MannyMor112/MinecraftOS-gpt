if [ "$(tty)" = /dev/tty1 ]; then
    exec startx /usr/local/bin/brick-session -- :0 vt1 -keeptty -nolisten tcp -noreset
fi
