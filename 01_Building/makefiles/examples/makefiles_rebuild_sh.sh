#!/usr/bin/env bash
# What make does with the demo Makefile: build in order, then rebuild only what
# a changed timestamp reaches.
#
# Runs in a scratch copy of ../demo, so the lesson folder stays clean. Prints only
# what GNU Make 3.81 (the macOS runner) and 4.x (the Ubuntu runner) print alike:
# the commands make runs, what the program says, and make -q's exit status
# (0 = nothing to rebuild, 1 = something would be). make's own "is up to date"
# message is left out on purpose -- the two versions quote the name differently.
set -u
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cp ../demo/* "$work"/
cd "$work" || exit 1

say()    { printf '$ %s\n' "$*"; eval "$*" 2>&1; }
status() { printf '$ %s\n' "$*"; eval "$*" >/dev/null 2>&1; echo "exit $?"; }

say make
say ./hello
status make -q

# make 3.81 compares timestamps to the whole second, so an edit made in the same
# second as the build is invisible to it. Every edit below waits one second first.
sleep 1
say touch greet.c
status make -q
say make

sleep 1
say touch greet.h
say make -n
say make
