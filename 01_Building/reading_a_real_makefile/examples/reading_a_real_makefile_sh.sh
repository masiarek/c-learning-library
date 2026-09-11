#!/usr/bin/env bash
# The twin of the talk's Makefile, run: the three rules it writes for itself, what
# `make run` passes through, and what one missing $ does to the rule eval writes.
#
# Runs in a scratch copy of ../demo. Prints only what GNU Make 3.81 (the macOS
# runner) and 4.x (the Ubuntu runner) print alike: the commands make runs, what
# the programs say, and make -q's exit status (0 = nothing to rebuild).
set -u
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cp ../demo/* "$work"/
cd "$work" || exit 1

say()    { printf '$ %s\n' "$*"; eval "$*" 2>&1; }
status() { printf '$ %s\n' "$*"; eval "$*" >/dev/null 2>&1; echo "exit $?"; }

say make -n
say make
say make run
say "make run ARGS='--fast --verbose'"
status make -q

# The same Makefile with the recipe's $$@ written as a single $@.
say 'sed "s/[$][$]@/\$@/" Makefile > onedollar.mk'
say "grep -e '-o ' onedollar.mk"
say make -n -B -f onedollar.mk variant.alpha
