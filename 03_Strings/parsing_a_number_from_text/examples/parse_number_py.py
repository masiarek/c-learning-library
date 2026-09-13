"""int() either returns the number or raises: there is no zero-on-failure.
It is also more permissive than strtol in ways worth knowing -- whitespace on
either side, underscores between digits, and no overflow at all."""
inputs = ["42", " 42", "42 ", "42abc", "", "abc", "-7", "+7", "0x1A",
          "99999999999999999999999", "1_000"]
for text in inputs:
    try:
        print(f"int({text!r:28}) = {int(text)}")
    except ValueError as e:
        print(f"int({text!r:28}) -> ValueError: {e}")

print(f"int('0x1A', 0) = {int('0x1A', 0)}, int('012', 0) -> ", end="")
try:
    print(int("012", 0))
except ValueError as e:
    print(f"ValueError: {e}")
print(f"int('0o12', 0) = {int('0o12', 0)}")
