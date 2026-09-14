# Strings

**One line:** A C string is not a type — it is an array of `char` with a zero byte somewhere in it, and the length nobody wrote down is the source of nearly every classic C bug: the copy that ran off the end, the accented letter that was really two bytes, the input that was trusted, and the format string that turned out to be a program.

Every other language in this library's family hands you a string that knows its own length and, usually, its own encoding. C hands you bytes and a convention: the string ends at the first `\0`, and everything else — how long it is, whether it is valid text, whether it fits — is found by looking, every time, by code you wrote. That single decision is why C is still the language at the edge of the machine, and why the same decision is behind decades of buffer overruns.

This chapter is the string, taken apart. The bytes and the terminator first, then the fact that a `char` is a byte and not a letter, then the library functions that were built without a length and the ones built to replace them, then reading a number back out of text, then the format string — the one piece of "data" that `printf` runs as code — and a directive of your own added to it, and finally ICU4C, the library a C program reaches for once its text stops being ASCII.

| Lesson | Level | What it settles |
|---|---|---|
| [A string is bytes up to a NUL](a_string_is_bytes_up_to_a_nul/README.md) | 101 → 201 | Where a C string ends, why `sizeof` and `strlen` give different answers, and why the length is gone the moment you pass the array to a function |
| [A `char` is a byte, not a character](char_is_a_byte_not_a_character/README.md) | 201 | Why an accented letter is more than one `char`, why `toupper` leaves it alone, and why `<ctype.h>` must be handed an `unsigned char` |
| [The functions that do not check](the_functions_that_do_not_check/README.md) | 201 → 301 | `strcpy`, `strcat` and `sprintf` take no length; what `strncpy`, `snprintf`, `strlcpy` and `fgets` do instead, and where each still bites |
| [Parsing a number from text](parsing_a_number_from_text/README.md) | 201 | `atoi` cannot report failure, `sscanf` half can, and `strtol` tells you exactly where it stopped and why — deserialization in miniature |
| [A format string is a program](a_format_string_is_a_program/README.md) | 301 | Why `printf(user_text)` is a security hole, what `%n` does, and the one compiler flag that turns the hole into a build error |
| [Teaching `printf` a new conversion](teaching_printf_a_new_conversion/README.md) | 301 | How glibc and Apple's libc let a program add `%W` to the format language — program-wide on one, inside a *printf domain* on the other — and why `man xprintf(5)` fails in every shell while `man 5 xprintf` opens the page |
| [ICU4C: Unicode text in C](unicode_text_with_icu4c/README.md) | 201 → 301 | A tutorial on the library that does what the lessons above show libc cannot — case in a language, normalization, character boundaries, sorting and code pages — and on the build flags, the status code and the UTF-16 it asks for in return |

## Where this connects

Strings are the crossing point between this library and its siblings, because every language meets the same bytes and answers differently:

- The [encodings library ↗](https://masiarek.github.io/encodings-learning-library/) teaches what the bytes *mean* — [the NUL byte ↗](https://masiarek.github.io/encodings-learning-library/02_Characters/the_nul_byte/index.html), [UTF-8 by hand ↗](https://masiarek.github.io/encodings-learning-library/03_Encodings/utf8_by_hand/index.html), and [C or Rust for text ↗](https://masiarek.github.io/encodings-learning-library/10_Best_Practices/c_or_rust_for_text/index.html), which is this whole chapter's argument in one page.
- The [Rust library's Strings chapter ↗](https://masiarek.github.io/rust-learning-library/14_Strings/index.html) is the same subject with the length and the encoding put back into the type, and its [C and C++ chapter ↗](https://masiarek.github.io/rust-learning-library/31_C_and_Cpp/index.html) runs the bugs this chapter explains.
- The [Python library's text chapter ↗](https://masiarek.github.io/python-learning-library/01_Text_and_Bytes/index.html) is Python's answer: `str` and `bytes` as two types that will not mix.

Each lesson here shows the C, then the same problem in Python and Rust with their own verified output, then a note on ABAP.
