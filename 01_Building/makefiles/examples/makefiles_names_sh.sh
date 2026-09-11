#!/usr/bin/env bash
# make compares names and timestamps and nothing else: an older file put back is
# not an edit, and a target is only a file name -- which is what .PHONY is for.
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

say make

# An edit, then the file's timestamp set back to 2020: new contents, old date.
sleep 1
say "sed 's/Hello/Goodbye/' greet.h > greet.h.new && mv greet.h.new greet.h"
say touch -t 202001010000 greet.h
status make -q
say ./hello

# A stray file called clean, with and without the .PHONY line.
say touch clean
status make -q clean
say "grep -v '^[.]PHONY' Makefile > nophony.mk"
status make -q -f nophony.mk clean
