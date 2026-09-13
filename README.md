# C — Learning Library

<!-- --8<-- [start:hero] -->

A learning library about **C — how a program is written, built and run** — made the same way as its siblings [rust-learning-library ↗](https://github.com/masiarek/rust-learning-library) and [encodings-learning-library ↗](https://github.com/masiarek/encodings-learning-library): **one idea per page, and every claim backed by a program that actually runs.**

No page here hand-types what a program prints. Each lesson links real example files — C, and bash scripts that drive `cc` and `make` over a demo folder — and a tool runs them, checks the output against a recorded answer key, and pastes that verified output into the page. CI fails if any of them drift apart, and it runs them on two machines chosen to disagree: **Ubuntu, with GCC on x86-64**, and **macOS, with Apple clang on arm64**.

📖 **Read it as a site:** <https://masiarek.github.io/c-learning-library/>

<!-- --8<-- [end:hero] -->

<!-- --8<-- [start:below-hero] -->

## Why two machines

C leaves a great deal to "the implementation" — whether a plain `char` is signed, how wide a `long` is, what the compiler warns about — and the `cc` and `make` on your `PATH` are programs with versions of their own. A page that shows one machine's answer as if it were C's is wrong on the other machine, and nothing on the page says so.

So every answer key here is something **both** machines print. What they disagree about goes in a fence that names the machine and the tool versions — measured, labelled, and not claimed any further. The first chapter already has three of those: macOS still ships GNU Make 3.81 from 2006, which compares timestamps to the whole second; clang's `-Wconversion` is stricter than GCC's in C++; and `-static` links on Linux and does not link on a Mac at all.

## Start here

[00_Start_Here](00_Start_Here/README.md) — what the library is for, how a lesson is put together, and how to run any example yourself.

## Chapters

| Chapter | The question it settles |
|---|---|
| [01_Building](01_Building/README.md) | What turns `.c` files into a program — and what decides which of them to compile again? |
| [02_Decompiling](02_Decompiling/README.md) | What does a compiled program still say about the C it came from — and what has to be told to it? |

More are planned; [00_Start_Here](00_Start_Here/README.md) has the list.

## What you need

A C compiler and `make`. On a Mac: `xcode-select --install`. On Debian or Ubuntu: `sudo apt install build-essential`. Nothing else — no libraries, no package manager, no IDE. The [Decompiling](02_Decompiling/README.md) chapter also drives Ghidra — `brew install ghidra` on a Mac — but every Ghidra result is on the page, so you need it only to reproduce them.

## See also

- [rust-learning-library ↗](https://masiarek.github.io/rust-learning-library/) — its [C and C++ chapter ↗](https://masiarek.github.io/rust-learning-library/31_C_and_Cpp/index.html) runs nine classic C bugs and shows the Rust that refuses to compile each one.
- [encodings-learning-library ↗](https://masiarek.github.io/encodings-learning-library/) — bytes, characters and UTF-8, with a C view on several pages.
- [CONTRIBUTING.md](CONTRIBUTING.md) — house rules, for whoever is about to write a page.

<!-- --8<-- [end:below-hero] -->
