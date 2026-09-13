#!/usr/bin/env bash
# Which functions does Ghidra find in a stripped program, and by what means?
#
# Builds prologues.c with the Mac's clang, strips it, and analyzes it four ways:
# as linked, and linked without the Mach-O function-starts table; each with the
# analyzers as shipped, and with the pattern-based function start search off.
# Then the same two analyses for any executable named on the command line -- an
# ELF built by GCC in Docker, say -- which must come with a stripped copy beside
# it named <binary>.stripped, because the strip on a Mac only knows Mach-O.
# One line per run: how many functions Ghidra found in the program's own code,
# and which of the seven in prologues.c it did not find.
#
#   demo/found.sh [binary ...]
#
# Needs a Ghidra install. Homebrew's is found through `brew --prefix ghidra`;
# any other one through GHIDRA_INSTALL_DIR, the folder holding support/.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
ghidra=${GHIDRA_INSTALL_DIR:-$(brew --prefix ghidra)/libexec}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
out=$work/found.txt

# name=address for the seven functions in prologues.c, from a build that still has its symbol table.
named() {
    nm "$1" | awk '{ name = $3; sub(/^_/, "", name)
        if (name ~ /^(twice|clamp|sum|greet_len|say|orphan|main)$/) { printf "%s%s=%s", sep, name, $1; sep = "," } }
        END { print "" }'
}

# analyze <label> <binary> <name=address,...> [analyzeHeadless options...]
analyze() {
    local label=$1 binary=$2 addr=$3
    shift 3
    "$ghidra/support/analyzeHeadless" "$work" found -import "$binary" -scriptPath "$here" "$@" \
        -postScript FunctionsFound.java "$out" "$label" "$addr" \
        -deleteProject > "$work/ghidra.log" 2>&1 || { cat "$work/ghidra.log"; exit 1; }
}

cc -std=c17 -Wall -Wextra -O0 -o "$work/mac" "$here/prologues.c"
addr=$(named "$work/mac")
strip "$work/mac"
analyze "clang -O0, stripped" "$work/mac" "$addr"
analyze "  the same, pattern search off" "$work/mac" "$addr" -preScript NoPatternSearch.java

cc -std=c17 -Wall -Wextra -O0 -Wl,-no_function_starts -o "$work/mac_nofs" "$here/prologues.c"
addr=$(named "$work/mac_nofs")
strip "$work/mac_nofs"
analyze "clang -O0, stripped, no LC_FUNCTION_STARTS" "$work/mac_nofs" "$addr"
analyze "  the same, pattern search off" "$work/mac_nofs" "$addr" -preScript NoPatternSearch.java

for binary in "$@"; do
    [ -f "$binary.stripped" ] || { echo "need $binary.stripped next to $binary" >&2; exit 1; }
    addr=$(named "$binary")
    analyze "$(basename "$binary"), stripped" "$binary.stripped" "$addr"
    analyze "  the same, pattern search off" "$binary.stripped" "$addr" -preScript NoPatternSearch.java
done

cat "$out"
