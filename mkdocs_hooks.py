"""Build-time fixes that would otherwise cost a pinned plugin dependency.

Three jobs, all about the sidebar:

1. **Clean chapter labels.** MkDocs derives a section label from the folder name
   on disk, so `01_Building/` reads as "01 Building". The numeric prefix exists
   to set reading order in a file listing; it should not be visible in the nav.
   Only *prefixed* folders are relabelled from their name — a lesson folder takes
   its README's own H1 (job 3).

2. **Order the sections.** `NAV_ORDER` states the intended reading order per
   folder, keyed by folder path, listing children by their on-disk name.

3. **Label lessons from their H1.** Left alone, MkDocs titles a lesson folder
   from its name, so `reading_a_real_makefile` reads "Reading a real makefile" —
   and `mkdocs build --strict` passes either way. A lesson folder takes its
   README's H1 instead, backticks dropped. `LABEL_OVERRIDES` holds the exceptions:
   an H1 with a subtitle the sidebar has no room for.

One check rides along at the bottom of the file, unrelated to the sidebar:
every TAB inside a fence has to reach the page's HTML -- see the comment
above `on_page_content`.

Why order here rather than by renaming files: a filename is a permanent URL.
Renumbering `03_` to `04_` to insert a lesson would move every page after it and
break any link anyone saved. Ordering is presentation, so it belongs in the
presentation layer. Unlisted pages keep their alphabetical slot at the bottom, so
adding a page needs no edit here.

One structural note that is easy to get wrong: the top-level object MkDocs hands
`on_nav` is a `Navigation`, whose children live on `.items`. Only `Section` has
`.children`. A hook that reaches for `.children` at the top level silently does
nothing at all — the build still succeeds, and the sidebar is simply never
touched.
"""

from __future__ import annotations

import logging
import re

# A child of the "mkdocs" logger, so `mkdocs build --strict` counts its warnings.
log = logging.getLogger("mkdocs.plugins.mkdocs_hooks")

PREFIX = re.compile(r"^(\d+)[_-]")

# Words the naive title-caser gets wrong.
FIXUPS = {
    "Vs": "vs",
    "And": "and",
    "Or": "or",
    "The": "the",
    "To": "to",
    "A": "a",
    "In": "in",
    "Of": "of",
}

# Lesson folders whose sidebar label is deliberately not their H1. Every other
# lesson folder is labelled with its README's H1, backticks dropped -- see
# `_visit`. Keyed by on-disk folder name -- a folder name is a permanent URL, so
# the fix belongs here rather than in a rename. Like NAV_ORDER, an entry naming a
# folder that no longer exists is a silent no-op, which tools/check_nav_chain.py
# reports.
LABEL_OVERRIDES: dict[str, str] = {
    # The H1 is "Makefiles: a build graph you write by hand".
    "makefiles": "Makefiles",
}

# Reading order per folder path. Children named by on-disk name; anything not
# listed sorts alphabetically after the listed ones.
NAV_ORDER: dict[str, list[str]] = {
    "": [
        "index.md",
        "00_Start_Here",
        "01_Building",
        "02_Decompiling",
        "04_Debugging",
    ],
    # The graph you write by hand first; then a real one that writes its own rules.
    "01_Building": [
        "README.md",
        "makefiles",
        "reading_a_real_makefile",
    ],
    "02_Decompiling": [
        "README.md",
        "what_the_decompiler_recovers",
    ],
    "04_Debugging": [
        "README.md",
        "what_the_debugger_records",
    ],
}


def _label(name: str) -> str:
    """Folder name on disk -> sidebar label."""
    words = PREFIX.sub("", name).replace("_", " ").replace("-", " ").split()
    out = [FIXUPS.get(w.capitalize(), w.capitalize()) for w in words]
    if out:
        out[0] = out[0][0].upper() + out[0][1:]
    return " ".join(out)


def _is_section(item) -> bool:
    return getattr(item, "children", None) is not None


def _first_src(item) -> str:
    """Source path of `item`, or of the first page anywhere beneath it."""
    page_file = getattr(item, "file", None)
    if page_file is not None:
        return page_file.src_uri
    for child in getattr(item, "children", None) or []:
        found = _first_src(child)
        if found:
            return found
    return ""


def _on_disk_name(item, depth: int) -> str:
    """The name NAV_ORDER lists this child by: a filename, or a folder segment."""
    src = _first_src(item)
    if not src:
        return (getattr(item, "title", "") or "").lower()
    parts = src.split("/")
    if not _is_section(item):
        return parts[-1]
    return parts[depth] if depth < len(parts) - 1 else parts[-1]


def _order_key(path: str, name: str) -> tuple[int, str]:
    listed = NAV_ORDER.get(path, [])
    if name in listed:
        return (listed.index(name), "")
    return (len(listed), name.lower())


def _readme_h1(section) -> str:
    """The H1 of a section's own README.md, read from disk ("" if it has none).

    Read from disk because MkDocs fills in a page's title only when it renders
    the page, long after `on_nav`. Backticks are dropped: the sidebar prints
    them as literal characters.
    """
    for child in section.children:
        page_file = getattr(child, "file", None)
        if page_file is None or page_file.src_uri.rsplit("/", 1)[-1] != "README.md":
            continue
        with open(page_file.abs_src_path, encoding="utf-8") as fh:
            for line in fh:
                if line.startswith("# "):
                    return line[2:].strip().replace("`", "")
    return ""


def _visit(items: list, path: str, depth: int) -> None:
    for child in items:
        if not _is_section(child):
            continue
        name = _on_disk_name(child, depth)
        # A numbered chapter folder is relabelled from its name. A lesson folder
        # takes its README's H1, which is authored prose, unless LABEL_OVERRIDES
        # names a label for it. Title-casing the folder name instead would fight
        # the page it points at ("Significant Figures").
        if name in LABEL_OVERRIDES:
            child.title = LABEL_OVERRIDES[name]
        elif PREFIX.match(name):
            child.title = _label(name)
        else:
            child.title = _readme_h1(child) or child.title

    items.sort(key=lambda c: _order_key(path, _on_disk_name(c, depth)))

    for child in items:
        if not _is_section(child):
            continue
        name = _on_disk_name(child, depth)
        _visit(child.children, f"{path}/{name}".lstrip("/"), depth + 1)


def _pages_in_nav_order(items: list) -> list:
    """Every page under `items`, depth-first, in the order the sidebar shows."""
    out = []
    for item in items:
        if item.is_page:
            out.append(item)
        elif item.is_section:
            out.extend(_pages_in_nav_order(item.children))
    return out


def on_nav(nav, config, files):
    """Relabel numbered chapters, apply NAV_ORDER, and re-chain prev/next."""
    _visit(nav.items, "", 0)

    # Sorting nav.items fixes the sidebar and nothing else. MkDocs computes
    # every page's previous_page/next_page inside get_navigation(), which runs
    # BEFORE this hook -- so without the re-chain below, the arrows at the foot
    # of a lesson walk the reader alphabetically while the sidebar beside them
    # reads in order. That was live on all 13 chapters until 2026-09-07: the
    # published 11_Tools/index.html said rel="next" -> awk where NAV_ORDER
    # says grep. For a library with a reading order, the arrow IS the order.
    #
    # This repeats mkdocs.structure.nav._add_previous_and_next_links rather
    # than calling it, because that function is private and this is four lines;
    # a pin bump should not be able to break the nav silently.
    ordered = _pages_in_nav_order(nav.items)
    # If MkDocs ever grows a nav item type the walk above does not descend
    # into, this is where it shows -- loudly, at build time, rather than as a
    # handful of pages quietly dropping out of the prev/next chain.
    # Compared by source path, not by identity: MkDocs' Page defines __eq__
    # without __hash__, so a Page cannot go in a set.
    walked = {page.file.src_uri for page in ordered}
    known = {page.file.src_uri for page in nav.pages}
    assert walked == known, (
        "_pages_in_nav_order is out of step with mkdocs.structure.nav: "
        f"missed {sorted(known - walked)}, invented {sorted(walked - known)}"
    )
    for i, page in enumerate(ordered):
        page.previous_page = ordered[i - 1] if i else None
        page.next_page = ordered[i + 1] if i + 1 < len(ordered) else None
    nav.pages[:] = ordered

    return nav

# ---------------------------------------------------------------------------
# Fenced TABs. Python-Markdown expands every TAB in a page to spaces --
# `expandtabs(4)`, in its NormalizeWhitespace preprocessor -- before any fence
# is parsed, so a site built with its defaults serves no TAB byte at all, and a
# Makefile copied off 01_Building/makefiles would fail with `missing
# separator`. The fix is `preserve_tabs: true` on pymdownx.superfences in
# mkdocs.yml, which lifts fences out ahead of that pass. Upstream calls the
# option experimental, and losing it would break nothing a build reports, so
# this is the check: a page whose fences hold N TABs in its Markdown must hold
# at least N in its HTML, or the build warns and `--strict` fails.
#
# The code below is the Rust library's (mkdocs_hooks.py, commit b261f8c, which
# found the option and measured it), copied verbatim. Only closed fences at the
# left margin are counted. "At least N" rather than N because the homepage
# inlines README.md through pymdownx.snippets, and an inlined fence's TABs are
# in the HTML without being in `page.markdown`.
# ---------------------------------------------------------------------------

FENCE_OPEN = re.compile(r"`{3,}|~{3,}")


def _fenced_tabs(markdown: str) -> int:
    """TABs inside the closed fences that start at the left margin."""
    total = pending = 0
    fence = None
    for line in markdown.split("\n"):
        if fence is None:
            m = FENCE_OPEN.match(line)
            if m:
                fence, pending = m.group(), 0
        elif re.fullmatch(rf"{fence[0]}{{{len(fence)},}}\s*", line):
            total += pending
            fence = None
        else:
            pending += line.count("\t")
    return total


def on_page_content(html, page, config, files):
    """Warn when a page's HTML holds fewer TABs than its fences did."""
    want = _fenced_tabs(page.markdown)
    got = html.count("\t")
    if got < want:
        log.warning(
            "Fenced TABs lost: %s has %d inside its fences and %d in its "
            "HTML. Is `preserve_tabs: true` still set on pymdownx.superfences?",
            page.file.src_uri,
            want,
            got,
        )
    return html
