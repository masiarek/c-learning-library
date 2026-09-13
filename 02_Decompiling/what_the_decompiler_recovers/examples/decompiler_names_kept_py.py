"""A Python function keeps its names. The C build on this page lost `total` and
`i` the moment the compiler finished; the code object CPython compiles this
function into carries them, and every bytecode that touches one names it."""


def sum_to(n):
    total = 0
    for i in range(1, n + 1):
        total += i
    return total


code = sum_to.__code__
print("sum_to(10)  =", sum_to(10))
print("co_varnames =", code.co_varnames)   # parameters first, then locals
print("co_names    =", code.co_names)      # globals the bytecode looks up
