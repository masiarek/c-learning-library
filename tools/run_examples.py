#!/usr/bin/env python3
"""Run every example, and hold its output to a recorded answer key.

This is the spine of the library. A lesson page never hand-types what a program
prints; it marks the spot and this tool fills it from a real run:

    <!-- output:makefiles_rebuild_sh -->
    <!-- /output -->

Inside the markers is generated, outside is yours. There is a second kind,
`source:`, which pastes the program itself — for the pages where the code *is*
the lesson and a hand-copied fence could quietly drift from the file CI runs.

Four kinds of example, told apart by extension
----------------------------------------------
    examples/<stem>.c     compiled with `cc -std=c17 -Wall -Wextra -pedantic` -- Apple
                          clang on a Mac, GCC on Linux, and the key must match both.
                          libc only, unless its opening comment's build command
                          says `$(pkg-config --cflags --libs MODULE...)`
    examples/<stem>.sh    run as `bash <stem>.sh` -- mostly driving `cc` and `make`
                          over a scratch copy of the lesson's demo/ folder
    examples/<stem>.py    stdlib-only Python, run as `python3 -I <stem>.py`
    examples/<stem>.rs    compiled with `rustc --edition 2024`, no cargo, no crates

Stems are unique repo-wide *across* extensions, because a Markdown block names a
bare stem with no path and no extension. The convention is a language suffix:
`makefiles_rebuild_sh`, `integer_width_c`.

Every example runs under one fixed environment — `LC_ALL=C`, `LANG=C`,
`PYTHONUTF8=1` — so the answer key does not depend on whoever ran it. A lesson
whose subject is the locale sets its own inside the script, on purpose and in
view. Output is captured as bytes and decoded as UTF-8; a byte that is not
valid UTF-8 is written into the key as `\\xNN`, which is deterministic, and is
usually the moment a shell example should have piped through `xxd` instead.

Four modes
----------
    python3 tools/run_examples.py             verify + refill the .md blocks
    python3 tools/run_examples.py --update    accept current output as the key
    python3 tools/run_examples.py --check     write nothing; fail on any drift  (CI)
    python3 tools/run_examples.py --only X    touch example X and nothing else

``--only`` narrows both the running and the refilling to the stems you name.
A full ``--update`` re-records *every* answer key and refills *every* page, which
in a checkout open in two sessions means adopting whatever a colleague's
half-finished example happens to print. It is never right in CI: a partial run
cannot see repo-wide drift, which is the whole job there.
"""

from __future__ import annotations

import argparse
import difflib
import functools
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
EDITION = "2024"

# extension -> (how the page labels the source fence, how the tool runs it)
LANGS = {".py": "python", ".rs": "rust", ".sh": "bash", ".c": "c"}

# A C example that needs a library beyond libc says so the way a reader is told:
# its opening comment carries the build command, and the tool asks pkg-config the
# same question that command does.
#
#     Build: cc -std=c17 ... icu_case_c.c $(pkg-config --cflags --libs icu-uc) -o icu_case_c
PKG_CONFIG = re.compile(r"\$\(pkg-config --cflags --libs (?P<modules>[\w.+\- ]+)\)")

# Homebrew installs some libraries keg-only -- not linked into its prefix -- so
# pkg-config cannot find them until PKG_CONFIG_PATH names the keg, which is what
# `brew info icu4c` tells a reader to set. pkg-config module -> Homebrew formula.
BREW_KEG_ONLY = {"icu-uc": "icu4c", "icu-i18n": "icu4c", "icu-io": "icu4c"}

LIBRARY_HINT = (
    "  macOS:          brew install icu4c pkgconf\n"
    "  Debian/Ubuntu:  sudo apt install libicu-dev pkg-config\n"
)

# <!-- output:stem -->  ...generated...  <!-- /output -->
# <!-- source:stem -->  ...generated...  <!-- /source -->
BLOCK = re.compile(
    r"(?P<open><!--\s*(?P<kind>output|source):(?P<stem>[A-Za-z0-9_\-]+)\s*-->)"
    r"(?P<body>.*?)"
    r"(?P<close><!--\s*/(?P=kind)\s*-->)",
    re.DOTALL,
)

SKIP_DIRS = {".git", "site", ".venv", "__pycache__", ".github"}

# A fenced code block, opened or closed. The pages that DOCUMENT this mechanism
# (README.md, CONTRIBUTING.md) show the markers inside a fence — those are
# documentation, not blocks to fill.
FENCE = re.compile(r"^[ \t]*(?P<f>`{3,}|~{3,})", re.MULTILINE)


def fenced_spans(text: str) -> list[tuple[int, int]]:
    """Character ranges covered by fenced code blocks."""
    spans: list[tuple[int, int]] = []
    open_at: int | None = None
    open_fence = ""
    for m in FENCE.finditer(text):
        fence = m.group("f")
        if open_at is None:
            open_at, open_fence = m.start(), fence
        elif fence[0] == open_fence[0] and len(fence) >= len(open_fence):
            spans.append((open_at, m.end()))
            open_at = None
    if open_at is not None:
        spans.append((open_at, len(text)))
    return spans


def walk(root: Path):
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for name in filenames:
            yield Path(dirpath) / name


def find_examples() -> dict[str, Path]:
    """Map stem -> path for every example under an examples/ folder. Stems are unique."""
    found: dict[str, Path] = {}
    for path in sorted(walk(REPO)):
        if path.suffix not in LANGS or path.parent.name != "examples":
            continue
        if path.stem in found:
            sys.exit(
                f"ERROR: duplicate example stem {path.stem!r}\n"
                f"  {found[path.stem].relative_to(REPO)}\n  {path.relative_to(REPO)}\n"
                "Stems are named bare in Markdown blocks, so they must be unique "
                "across languages too — add a _py / _rs / _sh / _c suffix."
            )
        found[path.stem] = path
    return found


@functools.cache
def keg_only_pkgconfig_dirs() -> tuple[str, ...]:
    """The lib/pkgconfig folders of the BREW_KEG_ONLY formulas installed here, if any."""
    if shutil.which("brew") is None:
        return ()
    dirs: list[str] = []
    for formula in sorted(set(BREW_KEG_ONLY.values())):
        done = subprocess.run(
            ["brew", "--prefix", "--installed", formula], capture_output=True, text=True
        )
        pc = Path(done.stdout.strip()) / "lib" / "pkgconfig"
        if done.returncode == 0 and pc.is_dir():
            dirs.append(str(pc))
    return tuple(dirs)


def fixed_env() -> dict[str, str]:
    """One environment for every run, so the key is the program's and not the machine's."""
    env = dict(os.environ)
    for k in list(env):
        if k.startswith("LC_") or k in {"LANG", "LANGUAGE"}:
            del env[k]
    env.update({"LC_ALL": "C", "LANG": "C", "PYTHONUTF8": "1", "PYTHONIOENCODING": "utf-8"})
    # After whatever PKG_CONFIG_PATH the caller set, so their own choice still wins.
    kegs = keg_only_pkgconfig_dirs()
    if kegs:
        own = [d for d in env.get("PKG_CONFIG_PATH", "").split(os.pathsep) if d]
        env["PKG_CONFIG_PATH"] = os.pathsep.join(own + [d for d in kegs if d not in own])
    return env


def library_flags(src: Path, env: dict[str, str]) -> list[str]:
    """Compiler and linker flags for the pkg-config modules a C example's opening comment names."""
    opening_comment = src.read_text(encoding="utf-8").split("*/", 1)[0]
    m = PKG_CONFIG.search(opening_comment)
    if not m:
        return []
    modules = m.group("modules").split()
    where = f"{src.relative_to(REPO)} builds against {' '.join(modules)}"
    if shutil.which("pkg-config") is None:
        sys.exit(f"ERROR: {where}, and pkg-config is not on PATH\n{LIBRARY_HINT}")
    done = subprocess.run(
        ["pkg-config", "--cflags", "--libs", *modules], capture_output=True, text=True, env=env
    )
    if done.returncode != 0:
        sys.exit(f"ERROR: {where}, which pkg-config cannot find\n{done.stderr}{LIBRARY_HINT}")
    return done.stdout.split()


def _decode(raw: bytes) -> str:
    return raw.decode("utf-8", errors="backslashreplace")


def run_example(src: Path, workdir: Path) -> str:
    """Run (or compile and run) one example; return its stdout. Exits on failure.

    Run from the example's own folder so a page can tell the reader to `cd` there
    and type the same command. Python gets -I so a stray module beside it or on
    PYTHONPATH cannot change what the page claims.
    """
    env = fixed_env()
    if src.suffix in (".rs", ".c"):
        binary = workdir / src.stem
        if src.suffix == ".rs":
            build_cmd = ["rustc", "--edition", EDITION, str(src), "-o", str(binary)]
        else:
            build_cmd = [
                "cc", "-std=c17", "-Wall", "-Wextra", "-pedantic",
                str(src), *library_flags(src, env), "-o", str(binary),
            ]
        build = subprocess.run(build_cmd, capture_output=True, text=True)
        if build.returncode != 0:
            sys.exit(f"ERROR: {src.relative_to(REPO)} failed to compile\n{build.stderr}")
        if build.stderr.strip():
            print(f"  note: {src.relative_to(REPO)} compiled with warnings:\n{build.stderr}")
        cmd = [str(binary)]
    elif src.suffix == ".py":
        cmd = [sys.executable, "-I", src.name]
    else:
        cmd = ["bash", src.name]

    proc = subprocess.run(cmd, cwd=src.parent, capture_output=True, env=env, timeout=60)
    if proc.returncode != 0:
        sys.exit(
            f"ERROR: {src.relative_to(REPO)} exited {proc.returncode}\n{_decode(proc.stderr)}"
        )
    if proc.stderr.strip():
        print(f"  note: {src.relative_to(REPO)} wrote to stderr:\n{_decode(proc.stderr)}")
    return _decode(proc.stdout)


def rendered_block(kind: str, src: Path, output: str, page: Path) -> str:
    """The generated body that goes between the markers on `page`."""
    href = os.path.relpath(src, page.parent)
    if kind == "source":
        body = src.read_text(encoding="utf-8").strip("\n")
        return (
            f"\n*[`{src.name}`]({href}) in full — pasted here by "
            f"`tools/run_examples.py` from the file CI runs.*\n\n"
            f"```{LANGS[src.suffix]}\n{body}\n```\n"
        )
    return (
        f"\n*Verified output of [`{src.name}`]({href}) — regenerated by "
        f"`tools/run_examples.py`, never hand-typed.*\n\n"
        f"```text\n{output.strip(chr(10))}\n```\n"
    )


def fill_pages(
    outputs: dict[str, str],
    sources: dict[str, Path],
    write: bool,
    problems: list[str],
    only: set[str] | None = None,
) -> list[str]:
    """Refill every generated block on every Markdown page. Returns drift.

    A block naming a stem that no longer exists is recorded in `problems` and left
    untouched rather than exiting on the spot — dying on the first one would leave
    every other page unfilled.

    With `only` set, a block naming any other stem is left exactly as it is. The
    selection is checked before the kind is, so a run scoped to one stem cannot
    rewrite either half of somebody else's page.
    """
    drift: list[str] = []
    for page in sorted(walk(REPO)):
        if page.suffix != ".md":
            continue
        text = page.read_text(encoding="utf-8")
        if "<!-- output:" not in text and "<!-- source:" not in text:
            continue
        skip = fenced_spans(text)

        def replace(m: re.Match) -> str:
            if any(lo <= m.start() < hi for lo, hi in skip):
                return m.group(0)
            stem, kind = m.group("stem"), m.group("kind")
            if only is not None and stem not in only:
                return m.group(0)
            known = sources if kind == "source" else outputs
            if stem not in known:
                problems.append(
                    f"{page.relative_to(REPO)}: asks for {kind} block {stem!r}, "
                    "but no examples/ file has that stem"
                )
                return m.group(0)
            return (
                m.group("open")
                + rendered_block(kind, sources[stem], outputs.get(stem, ""), page)
                + m.group("close")
            )

        new = BLOCK.sub(replace, text)
        if new != text:
            drift.append(str(page.relative_to(REPO)))
            if write:
                page.write_text(new, encoding="utf-8")
    return drift


def examples_under(token: Path, examples: dict[str, Path]) -> set[str]:
    """Every example stem inside `token`, if `token` names a directory."""
    for base in (token, REPO / token):
        try:
            if not base.is_dir():
                continue
            resolved = base.resolve()
        except OSError:
            continue
        held = {s for s, p in examples.items() if resolved in p.parents}
        if held:
            return held
    return set()


def resolve_selection(raw: list[str], examples: dict[str, Path]) -> set[str]:
    """Turn `--only` values into stems: a bare stem, a path to the file, or a folder.

    A token that names nothing is an error rather than an empty selection: a typo
    that records nothing looks exactly like a successful run.
    """
    wanted: set[str] = set()
    unknown: list[str] = []
    for token in (t.strip() for value in raw for t in value.split(",")):
        if not token:
            continue
        as_path = Path(token)
        held = examples_under(as_path, examples)
        if held:
            wanted |= held
            continue
        for candidate in (token, as_path.stem, as_path.name):
            if candidate in examples:
                wanted.add(candidate)
                break
        else:
            unknown.append(token)
    if unknown:
        sys.exit(
            f"ERROR: --only names no such example: {', '.join(unknown)}\n"
            f"Known stems: {', '.join(sorted(examples))}"
        )
    return wanted


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--update", action="store_true", help="record current output as the answer key")
    ap.add_argument("--check", action="store_true", help="write nothing; fail on drift (CI)")
    ap.add_argument(
        "--only",
        action="append",
        metavar="STEM[,STEM…]",
        help="restrict to these example stems (a path or a folder works too); "
        "repeat the flag or comma-separate. Not for CI.",
    )
    args = ap.parse_args()

    examples = find_examples()
    if not examples:
        print("No examples found (looked for *.py / *.rs / *.sh / *.c under any examples/ folder).")
        return 0

    selected = resolve_selection(args.only, examples) if args.only else None

    outputs: dict[str, str] = {}
    failures: list[str] = []

    with tempfile.TemporaryDirectory(prefix="c-examples-") as tmp:
        workdir = Path(tmp)
        for stem, src in sorted(examples.items()):
            key = src.with_suffix(".out")

            # Outside the selection: not ours. Not run, and deliberately not even
            # read — an output in hand is one `fill_pages` could write into someone
            # else's page.
            if selected is not None and stem not in selected:
                continue

            actual = run_example(src, workdir)
            outputs[stem] = actual

            if args.update:
                key.write_text(actual, encoding="utf-8")
                print(f"  recorded  {key.relative_to(REPO)}")
                continue
            if not key.exists():
                failures.append(f"{src.relative_to(REPO)}: no answer key — run with --update")
                continue
            recorded = key.read_text(encoding="utf-8")
            if recorded != actual:
                failures.append(f"{src.relative_to(REPO)}: output differs from {key.name}")
                # Print the diff here, not just the verdict: on a CI runner the
                # difference is usually a BSD/GNU tool quirk, and the log is the
                # only place anyone can see which line it was.
                print(f"  DIFF      {src.relative_to(REPO)} (recorded -> actual)")
                for line in difflib.unified_diff(
                    recorded.splitlines(), actual.splitlines(),
                    fromfile=key.name, tofile="actual", lineterm="", n=1,
                ):
                    print("    " + line)
            else:
                print(f"  ok        {src.relative_to(REPO)}")

    drift = fill_pages(outputs, examples, write=not args.check, problems=failures, only=selected)

    if args.check and drift:
        failures.append(
            "Markdown output blocks are stale: " + ", ".join(drift)
            + " — run tools/run_examples.py"
        )
    elif drift:
        for page in drift:
            print(f"  filled    {page}")

    if failures:
        print("\nFAILED:")
        for f in failures:
            print(f"  - {f}")
        return 1

    if selected is not None:
        print(
            f"\n{len(selected)} of {len(examples)} example(s) verified. --only was in "
            f"effect: the other {len(examples) - len(selected)} were left untouched. "
            "Do a full run before committing."
        )
        return 0

    print(f"\n{len(examples)} example(s) verified against their recorded output.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
