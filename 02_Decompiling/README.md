# Decompiling

**One line:** A compiled program still computes what its source said, so a decompiler can hand back C that does the same — but the names, the types and the loops the optimizer removed are not in the file, and reading a binary is mostly the work of putting them back.

| Lesson | Level | What it teaches |
|---|---|---|
| [What the decompiler recovers](what_the_decompiler_recovers/README.md) | 201 | Four small functions through Ghidra's headless decompiler: an expression rebuilt from six instructions, a struct that comes back as `param_1[1]` until you declare it, and a loop that comes back as a formula because the optimizer got there first |
| [Where a function starts](function_start_patterns/README.md) | 301 | How Ghidra finds functions in a stripped file: the four bytes clang puts at the start of every one, the Function Bit Patterns Explorer that discovers such bytes and turns them into a pattern file, and the two tables — one per executable format — that make the search unnecessary until a linker flag removes them |

## Planned

Rough order, not a promise:

- **What `-g` puts back** — DWARF: the names and types the decompiler otherwise asks you for, which Ghidra reads on import when they are there, and why on a Mac they are in a `.dSYM` and not in the executable
- **Reading the listing** — the assembly the decompiler started from, and the calling convention that tells it which registers hold the parameters
- **The Debugger** — Ghidra is not a debugger but drives one (LLDB on a Mac, GDB on Linux) and records what it sees into a trace that can be rewound; large enough that it may become a chapter of its own
