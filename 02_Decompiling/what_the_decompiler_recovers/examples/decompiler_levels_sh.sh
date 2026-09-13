#!/usr/bin/env bash
# The same source at -O0 and -O2: two executables that differ byte for byte --
# the page decompiles both -- and print the same lines. Runs in a scratch copy
# of ../demo; prints only what GCC on Linux and Apple clang on macOS print
# alike, so cmp is asked for its exit status and not its message.
set -u
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cp ../demo/shapes.c "$work"/
cd "$work" || exit 1

say()    { printf '$ %s\n' "$*"; eval "$*" 2>&1; }
status() { printf '$ %s\n' "$*"; eval "$*" >/dev/null 2>&1; echo "exit $?"; }

say cc -std=c17 -Wall -Wextra -O0 -o shapes0 shapes.c
say cc -std=c17 -Wall -Wextra -O2 -o shapes2 shapes.c
status cmp -s shapes0 shapes2
say ./shapes0
say ./shapes2
