#!/usr/bin/env bash
# terse.mk has no recipes at all: make's built-in rules supply every command. And
# the built-in link rule needs a program named after one of its objects -- with a
# main.c instead of hello.c, make compiles everything, links nothing, and exits 0.
#
# Runs in a scratch copy of ../demo. Prints only what GNU Make 3.81 and 4.x print
# alike -- make's "Nothing to be done" message is left out, since the two versions
# quote the target's name differently.
set -u
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cp ../demo/* "$work"/
cd "$work" || exit 1

say()    { printf '$ %s\n' "$*"; eval "$*" 2>&1; }
status() { printf '$ %s\n' "$*"; eval "$*" >/dev/null 2>&1; echo "exit $?"; }

say make -f terse.mk
say ./hello
status make -q -f terse.mk

# A fresh folder where the main file is called main.c.
mkdir named && cp hello.c greet.c greet.h named/ && cd named || exit 1
say mv hello.c main.c
say "printf 'CFLAGS = -Wall -O2\n\nhello: main.o greet.o\n' > named.mk"
say make -f named.mk
status make -f named.mk
say ls
