"""The same word as str and as bytes. Indexing, upper-casing, cutting and
reversing give the C answer on the bytes object and the text answer on the
str -- and the cut that lands inside a character is an error at decode time,
not a silent byte string."""
s = "café"
b = s.encode("utf-8")
print(f"str {s!r}: len {len(s)}, s[3] = {s[3]!r}")
print(f"bytes {b!r}: len {len(b)}, b[3] = {b[3]}")

print(f"s.upper() = {s.upper()!r}")
print(f"b.upper() = {b.upper()!r}  -- ASCII only, like C's toupper")

cut = b[:4]
print(f"b[:4] = {cut!r}")
try:
    cut.decode("utf-8")
except UnicodeDecodeError as e:
    print(f"  .decode() -> UnicodeDecodeError: {e.reason} at byte {e.start}")

print(f"s[::-1] = {s[::-1]!r}")
print(f"b[::-1] = {b[::-1]!r}  -> decode(errors='replace') = {b[::-1].decode(errors='replace')!r}")
