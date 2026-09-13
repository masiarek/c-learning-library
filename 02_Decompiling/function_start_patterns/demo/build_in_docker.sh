#!/usr/bin/env bash
# Build prologues.c with GCC on Linux, in Docker's gcc:14 image, so the page can
# put the same source through a second compiler: at -O0 and -O2, each as GCC
# links it and again without the unwind table in .eh_frame, and each with a
# stripped copy made there, because the strip on a Mac only knows Mach-O.
#
#   demo/build_in_docker.sh <output directory>
set -eu
here=$(cd "$(dirname "$0")" && pwd)
outdir=$(mkdir -p "$1" && cd "$1" && pwd)
docker run --rm -v "$here:/src:ro" -v "$outdir:/out" gcc:14 sh -c '
    build() {  # build <name> <gcc options...>
        name=$1; shift
        gcc -std=c17 -Wall -Wextra "$@" -o /out/$name /src/prologues.c
        cp /out/$name /out/$name.stripped
        strip /out/$name.stripped
    }
    build prologues_gcc0 -O0
    build prologues_gcc2 -O2
    build prologues_gcc0_nounwind -O0 -fno-asynchronous-unwind-tables
    build prologues_gcc2_nounwind -O2 -fno-asynchronous-unwind-tables
    gcc --version | head -1'
ls "$outdir"
