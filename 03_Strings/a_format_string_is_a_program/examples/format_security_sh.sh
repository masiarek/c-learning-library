#!/usr/bin/env bash
# Build the vulnerable greeter and the fixed one, ask each compiler to refuse
# the vulnerable one, and run both on the same input.
#
# Runs in a scratch copy of ../demo. GCC and clang word their warnings
# differently, so the compile steps print only an exit status; the messages
# themselves are on the page in fences that name the compiler. The runs print
# what the programs print, which is the same on both machines.
set -u
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cp ../demo/*.c "$work"/
cd "$work" || exit 1

say()    { printf '$ %s\n' "$*"; eval "$*" 2>&1; }
status() { printf '$ %s\n' "$*"; eval "$*" >/dev/null 2>&1; echo "exit $?"; }

status cc -std=c17 -Wall -Wextra -Werror=format-security -o greet greet.c
status cc -std=c17 -Wall -Wextra -Werror=format-security -o greet_fixed greet_fixed.c
status cc -std=c17 -Wall -Wextra -Werror=format -o wrong_type wrong_type.c

# Without -Werror the vulnerable program builds (with a warning, not shown).
status cc -std=c17 -o greet greet.c

# One plain word survives either program unchanged.
say "./greet hello"
say "./greet_fixed hello"

# A % in the input is where they part: greet runs it as a format directive,
# greet_fixed prints it. "%%" is the literal two-character percent-sign.
say "./greet '100%% done'"
say "./greet_fixed '100%% done'"
