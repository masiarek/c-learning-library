# Debugging

**One line:** A debugger reads the process a file became — the stack, the heap, and the zeroes the loader supplied — and Ghidra's Debugger is a recorder for one: lldb or gdb does the stopping, and Ghidra keeps the mapping back to the file you analyzed and a trace of every stop.

| Lesson | Level | What it teaches |
|---|---|---|
| [What the debugger records](what_the_debugger_records/README.md) | 201 | One program with four kinds of memory, stopped three times by lldb on a Mac and by gdb in a container: which of the four the file ever held, what a back end reports at a stop, and what Ghidra keeps of it that no back end does |

## Planned

Rough order, not a promise:

- **The trace, measured** — the lesson's `RecordBump.java` run inside the Debugger tool, so the rewind is a fence and not a quotation
- **Watchpoints** — a breakpoint on data rather than code, and what `counter++` looks like when the debugger stops on the write
- **The emulator** — Ghidra's own back end, which needs no process and no operating system at all
- **Core dumps** — a process that stopped without a debugger attached, opened afterwards
