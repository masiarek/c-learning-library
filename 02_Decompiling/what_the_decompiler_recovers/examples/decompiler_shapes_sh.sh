#!/usr/bin/env bash
# Build the demo program the way the page decompiles it, run it, and ask the
# file what it still holds: the function names the linker exported, the format
# string, and none of the source's parameter, local or struct names -- then
# strip it and ask again.
#
# Runs in a scratch copy of ../demo. Prints only what GCC on Linux and Apple
# clang on macOS print alike: nm's addresses and the Mach-O underscore are
# removed before the names are listed, and the two binaries are never dumped.
set -u
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cp ../demo/shapes.c "$work"/
cd "$work" || exit 1

say() { printf '$ %s\n' "$*"; eval "$*" 2>&1; }

# The function names the file exports, without addresses or a leading underscore.
names() {
    nm -g --defined-only "$1" 2>/dev/null | awk '{print $NF}' | sed 's/^_//' \
        | grep -x -E 'weighted|sum_to|norm2|shout|main' | sort | tr '\n' ' '
    echo
}

say cc -std=c17 -Wall -Wextra -O2 -o shapes shapes.c
say ./shapes
say names shapes
say "grep -c -a 'has %zu letters' shapes"
say "grep -c -a -w -E 'point|word|total' shapes"
say strip shapes
say names shapes
say ./shapes
