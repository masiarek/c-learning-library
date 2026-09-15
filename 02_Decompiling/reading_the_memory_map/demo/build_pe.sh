#!/usr/bin/env bash
# Build layout.c as the kind of file Ghidra's Memory Map help pictures: a 32-bit
# Windows executable at image base 0x00400000, importing from kernel32.dll, with
# a version resource. No Windows, no Visual Studio and no C runtime -- clang,
# lld-link, llvm-rc and llvm-dlltool, which Debian and Ubuntu package as clang,
# lld and llvm.
#
#   demo/build_pe.sh <out dir>            writes <out dir>/layout.exe and layout.pdb
#
# A Mac has clang but not lld; the page runs this in Docker. Keep the .pdb beside
# the .exe: Ghidra's PDB analyzer looks there, and it is where the names come from.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
out=$(cd "${1:-.}" && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cp "$here/layout.c" "$here/layout.rc" "$here/kernel32.def" "$work"/
cd "$work"

# Relative names from here on: lld-link and llvm-rc take /-options, so an
# absolute path could be read as one. -debug writes the names to layout.pdb, and
# the .exe only points at it. -fixed leaves out the relocations, and with them
# the .reloc section.
clang --target=i686-pc-windows-msvc -std=c17 -Wall -Wextra -O2 -c -o layout.obj layout.c
llvm-rc -FO layout.res layout.rc
llvm-dlltool -m i386 -k -d kernel32.def -l kernel32.lib
lld-link -nologo -entry:start -subsystem:console -fixed -nodefaultlib -debug -pdb:layout.pdb \
    -out:layout.exe layout.obj layout.res kernel32.lib
cp layout.exe layout.pdb "$out"/
