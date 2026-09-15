#!/usr/bin/env bash
# Hand a program to Ghidra's headless analyzer and print its memory map as
# MemoryMapTour.java reads it: the rows, then what unchecking W, checking
# Volatile, adding an overlay and setting the image base each did.
#
#   demo/memory_map.sh              the clang -O2 build of layout.c, on this machine
#   demo/memory_map.sh <binary>     an executable built elsewhere -- build_pe.sh's
#                                   layout.exe, or GCC's ELF from Docker
#
# Needs a Ghidra install. Homebrew's is found through `brew --prefix ghidra`;
# any other one through GHIDRA_INSTALL_DIR, the folder holding support/.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
ghidra=${GHIDRA_INSTALL_DIR:-$(brew --prefix ghidra)/libexec}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

if [ $# -ge 1 ]; then
    binary=$1
else
    binary=$work/layout
    cc -std=c17 -Wall -Wextra -O2 -o "$binary" "$here/layout.c"
fi

"$ghidra/support/analyzeHeadless" "$work" layout -import "$binary" \
    -scriptPath "$here" \
    -postScript MemoryMapTour.java "$work/tour.txt" \
    -deleteProject > "$work/ghidra.log" 2>&1 || { cat "$work/ghidra.log"; exit 1; }

# A post-script that throws does not fail analyzeHeadless; a missing file does.
[ -s "$work/tour.txt" ] || { cat "$work/ghidra.log"; exit 1; }
cat "$work/tour.txt"
