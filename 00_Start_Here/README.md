# Start here

**Level:** 101 · for anyone starting from zero

**One line:** C is a small language with a large amount of "it depends", and this library runs every example on two machines that disagree — so each page can say which answers are C's and which are your compiler's.

## What this library is for

Being able to build, read and debug C you did not write — a repository with a Makefile at the top, the benchmarks from a conference talk, the C view on a page in a sibling library — and to know, for anything a program prints, whether you are looking at the language or at your machine.

## How a lesson works

- **The page** — one idea, opening with the version that works, then the trap.
- **`examples/`** — the programs behind the page. A block marked *Verified output* was pasted in by a tool from a real run, and CI runs it again on Ubuntu and on macOS on every push.
- **`demo/`** — files meant to be copied: a Makefile and the sources it builds. A Makefile shown on a page is the demo file byte for byte, TABs included — which matters, because `make` refuses a recipe line indented with spaces.
- **A fence titled *Real output — …*** — one run on one named machine, kept because it shows a difference between machines. CI does not re-run it, and its title says where it came from.

## Running an example yourself

```bash
git clone https://github.com/masiarek/c-learning-library
cd c-learning-library/01_Building/makefiles/examples
bash makefiles_rebuild_sh.sh
python3 ../../../tools/run_examples.py --only makefiles_rebuild_sh
```

The first command prints what the page's verified block shows. The last one checks that output against the recorded answer key and tells you if your machine disagrees — which, in a C library, is sometimes the lesson.

## The chapters

| Chapter | What it covers |
|---|---|
| [01_Building](../01_Building/README.md) | The compiler, the linker and `make` — what turns `.c` files into a program, and what decides which of them to rebuild |
| [02_Decompiling](../02_Decompiling/README.md) | Ghidra's decompiler on a program you built — what it hands back, what was never in the file, and how to tell it |
| [04_Debugging](../04_Debugging/README.md) | lldb and gdb on a program you built, and what Ghidra's Debugger records of what they report |

## Planned

Rough order, not a promise:

- **Integers** — widths, promotions, signed overflow, and `<stdint.h>`
- **Strings are arrays** — the terminating NUL, `strlen` against `sizeof`, and the library functions that do not check a length
- **Pointers and memory** — `malloc` and `free`, lifetimes, and what AddressSanitizer catches
- **Undefined behaviour** — what the standard declines to define, and why the optimizer cares. The Rust library's [C and C++ chapter ↗](https://masiarek.github.io/rust-learning-library/31_C_and_Cpp/index.html) already runs nine of them
