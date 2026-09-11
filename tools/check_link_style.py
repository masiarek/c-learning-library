#!/usr/bin/env python3
"""Every link that leaves the library says so.

House rule (CONTRIBUTING.md → Linking): an **external** link — one whose href is
an absolute `http(s)://` URL — ends its label with ` ↗`. An **internal** link —
anything relative, which is to say anything still inside this library — never
carries one. A reader scanning a table of blue text can then tell, without
hovering, which links stay home and which open the web.

The marker lives in the Markdown, not in the site's CSS, because this repo is
read on three surfaces: GitHub, the published MkDocs site, and a plain local
Markdown viewer. A stylesheet only reaches one of them.

    python3 tools/check_link_style.py          # report offenders, exit 1 if any
    python3 tools/check_link_style.py --fix    # add/remove the marker in place
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
SKIP_DIRS = {"site", ".venv", ".git", "__pycache__", "target"}
MARK = "↗"

FENCE = re.compile(r"^\s*(```|~~~)")
# [label](href) or ![label](href), with an optional "title" — one line at a time,
# so a fence can be tracked and a code block never rewritten.
LINK = re.compile(r'(!?)\[([^\]\[]*)\]\((\S+?)(\s+"[^"]*")?\)')


def pages() -> list[Path]:
    return [
        p
        for p in sorted(REPO.rglob("*.md"))
        if not any(part in SKIP_DIRS for part in p.relative_to(REPO).parts)
    ]


def fix_line(line: str) -> tuple[str, int, int]:
    """Return (rewritten line, markers added, markers removed)."""
    added = removed = 0

    def repl(m: re.Match[str]) -> str:
        nonlocal added, removed
        bang, label, href, title = m.group(1), m.group(2), m.group(3), m.group(4) or ""
        if bang:  # an image is not a link a reader follows
            return m.group(0)
        external = href.startswith(("http://", "https://"))
        stripped = label.rstrip()
        has = stripped.endswith(MARK)
        if external and not has:
            added += 1
            label = stripped + " " + MARK
        elif not external and has:
            removed += 1
            label = stripped[: -len(MARK)].rstrip()
        return f"[{label}]({href}{title})"

    return LINK.sub(repl, line), added, removed


def scan(fix: bool) -> int:
    offenders: list[str] = []
    added = removed = 0
    touched: list[Path] = []

    for page in pages():
        text = page.read_text(encoding="utf-8")
        out: list[str] = []
        in_fence = False
        changed = False
        for n, line in enumerate(text.splitlines(keepends=False), 1):
            if FENCE.match(line):
                in_fence = not in_fence
                out.append(line)
                continue
            if in_fence:
                out.append(line)
                continue
            new, a, r = fix_line(line)
            if new != line:
                changed = True
                added += a
                removed += r
                rel = page.relative_to(REPO)
                offenders.append(f"{rel}:{n}: {line.strip()[:110]}")
            out.append(new)
        if changed and fix:
            trailing = "\n" if text.endswith("\n") else ""
            page.write_text("\n".join(out) + trailing, encoding="utf-8")
            touched.append(page.relative_to(REPO))

    if not offenders:
        print("link style: every external link carries its ↗, no internal link does.")
        return 0

    if fix:
        print(f"link style: {added} marker(s) added, {removed} removed, in {len(touched)} file(s).")
        return 0

    print(f"link style: {len(offenders)} line(s) to fix ({added} missing ↗, {removed} stray ↗).")
    print("Run: python3 tools/check_link_style.py --fix\n")
    for line in offenders[:40]:
        print("  " + line)
    if len(offenders) > 40:
        print(f"  … and {len(offenders) - 40} more")
    return 1


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--fix", action="store_true", help="rewrite the pages in place")
    return scan(ap.parse_args().fix)


if __name__ == "__main__":
    sys.exit(main())
