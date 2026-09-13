"""Python's str.format has the same shape of bug. A format the user supplies
is run against the arguments, and a replacement field can walk attributes and
subscripts -- so a format string can read things that were never meant to be
printed. f-strings cannot be built from user text at run time, which is the
same fix as Rust's: the format must be a literal."""
API_KEY = "hunter2"


class Greeting:
    def __init__(self, name):
        self.name = name


g = Greeting("Ada")

for fmt in ["Hello, {0.name}!", "{0.__class__.__name__}", "{0.__init__.__globals__[API_KEY]}"]:
    print(f"{fmt!r:44} -> {fmt.format(g)!r}")

# The % operator has the same problem with a mapping.
config = {"user": "ada", "api_key": API_KEY}
user_fmt = "%(api_key)s"
print(f"{user_fmt!r:44} -> {user_fmt % config!r}")

# A template with no attribute access: string.Template substitutes names only.
from string import Template
print(f"{'$name':44} -> {Template('$name').safe_substitute(name='Ada')!r}")
