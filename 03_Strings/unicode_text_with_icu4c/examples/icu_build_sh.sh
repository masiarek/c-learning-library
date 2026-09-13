#!/usr/bin/env bash
# Build the demo against ICU: once by hand with pkg-config, once with the demo
# Makefile, and once with the header flags but not the library flags -- which
# gets past the compiler and fails at the linker.
#
# Runs in a scratch copy of ../demo. The linker's message differs between ld on
# macOS and GNU ld on Linux, so the failed link reports only its exit status;
# both messages are on the page, labelled with the machine.
set -u
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cp ../demo/hello_icu.c ../demo/Makefile "$work"/
cd "$work" || exit 1

say() { printf '$ %s\n' "$*"; eval "$*" 2>&1; }

say 'pkg-config --exists icu-uc icu-i18n && echo "pkg-config finds ICU"'
say 'cc -std=c17 -Wall -Wextra -pedantic hello_icu.c $(pkg-config --cflags --libs icu-uc) -o hello_icu'
say ./hello_icu
say rm hello_icu
say 'make -s hello_icu && ./hello_icu'
say 'cc -std=c17 hello_icu.c $(pkg-config --cflags icu-uc) -o no_libs 2>/dev/null; echo "exit status $?"'
