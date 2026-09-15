"""Python keeps a symbol table too -- its compiler builds one for every scope --
and the standard library's symtable module hands it over: for each name in a
scope, whether it is a parameter there, assigned there, global, and whether
anything in that same scope reads it."""

import symtable

SOURCE = """\
def _helper(x):
    return x * 2

def twice(x):
    return _helper(x)

def orphan(x):
    print("nobody calls this:", x)
    return x + 1

print(twice(21))
"""


def show(table):
    print(f"== {table.get_name()} ==")
    for sym in table.get_symbols():
        kinds = [
            kind
            for kind, yes in (
                ("parameter", sym.is_parameter()),
                ("assigned", sym.is_assigned()),
                ("global", sym.is_global()),
                ("referenced", sym.is_referenced()),
            )
            if yes
        ]
        print(f"  {sym.get_name():8} {' '.join(kinds)}")


module = symtable.symtable(SOURCE, "symbols.py", "exec")
show(module)
for child in module.get_children():
    # Python 3.14 gives every function a hidden __annotate__ scope as well, for
    # its deferred annotations; skipped, so the output is the same before 3.14.
    if child.get_name() != "__annotate__":
        show(child)
