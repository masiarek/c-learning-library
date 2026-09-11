#!/usr/bin/env bash
# deps.mk lets the compiler write the header lists: -MMD makes it write one .d
# file per object, -MP adds an empty rule per header, -include reads them back.
#
# Runs in a scratch copy of ../demo. The .d files are the same bytes from Apple
# clang and from GCC, which is what lets them sit in an answer key.
set -u
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cp ../demo/* "$work"/
cd "$work" || exit 1

say()    { printf '$ %s\n' "$*"; eval "$*" 2>&1; }
status() { printf '$ %s\n' "$*"; eval "$*" >/dev/null 2>&1; echo "exit $?"; }

say make -f deps.mk
say cat greet.d
say cat hello.d

# make 3.81 compares whole seconds, so wait one before the edit.
sleep 1
say "sed 's/Hello/Goodbye/' greet.h > greet.h.new && mv greet.h.new greet.h"
status make -q -f deps.mk
say make -f deps.mk
say ./hello
