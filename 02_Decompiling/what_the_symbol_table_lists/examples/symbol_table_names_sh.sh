#!/usr/bin/env bash
# Build the demo program, run it, and ask the file which names it holds: the
# program's own, split the way nm splits them into global and local, and the
# library functions it calls -- then strip it and ask again.
#
# Runs in a scratch copy of ../demo. Prints only what GCC on Linux and Apple
# clang on macOS print alike: nm's addresses, its type letters and the Mach-O
# underscore are dropped, and the names the C runtime adds are left out.
set -u
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cp ../demo/symbols.c "$work"/
cd "$work" || exit 1

say() { printf '$ %s\n' "$*"; eval "$*" 2>&1; }

own='counter|greeting|tail|helper|twice|via_table|table|orphan|main'

# The program's own names. A lowercase nm letter marks a local name -- one only
# this file could use -- and an uppercase letter a global one.
names() {
    nm "$1" 2>/dev/null \
        | awk 'NF == 3 { sub(/^_/, "", $3); print ($2 ~ /[a-z]/ ? "local" : "global"), $3 }' \
        | grep -E " ($own)\$" | sort \
        | awk '{ n[$1] = n[$1] " " $2 } END { printf "global:%s\nlocal:%s\n", n["global"], n["local"] }'
}

# The library functions it calls. Linux keeps them in the dynamic symbol table,
# which nm -D reads; macOS in the one nm reads by default.
imports() {
    { nm -u "$1"; nm -D -u "$1"; } 2>/dev/null | awk '{ print $NF }' | sed 's/^_//; s/@.*//' \
        | grep -x -E 'printf|puts' | sort -u | tr '\n' ' '
    echo
}

say cc -std=c17 -Wall -Wextra -O0 -o symbols symbols.c
say ./symbols
say names symbols
say imports symbols
say "grep -c -a objc_msgSend_rtp symbols"
say strip symbols
say names symbols
say imports symbols
say "grep -c -a 'nobody calls this' symbols"
say ./symbols
