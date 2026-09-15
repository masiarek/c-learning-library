#!/usr/bin/env bash
# The mebibyte of zeros costs the running program memory and costs the file
# nothing. Build layout.c as it is, then again with one byte of scratch set, and
# ask each executable whether it is bigger than the array it declares.
#
# Runs in a scratch copy of ../demo. Prints only what GCC on Linux and Apple
# clang on macOS agree on: bigger or smaller, never a byte count -- the two
# linkers pad a file to different page sizes.
set -u
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cp ../demo/layout.c "$work"/
cd "$work" || exit 1

say() { printf '$ %s\n' "$*"; eval "$*" 2>&1; }

# Is the file bigger than scratch, which is 1 << 20 bytes?
compare() {
    if [ "$(wc -c < "$1")" -gt 1048576 ]; then
        echo "$1 is bigger than scratch"
    else
        echo "$1 is smaller than scratch"
    fi
}

say cc -std=c17 -Wall -Wextra -O2 -o layout layout.c
say ./layout
say compare layout
say "sed 's/^char scratch\[1 << 20\];/char scratch[1 << 20] = { 1 };/' layout.c > filled.c"
say "grep '^char scratch' layout.c filled.c"
say cc -std=c17 -Wall -Wextra -O2 -o filled filled.c
say ./filled
say compare filled
