#!/usr/bin/env bash
# Build prologues.c, hand the program to Ghidra's headless analyzer, and print
# what the Function Bit Patterns Explorer shows for it -- gathered and worked
# through by ExploreFunctionStarts.java, without the window. With KEEP set to a
# directory, also keeps the XML the explorer's "Read XML Files" button reads and
# the pattern file its Export button writes.
#
#   demo/patterns.sh            the clang -O0 build
#   demo/patterns.sh -O2        any other optimisation level
#   demo/patterns.sh <binary>   an executable built elsewhere -- by GCC in Docker, say
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
    level=${1:--O0}
    binary=$work/prologues$level
    cc -std=c17 -Wall -Wextra "$level" -o "$binary" "$here/prologues.c"
fi

mkdir -p "$work/xml"
"$ghidra/support/analyzeHeadless" "$work" prologues -import "$binary" \
    -scriptPath "$here" \
    -postScript ExploreFunctionStarts.java "$work/explorer.txt" "$work/xml" \
    -deleteProject > "$work/ghidra.log" 2>&1 || { cat "$work/ghidra.log"; exit 1; }

cat "$work/explorer.txt"
if [ -n "${KEEP:-}" ]; then
    mkdir -p "$KEEP"
    cp "$work"/xml/* "$KEEP"/
fi
