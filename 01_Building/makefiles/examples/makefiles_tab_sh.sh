#!/usr/bin/env bash
# A recipe line must start with a TAB. expand(1) does to the demo Makefile what a
# Markdown renderer or a tab-to-spaces editor does to it, and make refuses the result.
#
# Runs in a scratch copy of ../demo. `cat -et` prints a TAB as ^I and marks each
# line end with $, and prints the same thing with BSD cat (macOS) and GNU cat.
set -u
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cp ../demo/* "$work"/
cd "$work" || exit 1

say() { printf '$ %s\n' "$*"; eval "$*" 2>&1; }

say "expand -t 4 Makefile > spaces.mk"
say make -f spaces.mk
say "head -5 Makefile | cat -et"
say "head -5 spaces.mk | cat -et"
