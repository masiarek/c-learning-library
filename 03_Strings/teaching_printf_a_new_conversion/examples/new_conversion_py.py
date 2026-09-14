"""Python has no table of conversions to add to: a type answers for itself.
format() and f-strings hand everything after the colon to the value's
__format__ method, which reads '#', '-', a width and a precision out of it --
the flags C's %W renderer is handed in a struct printf_info. Nothing global
changes, so there is nothing to keep in a domain."""
import re

SPEC = re.compile(r"(#?)(-?)(\d*)(?:\.(\d+))?")


class Coordinate:
    def __init__(self, x, y):
        self.x, self.y = x, y

    def __format__(self, spec):
        m = SPEC.fullmatch(spec)
        if m is None:
            raise ValueError(f"bad Coordinate format {spec!r}")
        alt, left, width, prec = m.groups()
        number = f"{'<' if left else '>'}{width}.{prec or 6}f"
        x, y = format(self.x, number), format(self.y, number)
        return f"({x}, {y})" if alt else f"{x} {y}"


c = Coordinate(12345.6789, 3.141593)

# The same six as the C program, with the flags where Python puts them.
for spec in ["", "14", "-14.2", "#", "#14", "#-14.2"]:
    label = "{:" + spec + "}" if spec else "{}"
    print(f"{label:10} |{c:{spec}}|")

# Beside the standard conversions, in an f-string and in str.format alike.
print(f"point {1:d} is {c:#.1}, {'done'}")
print("point {:d} is {:#.1}, {}".format(1, c, "done"))

# A spec the type does not understand is the type's error, raised by its code.
try:
    f"{c:x}"
except ValueError as e:
    print(f"ValueError: {e}")
