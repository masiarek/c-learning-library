#!/usr/bin/env bash
# trap.mk lists each .c file and forgets the header. Change the header and make
# sees nothing newer than anything it knows about -- no error, and a stale program.
#
# Runs in a scratch copy of ../demo. Prints only what GNU Make 3.81 and 4.x print
# alike: the commands make runs, what the program says, and make -q's exit status
# (0 = nothing to rebuild, 1 = something would be).
set -u
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cp ../demo/* "$work"/
cd "$work" || exit 1

say()    { printf '$ %s\n' "$*"; eval "$*" 2>&1; }
status() { printf '$ %s\n' "$*"; eval "$*" >/dev/null 2>&1; echo "exit $?"; }

say make -f trap.mk
say ./hello

# make 3.81 compares whole seconds, so wait one before the edit.
sleep 1
say "sed 's/Hello/Goodbye/' greet.h > greet.h.new && mv greet.h.new greet.h"
status make -q -f trap.mk
say ./hello
say make -B -f trap.mk
say ./hello
