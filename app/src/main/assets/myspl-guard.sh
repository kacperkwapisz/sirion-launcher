#!/system/bin/sh
# Blocks the vendor's myspl tool, which remounts /system and copies files
# into it (the likely cause of the 2026-10-22 factory reset), while keeping
# the one job it does all the time: CPU switching on sleep and wake (--acf).
#
#   myspl-guard.sh apply | remove | status
#
# "apply" bind-mounts this script over /system/bin/myspl. Nothing is written
# to the system partition, and a reboot undoes it; the launcher's root
# helper applies it again at start while /data/hu/block-myspl exists.
# When init runs /system/bin/myspl, this script runs instead (the stand-in).

TARGET=${MYSPL_TARGET:-/system/bin/myspl}
DIR=${MYSPL_DIR:-/data/hu}
REAL=$DIR/myspl-real
SELF=$DIR/myspl-guard.sh
LOG=$DIR/myspl.log
LABEL=u:object_r:myspl_exec:s0

is_mounted() { grep -q " $TARGET " /proc/mounts; }

# Called as the stand-in.
if [ "$0" = "$TARGET" ]; then
    case "$1" in
        --acf=0|--acf=1) exec "$REAL" "$@" ;;
    esac
    echo "$(date '+%Y-%m-%d %H:%M:%S') blocked: $*" >> "$LOG"
    exit 0
fi

case "$1" in
    apply)
        is_mounted && { echo "on"; exit 0; }
        mkdir -p "$DIR"
        # Keep the real tool for --acf. Only copy it while the original is in place.
        [ -f "$REAL" ] || cp "$TARGET" "$REAL" || exit 1
        chmod 755 "$SELF" "$REAL"
        chcon "$LABEL" "$SELF" "$REAL" 2>/dev/null
        # Read-only, so the mount table never shows a writable mount under
        # /system (toybox can't do that; hu-bindro ships with the launcher).
        "$DIR/hu-bindro" "$SELF" "$TARGET" || exit 1
        echo "on"
        ;;
    remove)
        is_mounted && umount "$TARGET"
        echo "off"
        ;;
    status)
        if is_mounted; then echo "on"; else echo "off"; fi
        [ -f "$LOG" ] && tail -n 5 "$LOG"
        ;;
    *)
        echo "usage: $0 apply|remove|status" >&2
        exit 2
        ;;
esac
