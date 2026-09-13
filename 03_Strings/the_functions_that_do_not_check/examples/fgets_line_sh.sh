#!/usr/bin/env bash
# Build the demo reader and feed it lines shorter and longer than its buffer.
# fgets never writes past the buffer: a long line arrives in pieces, and the
# newline -- kept when it fits -- is how the reader tells the pieces apart.
#
# Runs in a scratch copy of ../demo. Everything printed is the demo program's
# own output, which GCC on Linux and clang on macOS build alike.
set -u
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cp ../demo/read_line.c "$work"/
cd "$work" || exit 1

say() { printf '$ %s\n' "$*"; eval "$*" 2>&1; }

say cc -std=c17 -Wall -Wextra -pedantic -o read_line read_line.c
say "printf 'hi\\n' | ./read_line"
say "printf 'hello world\\n' | ./read_line"
say "printf 'no newline at all' | ./read_line"
say "printf 'ab\\ncd\\n' | ./read_line"
