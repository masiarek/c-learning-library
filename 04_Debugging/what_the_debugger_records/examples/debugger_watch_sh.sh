#!/usr/bin/env bash
# Build the program the page debugs, run it, and ask the file which of its four
# kinds of memory it actually holds bytes for. nm's addresses and the Mach-O
# underscore are removed, so GCC on Linux and Apple clang on macOS print alike.
# -fno-common is GCC's default and is passed so both compilers are asked the
# same thing; Apple's linker files `counter` under __DATA,__common regardless.
#
# Runs in a scratch copy of ../demo.
set -u
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cp ../demo/watch.c "$work"/
cd "$work" || exit 1

say() { printf '$ %s\n' "$*"; eval "$*" 2>&1; }

# What kind of thing the file says each of the four names is, in words. nm's
# letter is T for code and D for data on both machines; for the zero-filled
# section GNU nm says B and Apple's nm says S, so both are folded into one word.
kinds() {
    nm "$1" 2>/dev/null | awk '{print $2, $3}' | sed 's/ _/ /' \
        | grep -E ' (bump|main|table|counter)$' | sort -k2 \
        | sed 's/^T /code       /; s/^D /data       /; s/^[BS] /zero-fill  /'
}

say cc -std=c17 -Wall -Wextra -O0 -g -fno-common -o watch watch.c
say ./watch
say kinds watch

# The same array declared two ways, and what each costs the file.
printf 'int main(void) { return 0; }\n' > plain.c
printf 'int big[100000];\nint main(void) { return big[0]; }\n' > bss.c
printf 'int big[100000] = { 1 };\nint main(void) { return big[0]; }\n' > data.c
say cc -std=c17 -Wall -Wextra -O0 -o plain plain.c
say cc -std=c17 -Wall -Wextra -O0 -o bss bss.c
say cc -std=c17 -Wall -Wextra -O0 -o data data.c
plain=$(wc -c < plain); bss=$(wc -c < bss); data=$(wc -c < data)
[ $((bss - plain)) -lt 4096 ]    && echo "bss:  a 400000-byte array added under 4096 bytes to the file"
[ $((data - plain)) -ge 400000 ] && echo "data: the same array, initialized, added at least 400000 bytes"
