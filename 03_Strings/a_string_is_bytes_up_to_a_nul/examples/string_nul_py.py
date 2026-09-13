"""Python has no NUL terminator: a str knows its length and a NUL is a
character like any other -- until the value is handed to something written in
C, where the same byte ends the string. The last two lines are that boundary,
reached from Python: ctypes, and the operating system's file names."""
import ctypes

word = "hello"
print(f'len("hello") = {len(word)} -- no sixth byte, the length is stored')

cut = "ab\0cd"
print(f'len("ab\\0cd") = {len(cut)}, and "ab\\0cd".find("\\0") = {cut.find(chr(0))}')
print(f'"ab\\0cd".encode() = {cut.encode()!r} -- six bytes, the NUL among them')

# Hand those bytes to C and the string ends where C says it does.
as_c_string = ctypes.c_char_p(cut.encode())
print(f"ctypes.c_char_p(...).value = {as_c_string.value!r}")

# The OS's file APIs are C: Python refuses at the door rather than let a
# name be silently cut short.
try:
    open(cut)
except ValueError as e:
    print(f"open('ab\\0cd') -> ValueError: {e}")
