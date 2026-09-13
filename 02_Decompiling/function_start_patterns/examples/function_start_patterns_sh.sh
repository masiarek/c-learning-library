#!/usr/bin/env bash
# Build the demo program, run it, and ask the file about the function nothing
# calls: it is there before strip, by name, and still there after, by the one
# string only it uses. Runs in a scratch copy of ../demo. Prints only what GCC
# on Linux and Apple clang on macOS print alike: nm's addresses and the Mach-O
# underscore are removed before the names are listed.
set -u
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cp ../demo/prologues.c "$work"/
cd "$work" || exit 1

say() { printf '$ %s\n' "$*"; eval "$*" 2>&1; }

# The function names the file exports, without addresses or a leading underscore.
names() {
    nm -g --defined-only "$1" 2>/dev/null | awk '{print $NF}' | sed 's/^_//' \
        | grep -x -E 'twice|clamp|sum|greet_len|say|orphan|main' | sort | tr '\n' ' '
    echo
}

say cc -std=c17 -Wall -Wextra -O0 -o prologues prologues.c
say ./prologues
say names prologues
say "grep -c -a 'nobody calls this' prologues"
say strip prologues
say names prologues
say "grep -c -a 'nobody calls this' prologues"
say ./prologues
