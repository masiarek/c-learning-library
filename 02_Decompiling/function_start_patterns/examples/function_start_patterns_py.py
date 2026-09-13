"""Every Python function starts with the same instruction too -- RESUME, in every
CPython since 3.11 -- but nothing ever has to search for it. A function is an
object that holds its own code, and the code begins at offset 0 of that object;
the C build on this page has to be told where its functions start, or guess."""

import dis


def twice(x):
    return 2 * x


def orphan(x):
    return x + 1


for f in (twice, orphan):
    first = next(dis.get_instructions(f))
    print(f"{f.__name__:7} starts at offset {first.offset} with {first.opname}")
