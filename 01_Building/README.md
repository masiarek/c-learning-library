# Building

**One line:** Three programs stand between a `.c` file and something you can run — the compiler, the linker and `make` — and only the first two ever read your code; `make` decides which of them to run from file names and timestamps alone.

| Lesson | Level | What it teaches |
|---|---|---|
| [Makefiles: a build graph you write by hand](makefiles/README.md) | 101 → 201 | A rule, a TAB and a timestamp comparison are the whole of `make` — so a header nobody listed leaves a stale program and no error, until the compiler writes the dependency list for you |
| [Reading a real Makefile](reading_a_real_makefile/README.md) | 201 → 301 | A 31-line Makefile from a C++Now 2026 keynote that writes its own rules — `define`, `$(call)`, `$(eval)`, and the `$$` that survives both — read line by line, then built on Linux and refused by a Mac |

## Planned

Rough order, not a promise:

- **What `cc` actually runs** — the preprocessor, the compiler proper, the assembler and the linker as separate steps, and `cc -v` to watch it call them
- **The linker** — until then, the Rust library's [The linker ↗](https://masiarek.github.io/rust-learning-library/20_Compilers/the_linker/index.html) covers the stage no compiler owns
- **Static and shared libraries** — `ar` and `.a`, `.so` and `.dylib`, and why a Mac will not link `-static` at all
- **CMake** — a program that writes Makefiles, and why its errors arrive at two different times
