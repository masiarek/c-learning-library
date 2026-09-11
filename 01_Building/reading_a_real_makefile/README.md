# Reading a real Makefile

**Level:** 201 → 301 · for anyone handed a repository with a Makefile at the top

**One line:** The benchmarks behind a C++Now 2026 keynote are built by a 31-line Makefile that writes most of its own rules — `define` holds a rule with a hole in it, `$(call)` fills the hole, `$(eval)` turns the text into a real rule, and `$$` is the one character that has to survive both.

## The file

This is [`benchmarks/Makefile` ↗](https://github.com/hudson-trading/its-about-time/blob/c0240a2a997ba6ec4983ac6223432f538cb16235/benchmarks/Makefile) from [*It's About Time* ↗](https://github.com/hudson-trading/its-about-time) — the slides and benchmarks for Matt Godbolt's closing keynote at C++Now 2026, a talk about benchmarking, published by Hudson River Trading. It is quoted whole, as of commit `c0240a2` (2026-06-08, the last commit to touch it). It is BSD 2-Clause licensed: its copyright notice is its own first line, and the licence text is folded below it.

```make
# Copyright (c) 2026 Hudson River Trading LLC. See LICENSE.

CXX      = g++
CXXFLAGS = -std=c++23 -O3 -march=x86-64-v3 -Wall -Wextra -Wconversion -Werror -pedantic -static

CLOCKS   = steady system rdtsc rdtscp tsc_fenced monotonic mono_raw mono_coarse
SOURCES  = $(wildcard *.cpp)

BINARIES = $(foreach clock,$(CLOCKS),benchmark.$(clock))

all: $(BINARIES)

# Link all .cpp files into one binary per clock
define CLOCK_RULE
benchmark.$(1): $(SOURCES) harness.hpp Makefile
	$(CXX) $(CXXFLAGS) -DBENCH_CLOCK=$(1) $(SOURCES) -o $$@
endef

$(foreach clock,$(CLOCKS),$(eval $(call CLOCK_RULE,$(clock))))

run: all
	@for bin in $(BINARIES); do \
		echo "=== $$bin ==="; \
		./$$bin $(FILTER); \
		echo; \
	done

clean:
	rm -f $(BINARIES)

.PHONY: all run clean
```

<details markdown="1">
<summary>The BSD 2-Clause licence the file is published under</summary>

```text
Copyright (c) 2026 Hudson River Trading LLC

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

1. Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in the
   documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
SUCH DAMAGE.
```

</details>

What it builds: the same six `.cpp` files, compiled eight times into eight programs — `benchmark.steady`, `benchmark.system`, `benchmark.rdtsc` and five more — each one timing the same benchmarks with a different clock. Nothing in the file names those eight rules. The Makefile writes them itself, from the list on line 6, and the rest of this page is how.

## Line by line

### The compiler and its flags

`CXX` and `CXXFLAGS` are the conventional names for the C++ compiler and its flags — the names `make`'s built-in rules read, although this file never uses a built-in rule.

| Flag | Asks for |
|---|---|
| `-std=c++23` | the 2023 C++ standard |
| `-O3` | the optimizer's most aggressive level — the point of a benchmark |
| `-march=x86-64-v3` | the AVX2-era x86 baseline: the compiler may use instructions older x86 chips do not have, and the flag does not exist for ARM at all |
| `-Wall -Wextra -Wconversion -pedantic` | a wide set of warnings, including implicit conversions that can change a value |
| `-Werror` | every one of those warnings is an error |
| `-static` | link libc and the C++ library into the program, so it needs no shared libraries on the machine that runs it |

### A list, and a list made from a list

```make
CLOCKS   = steady system rdtsc rdtscp tsc_fenced monotonic mono_raw mono_coarse
SOURCES  = $(wildcard *.cpp)

BINARIES = $(foreach clock,$(CLOCKS),benchmark.$(clock))
```

`CLOCKS` is eight words and nothing more. `$(wildcard *.cpp)` asks the filesystem, when the Makefile is read, which `.cpp` files exist — so a new benchmark file joins the build without an edit here. `$(foreach clock,$(CLOCKS),benchmark.$(clock))` walks the eight words and turns each into a file name: `BINARIES` is `benchmark.steady benchmark.system … benchmark.mono_coarse`.

`all: $(BINARIES)` comes first in the file, so it is what a bare `make` builds: all eight.

### A rule with a hole in it

```make
define CLOCK_RULE
benchmark.$(1): $(SOURCES) harness.hpp Makefile
	$(CXX) $(CXXFLAGS) -DBENCH_CLOCK=$(1) $(SOURCES) -o $$@
endef
```

`define … endef` stores several lines in one variable, `CLOCK_RULE`. What it stores is the text of a rule, with `$(1)` where the clock's name will go. Nothing is a rule yet — it is a template.

### `$(call)` fills the hole, `$(eval)` makes it a rule

```make
$(foreach clock,$(CLOCKS),$(eval $(call CLOCK_RULE,$(clock))))
```

Read it from the inside. `$(call CLOCK_RULE,steady)` expands the template with `$(1)` set to `steady` and returns text — a rule for `benchmark.steady`. `$(eval …)` hands that text back to `make`'s own parser, as though it had been typed into the Makefile at this line. `$(foreach …)` does both once per clock. The result is eight ordinary rules, and `make -n` prints the command each one would run:

```text title="Abridged — real output, GNU Make 3.81 on macOS, the first two of eight lines; 4.4.1 on Linux prints the same eight"
$ make -n
g++ -std=c++23 -O3 -march=x86-64-v3 -Wall -Wextra -Wconversion -Werror -pedantic -static -DBENCH_CLOCK=steady branch_predict.cpp clocks.cpp dot_product.cpp harness.cpp matmul.cpp modulus.cpp -o benchmark.steady
g++ -std=c++23 -O3 -march=x86-64-v3 -Wall -Wextra -Wconversion -Werror -pedantic -static -DBENCH_CLOCK=system branch_predict.cpp clocks.cpp dot_product.cpp harness.cpp matmul.cpp modulus.cpp -o benchmark.system
```

`-DBENCH_CLOCK=steady` defines a macro for that compile, and the benchmark harness turns the name into the clock it times with; with no `-D` it falls back to `steady`. `$(call)` expanded the other variables in the template as well, so the rule `eval` stored names the six source files outright — `make -qp` prints `make`'s whole database, and there the rule reads:

```text title="Abridged — real output, GNU Make 3.81 on macOS, one line of make -p's database"
benchmark.steady: branch_predict.cpp clocks.cpp dot_product.cpp harness.cpp matmul.cpp modulus.cpp harness.hpp Makefile
```

Use `-qp`, not `-p`: `-p` prints the database and then runs the build, while `-q` makes it build nothing.

### Why `$$@`

`$(call)` expands every `$` in the template, including the one in `$@` — and at that moment `$@` is empty, because `make` is still reading the file and no rule is running. Written `$@`, the template would come out of `$(call)` as `-o` with nothing after it. Written `$$@`, `$(call)` turns the `$$` into a single `$` and leaves `$@` standing for `$(eval)`, which reads it as the automatic variable it is: the target's name, filled in when the recipe runs. The twin below makes that mistake on purpose.

### Every program depends on the Makefile

`benchmark.$(1): $(SOURCES) harness.hpp Makefile` — three kinds of prerequisite. Every `.cpp` file, because each program is compiled from all six. `harness.hpp`, by hand: it is the one header every source includes, so this is the header list [the Makefiles lesson](../makefiles/README.md#the-trap-a-header-nobody-listed) warns about — and it is complete, since the directory's other header, `harness-for-ce.hpp`, is included by nothing. And `Makefile` itself: `make` compares timestamps, not flags, so a new flag in `CXXFLAGS` would otherwise rebuild nothing. Listing the Makefile makes any edit to it rebuild all eight.

There are no object files. Each program compiles all six sources in one command, so a full build is forty-eight compilations. Object files would not help much here: every source includes `harness.hpp`, where `-DBENCH_CLOCK` picks the clock, so each object would have to be compiled once per clock anyway.

### One command, five lines

```make
run: all
	@for bin in $(BINARIES); do \
		echo "=== $$bin ==="; \
		./$$bin $(FILTER); \
		echo; \
	done
```

Four details, each the answer to a question a newcomer asks:

- **Each recipe line runs in its own shell**, so a loop has to be one line. The backslashes join the five into one command.
- **`$$bin`** is the same escape as `$$@`: `make` expands `$`, so the shell's own variable needs `$$` to reach the shell as `$bin`.
- **`@`** at the start stops `make` printing the command before running it — without it, the whole loop would be echoed first.
- **`$(FILTER)`** is set by nobody in the file. It is there to be set on the command line: `make run FILTER=matmul` passes `matmul` to every program, and the harness runs only the benchmarks whose names contain it.

`clean` removes the eight programs, and `.PHONY: all run clean` says that none of those three names is a file. It can sit at the bottom: `make` reads the whole file before it builds anything.

## The twin, verified

The talk's file needs Linux on x86-64 to build, so it cannot be an answer key here. [`demo/`](demo/Makefile) holds a small twin — the same `define`, `$(call)`, `$(eval)`, `$(foreach)` and `$$` machinery, building one program per variant from one C file — and this script runs it on both CI machines:

<!-- output:reading_a_real_makefile_sh -->
*Verified output of [`reading_a_real_makefile_sh.sh`](examples/reading_a_real_makefile_sh.sh) — regenerated by `tools/run_examples.py`, never hand-typed.*

```text
$ make -n
cc -std=c17 -Wall -Wextra -O2 -DVARIANT=alpha variant.c -o variant.alpha
cc -std=c17 -Wall -Wextra -O2 -DVARIANT=beta variant.c -o variant.beta
cc -std=c17 -Wall -Wextra -O2 -DVARIANT=gamma variant.c -o variant.gamma
$ make
cc -std=c17 -Wall -Wextra -O2 -DVARIANT=alpha variant.c -o variant.alpha
cc -std=c17 -Wall -Wextra -O2 -DVARIANT=beta variant.c -o variant.beta
cc -std=c17 -Wall -Wextra -O2 -DVARIANT=gamma variant.c -o variant.gamma
$ make run
=== variant.alpha ===
variant alpha
=== variant.beta ===
variant beta
=== variant.gamma ===
variant gamma
$ make run ARGS='--fast --verbose'
=== variant.alpha ===
variant alpha --fast --verbose
=== variant.beta ===
variant beta --fast --verbose
=== variant.gamma ===
variant gamma --fast --verbose
$ make -q
exit 0
$ sed "s/[$][$]@/\$@/" Makefile > onedollar.mk
$ grep -e '-o ' onedollar.mk
	$(CC) $(CFLAGS) -DVARIANT=$(1) $(SOURCES) -o $@
$ make -n -B -f onedollar.mk variant.alpha
cc -std=c17 -Wall -Wextra -O2 -DVARIANT=alpha variant.c -o 
```
<!-- /output -->

`make -n` shows the three rules `eval` wrote before any of them runs, and `make` then runs them. `make run` is the talk's loop, with `ARGS` in the part of `FILTER`, passed through to every program. The last three commands are the `$$` mistake: `sed` rewrites the recipe's `$$@` as `$@`, and the rule `eval` writes from it ends at `-o`, with the program's name gone.

## Built for real

On Linux x86-64 it builds as written. Docker's `gcc:14` image (GNU Make 4.4.1, GCC 14.4) built all eight programs:

```text title="Real output — GNU Make 4.4.1 and GCC 14 in Docker's gcc:14 image, x86-64, on one Intel Mac; the timings are that machine's"
make: exit 0, 128 s
8
make -j8: exit 0, 27 s
```

`make -j8` runs up to eight recipes at once, and the eight programs do not depend on each other, so nothing waits: the same build in about a fifth of the time. And `-static` did what it asks for:

```text title="Real output — file(1) in the same container"
benchmark.steady: ELF 64-bit LSB executable, x86-64, version 1 (GNU/Linux), statically linked, for GNU/Linux 3.2.0, with debug_info, not stripped
```

On a Mac it does not build, and not for one reason:

- **A Linux-only clock.** The harness calls `clock_gettime(CLOCK_MONOTONIC_COARSE, …)`, and macOS has no such clock — clang reports `use of undeclared identifier 'CLOCK_MONOTONIC_COARSE'` in every source file.
- **clang's `-Wconversion` is stricter than GCC's.** In C++, clang's includes `-Wsign-conversion` and GCC's does not, so indexing a `std::vector` with an `int` passes GCC 14 and, under `-Werror`, is an error in Apple clang 21 — ten of them in `matmul.cpp` alone.
- **`-static`**, had it got that far. A one-line C program linked with `-static` on this Mac fails at `ld: library 'crt0.o' not found`: macOS ships no static C runtime to link.
- **On Apple silicon, the architecture as well** — measured by aiming this Mac's clang at arm64: `-march=x86-64-v3` is an `unsupported argument`, and `<x86intrin.h>` stops the compile with `This header is only meant to be used on x86 and x64 architecture`.

```text title="Abridged — real output, GNU Make 3.81 and Apple clang 21, macOS on x86-64: the first error of each kind"
$ make benchmark.steady
g++ -std=c++23 -O3 -march=x86-64-v3 -Wall -Wextra -Wconversion -Werror -pedantic -static -DBENCH_CLOCK=steady branch_predict.cpp clocks.cpp dot_product.cpp harness.cpp matmul.cpp modulus.cpp -o benchmark.steady
In file included from branch_predict.cpp:3:
./harness.hpp:100:19: error: use of undeclared identifier 'CLOCK_MONOTONIC_COARSE'; did you mean '_CLOCK_MONOTONIC_RAW'?
dot_product.cpp:17:23: error: implicit conversion changes signedness: 'int' to 'size_type' (aka 'unsigned long') [-Werror,-Wsign-conversion]
make: *** [benchmark.steady] Error 1
```

`g++` on a Mac is Apple clang under another name — `g++ --version` says so — which is why a Makefile written for GCC meets clang's warnings there.

## See also

- [Makefiles: a build graph you write by hand](../makefiles/README.md) — rules, timestamps, the TAB, and the header lists this file writes by hand
- [The GNU `make` manual ↗](https://www.gnu.org/software/make/manual/make.html) — on [`eval` ↗](https://www.gnu.org/software/make/manual/html_node/Eval-Function.html), [`call` ↗](https://www.gnu.org/software/make/manual/html_node/Call-Function.html), [`foreach` ↗](https://www.gnu.org/software/make/manual/html_node/Foreach-Function.html), [`wildcard` ↗](https://www.gnu.org/software/make/manual/html_node/Wildcard-Function.html), [splitting recipe lines ↗](https://www.gnu.org/software/make/manual/html_node/Splitting-Recipe-Lines.html) and [echoing ↗](https://www.gnu.org/software/make/manual/html_node/Echoing.html)
- [*It's About Time* ↗](https://github.com/hudson-trading/its-about-time) — the whole repository: slides, speaker notes, and benchmark results from four machines in `benchmarks/results/`
