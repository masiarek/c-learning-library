#!/usr/bin/env bash
# Build shapes.c, hand the program to Ghidra's headless analyzer, and print what
# the decompiler gives back -- first as the file arrived, then after
# ApplyPointType.java tells Ghidra about struct point.
#
# Needs a Ghidra install. Homebrew's is found through `brew --prefix ghidra`;
# any other one through GHIDRA_INSTALL_DIR, the folder holding support/.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
ghidra=${GHIDRA_INSTALL_DIR:-$(brew --prefix ghidra)/libexec}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

cc -std=c17 -Wall -Wextra -O2 -o "$work/shapes" "$here/shapes.c"

"$ghidra/support/analyzeHeadless" "$work" shapes -import "$work/shapes" \
    -scriptPath "$here" \
    -postScript DumpDecompiled.java "$work/before.txt" weighted sum_to norm2 shout \
    -postScript ApplyPointType.java "$work/after.txt" \
    -deleteProject > "$work/ghidra.log" 2>&1

cat "$work/before.txt" "$work/after.txt"
