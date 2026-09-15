#!/usr/bin/env bash
# Build symbols.c, hand the program to Ghidra's headless analyzer, and print its
# Symbol Table as SymbolTableTour.java reads it: the rows, the queries the
# Symbol Table Filter dialog builds -- run through Ghidra's own filter class --
# and what renaming, deleting and pinning a symbol do.
#
#   demo/symbol_table.sh            the clang -O0 build, with its symbol table
#   demo/symbol_table.sh -s         the same build, stripped
#   demo/symbol_table.sh <binary>   an executable built elsewhere -- by GCC in Docker, say
#
# Needs a Ghidra install. Homebrew's is found through `brew --prefix ghidra`;
# any other one through GHIDRA_INSTALL_DIR, the folder holding support/.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
ghidra=${GHIDRA_INSTALL_DIR:-$(brew --prefix ghidra)/libexec}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

if [ $# -ge 1 ] && [ -f "$1" ]; then
    binary=$1
else
    binary=$work/symbols
    cc -std=c17 -Wall -Wextra -O0 -o "$binary" "$here/symbols.c"
    if [ "${1:-}" = -s ]; then
        strip "$binary"
    fi
fi

"$ghidra/support/analyzeHeadless" "$work" symbols -import "$binary" \
    -scriptPath "$here" \
    -postScript SymbolTableTour.java "$work/tour.txt" \
    -deleteProject > "$work/ghidra.log" 2>&1 || { cat "$work/ghidra.log"; exit 1; }

# A post-script that throws does not fail analyzeHeadless; a missing file does.
[ -s "$work/tour.txt" ] || { cat "$work/ghidra.log"; exit 1; }
cat "$work/tour.txt"
