#!/usr/bin/env python3
"""The sidebar and the footer arrows must tell the same story.

`NAV_ORDER` in `mkdocs_hooks.py` states the reading order and `on_nav` applies
it. Sorting `nav.items` fixes the **sidebar**. It does not, on its own, fix the
two arrows at the foot of the page: MkDocs sets every page's `previous_page` /
`next_page` inside `get_navigation()`, which runs *before* any hook, so a hook
that only re-sorts leaves the arrows walking the default alphabetical order.

That shipped on all thirteen chapters until 2026-09-07 -- the published
`11_Tools` page offered *awk* as the page after it while the sidebar beside it
read *grep* first. Nothing caught it because each half is internally
consistent, `--strict` has no opinion about a hook that reorders a nav without
re-linking it, and only a reader who already knew what came next could tell.

This gate checks three things, and walks the nav itself rather than importing
the hook's helper, so the fix is not checked with the code under test:

1. **The forward chain follows the sidebar.** `next_page` from the first page
   must visit exactly the pages a depth-first walk of the sidebar visits, in
   that order.
2. **`previous_page` mirrors it.** The hook sets both in one loop today, so
   they cannot diverge now -- but a half-rebuilt chain is precisely the
   regression this gate exists to catch, so it is asserted rather than assumed.
3. **`nav.pages` matches.** `on_nav` promises to rewrite it; templates and
   plugins read it.

Plus a fourth, older hazard that `mkdocs_hooks.py` flags in its own comments
twice: **an entry naming something that no longer exists is a silent no-op.**
`_order_key` never matches a stale name and `LABEL_OVERRIDES` never fires for a
renamed folder, so the page quietly drops to the alphabetical tail with nothing
printed. (That check came from a parallel session that reached the same bug.)

    python3 tools/check_nav_chain.py
    python3 tools/check_nav_chain.py --selftest   # prove it still bites

Needs the docs group: run under `uv run --group docs`, which is how
`check_all.py` and CI invoke it.
"""

from __future__ import annotations

import pathlib
import sys

REPO = pathlib.Path(__file__).resolve().parent.parent


def walk(items: list) -> list:
    """Pages under `items`, depth-first -- the order the sidebar renders."""
    out = []
    for item in items:
        if item.is_page:
            out.append(item)
        elif item.is_section:
            out.extend(walk(item.children))
    return out


def uri(page) -> str:
    """Compare pages by source path: Page defines __eq__ but not __hash__."""
    return page.file.src_uri


def build_nav():
    from mkdocs.config import load_config
    from mkdocs.structure.files import get_files
    from mkdocs.structure.nav import get_navigation

    config = load_config(str(REPO / "mkdocs.yml"))
    config.plugins.on_startup(command="build", dirty=False)
    files = config.plugins.on_files(get_files(config), config=config)
    nav = get_navigation(files, config)
    return config.plugins.on_nav(nav, config=config, files=files)


def follow(start, attr: str) -> list:
    """Follow `attr` from `start` until it ends or repeats."""
    out, seen, page = [], set(), start
    while page is not None and id(page) not in seen:
        seen.add(id(page))
        out.append(page)
        page = getattr(page, attr)
    return out


def check_chain(nav) -> list[str]:
    """Problems with the prev/next chain. Empty list means all three hold."""
    sidebar = walk(nav.items)
    if not sidebar:
        return ["the nav has no pages -- nothing to check"]
    problems = []

    forward = [uri(p) for p in follow(sidebar[0], "next_page")]
    want = [uri(p) for p in sidebar]
    if forward != want:
        detail = f"the arrows reach {len(forward)} pages, the sidebar has {len(want)}"
        for i, (a, b) in enumerate(zip(want, forward)):
            if a != b:
                detail = (f"first divergence at position {i}:\n"
                          f"    sidebar says  {a}\n"
                          f"    arrows say    {b}")
                break
        problems.append("next_page does not follow the sidebar -- " + detail)

    backward = [uri(p) for p in follow(sidebar[-1], "previous_page")]
    if backward != want[::-1]:
        problems.append(
            f"previous_page is not the mirror of next_page: it reaches "
            f"{len(backward)} pages walking back from {want[-1]}, expected "
            f"{len(want)}")

    if [uri(p) for p in nav.pages] != want:
        problems.append(
            "nav.pages is not in sidebar order -- on_nav promises to rewrite "
            "it, and templates and plugins read it")

    return problems


def stale_entries() -> list[tuple[str, str | None, str]]:
    """NAV_ORDER / LABEL_OVERRIDES names with nothing behind them."""
    sys.path.insert(0, str(REPO))
    import mkdocs_hooks

    bad: list[tuple[str, str | None, str]] = []
    for key, names in mkdocs_hooks.NAV_ORDER.items():
        base = REPO / key if key else REPO
        if not base.is_dir():
            bad.append(("NAV_ORDER", None, key))
            continue
        for name in names:
            if not (base / name).exists():
                bad.append(("NAV_ORDER", key, name))
    folders = {d.name for d in REPO.rglob("*")
               if d.is_dir() and ".git" not in d.parts and "site" not in d.parts}
    for name in mkdocs_hooks.LABEL_OVERRIDES:
        if name not in folders:
            bad.append(("LABEL_OVERRIDES", None, name))
    return bad


def selftest() -> int:
    """Re-chain the pages alphabetically and prove the gate reports it."""
    nav = build_nav()
    pages = sorted(walk(nav.items), key=uri)
    for i, page in enumerate(pages):
        page.previous_page = pages[i - 1] if i else None
        page.next_page = pages[i + 1] if i + 1 < len(pages) else None
    nav.pages[:] = pages
    problems = check_chain(nav)
    if not problems:
        print("selftest FAILED: an alphabetical chain was reported as correct.")
        return 1
    print(f"selftest ok: an alphabetical chain is reported "
          f"({len(problems)} problem(s), first: "
          f"{problems[0].splitlines()[0]}).")
    return 0


def main(argv: list[str]) -> int:
    try:
        import mkdocs  # noqa: F401
    except ImportError:
        print("check_nav_chain: MkDocs not importable -- run under "
              "`uv run --group docs`", file=sys.stderr)
        return 2

    if "--selftest" in argv:
        return selftest()

    stale = stale_entries()
    if stale:
        print("nav tables name things that do not exist:\n")
        for table, key, name in stale:
            where = f"{table}[{key!r}]" if key is not None else table
            print(f"  {where} lists {name!r} -- no such file or folder")
        print("\n  These are silent no-ops: the hook skips a name it cannot")
        print("  match, so the page drops to the alphabetical tail and")
        print("  nothing is printed.\n")
        print("  Two ways this happens. A rename left the entry behind --")
        print("  update it. Or the folder exists on disk but is not committed")
        print("  yet, in which case the entry is ahead of its page: commit")
        print("  them together, as CONTRIBUTING's 'Nav order' section says.")
        return 1

    nav = build_nav()
    problems = check_chain(nav)
    if not problems:
        print(f"nav chain: the arrows follow the sidebar, both ways, all "
              f"{len(nav.pages)} pages.")
        return 0

    print("nav chain: the sidebar and the arrows disagree.\n")
    print("  Check that mkdocs_hooks.on_nav still reassigns previous_page,")
    print("  next_page and nav.pages after _visit() re-sorts the tree.\n")
    for problem in problems:
        print(f"  {problem}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
