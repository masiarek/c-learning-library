# Conventions

House rules for writing a page here. Readers browsing lessons do not need this file; it is for whoever is about to add one.

## The rule that comes before the others

**A page never claims something a program has not printed** — and in a C library, *printed* needs a second clause: **on both machines.** C leaves a great deal to the implementation, and CI compiles every example with two compilers, for two CPUs, and drives two versions of `make`. An answer key is only what all of them agree on. Anything only one machine can say goes in a fence whose title names the machine.

## The shape of a lesson

```
01_Building/
  makefiles/
    README.md                        the lesson
    demo/                            files a reader takes as-is: a Makefile, the sources it builds
    examples/
      makefiles_rebuild_sh.sh        a script that drives cc and make over a copy of demo/
      makefiles_rebuild_sh.out       its recorded output (generated — do not hand-edit)
```

One idea per folder. The folder name is the idea, in `lower_snake_case`, and it becomes a permanent URL — so name it for what it teaches, not for where it sits in the reading order. A C example is `examples/<stem>_c.c` and a script is `examples/<stem>_sh.sh`: a page names an example by its bare stem, so stems must be unique across languages, and the suffix is how.

**`demo/` holds the files a reader copies**, and a fence on the page is a picture of one: copy a Makefile into a fence with a command, never by retyping it, and copy it again when the file changes. The site keeps the TABs inside fences — SuperFences' `preserve_tabs`, because Python-Markdown would otherwise expand every TAB to spaces and a copied Makefile would fail with `missing separator` — and `mkdocs_hooks.py` fails the strict build if a page's HTML ever holds fewer TABs than its fences. Scripts never build inside `demo/` — they copy it to a `mktemp -d` and work there, so the lesson folder stays clean.

## The page

Open with the title, a `**Level:**` line (`101` / `201` / `301` / `reference`, then `·`, then who it is for) and a `**One line:**` that states the claim rather than the topic. Lead with the version that works; the trap comes after. Do not hard-wrap paragraphs — one paragraph, one line.

## Output is generated, never typed

Mark the spot and let the tool fill it:

```markdown
<!-- output:makefiles_rebuild_sh -->
<!-- /output -->
```

`tools/run_examples.py` runs the example and pastes what it actually printed, with a provenance line above the fence. Inside the markers is generated; outside is yours. A second kind, `<!-- source:stem -->`, pastes the program itself — use it when the code *is* the lesson.

```bash
python3 tools/run_examples.py                      # verify + refill
python3 tools/run_examples.py --update --only X    # record X's output as its answer key
python3 tools/run_examples.py --check              # write nothing, fail on drift (CI)
python3 tools/check_all.py                         # every gate CI runs, honest exit status
python3 tools/check_all.py --committed             # the same gates on git archive HEAD
```

**Always pass `--only` with `--update`**, and read what it recorded before committing: `--update` accepts whatever the program printed, so it will happily enshrine a bug.

## The programs

**C: `cc -std=c17 -Wall -Wextra -pedantic`, libc only.** One file per example, compiled and run from its own folder. A warning is reported as a note, not a failure; a lesson about a warning shows it in a labelled fence.

**Shell: `bash`, from the example's folder, under `LC_ALL=C`.** Most scripts here drive `cc` and `make` over a scratch copy of the lesson's `demo/`. Print each command before running it — the `say` helper in the existing scripts does that — so a verified block reads like a terminal.

**Deterministic, on both machines.** No clocks, timings, random numbers or addresses (`%p` changes from run to run). No value the ABI chooses — `sizeof(long)`, whether a plain `char` is signed. And no message a tool words differently on the two machines: ask for an exit status instead (`make -q`), and put the message in a labelled fence.

**Wait a second before an edit** that a script then asks `make` about. macOS's `make` compares whole seconds, so an edit in the same second as the build is invisible to it — one of the rows below.

**Never record undefined behaviour.** A program whose output the C standard does not define has no answer key, however reproducible it looks on one machine. Show its output in a fence labelled with the compiler, flags and platform that produced it — the convention of the Rust library's [C and C++ chapter ↗](https://masiarek.github.io/rust-learning-library/31_C_and_Cpp/index.html).

## Two compilers, two CPUs, two makes

CI runs every example on `ubuntu-latest` and `macos-latest`. Measured differences so far — add a row when you find one, and say where you measured it:

| | Ubuntu 24.04 runner | macOS runner |
|---|---|---|
| `cc` | GCC 13 | Apple clang |
| CPU | x86-64 | arm64 |
| `make` | GNU Make 4.3 | GNU Make 3.81, from 2006 |
| `make`'s own messages | `make: 'hello' is up to date.` | `` make: `hello' is up to date. `` |
| a recipe that failed | `make: *** [Makefile:11: greet.o] Error 1` | `make: *** [greet.o] Error 1` |
| a built-in recipe that failed | `make: *** [<builtin>: hello] Error 1` | `make: *** [hello] Error 1` |
| timestamps `make` compares | to a fraction of a second | to the whole second |
| `.RECIPEPREFIX` | honoured | ignored |
| `CXX` if unset | `g++` | `c++` |
| `-static` | links | `ld: library 'crt0.o' not found` |
| `-Wconversion` in C++ | leaves out `-Wsign-conversion` | includes it |

Measured 2026-09-11. The Linux column in Docker — `ubuntu:24.04` with `make`, `gcc` and `g++` from apt, and Debian's `gcc:14` image (GNU Make 4.4.1, GCC 14), which agreed on every row either was asked; `-static` and `-Wconversion` were measured in `gcc:14`. The macOS column on an x86-64 Mac (macOS 26, Apple clang 21); the runner is arm64, so a row that turns out to depend on the CPU gets a note when CI finds it.

## Bridges

Every lesson ends with *If you are coming from another language*: Rust (link the Rust library's page when it has one — it often does), Python, and ABAP. The ABAP half is prose and says so: *(Not machine-checked — CI cannot run ABAP.)*

## Links

- Link a folder by naming its `README.md` — `[label](some_folder/README.md)`, never `[label](some_folder/)`.
- A repo path in backticks should be a link, not bare code text: backticks in the label, a real relative path in the href.
- **A link that leaves the library ends its label with ` ↗`**; an internal link never does. `python3 tools/check_link_style.py --fix` adds and removes them; CI runs it without `--fix`.
- Where a sibling library already teaches something, link to it and do not repeat it — the Rust library's C and C++ chapter, the encodings library's C views.

## Nav order

A new lesson folder gets a row in `NAV_ORDER` in [`mkdocs_hooks.py` ↗](https://github.com/masiarek/c-learning-library/blob/master/mkdocs_hooks.py), and an entry in `LABEL_OVERRIDES` if MkDocs would title-case its name wrongly. `tools/check_nav_chain.py` fails on a row naming a folder that does not exist, so commit the folder and its row together.

## Stubs

A **stub** is a lesson page with no example behind it yet: an H1, a `**Level:**`, the notice, a `**One line:**`, and the questions the finished page has to answer. Every stub carries this notice directly under its `**Level:**` line:

```markdown
> **Stub — an outline, not a lesson.** There is no runnable example behind this page yet, so nothing on it has been through [the check that backs every other claim in this library](../../CONTRIBUTING.md). The bullets below are the questions the finished page has to answer.
```

A stub must not have an `<!-- output: -->` block — there is nothing to fill it from. It graduates by gaining an `examples/` program and losing the notice.
