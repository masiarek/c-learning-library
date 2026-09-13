"""The same jobs with Python's standard library and no ICU. A str is already
code points, so there is no conversion step and no status to reset; upper()
and casefold() know the full Unicode tables; unicodedata normalizes. What is
missing is the language: no locale reaches str.upper(), sorted() is code point
order, and there is no count of characters as a reader sees them."""
import unicodedata

text = "cafe\N{COMBINING ACUTE ACCENT} \N{REGIONAL INDICATOR SYMBOL LETTER P}\N{REGIONAL INDICATOR SYMBOL LETTER L}"
print(f"UTF-8 bytes {len(text.encode())}, UTF-16 units {len(text.encode('utf-16-le')) // 2}, "
      f"code points {len(text)}, characters: not in the standard library")

print(f"'straße'.upper()   = {'straße'.upper()!r}")
print(f"'istanbul'.upper() = {'istanbul'.upper()!r}  -- upper() takes no locale")
print(f"'Straße'.casefold() == 'STRASSE'.casefold(): {'Straße'.casefold() == 'STRASSE'.casefold()}")

composed = "caf\N{LATIN SMALL LETTER E WITH ACUTE}"
decomposed = "cafe\N{COMBINING ACUTE ACCENT}"
same_after_nfc = unicodedata.normalize("NFC", composed) == unicodedata.normalize("NFC", decomposed)
print(f"composed == decomposed: {composed == decomposed}; after NFC: {same_after_nfc}")
print(f"NFKC('ﬁle №①') = {unicodedata.normalize('NFKC', 'ﬁle №①')!r}")

words = ["zebra", "Zoo", "apple", "Ofen", "Öl", "Orange", "Łódź", "Lwów"]
print("sorted():", " ".join(sorted(words)), " -- code point order, strcmp's answer")

raw = "Kraków, Wrocław, Gdańsk".encode("cp1250")
print(f"decode('cp1250')  = {raw.decode('cp1250')!r}")
print(f"decode('latin-1') = {raw.decode('latin-1')!r}  -- no error here either")
try:
    "café".encode("ascii")
except UnicodeEncodeError as e:
    print(f"'café'.encode('ascii') -> UnicodeEncodeError: {e.reason}  -- strict by default")
print(f"'café'.encode('ascii', errors='replace') = {'café'.encode('ascii', errors='replace')!r}")
