# ICU4C: Unicode text in C

**Level:** 201 → 301 · a tutorial, for a C programmer whose text has stopped being ASCII

**One line:** ICU4C is the C edition of *International Components for Unicode*, the library that carries the Unicode and CLDR tables every real text job needs — case in a language, normalization, character boundaries, alphabetical order, legacy code pages — and C gets all of it for the price of two linked libraries, a status code that must start at zero, and UTF-16 at the heart of the API.

The lessons before this one keep ending at the same wall. `strlen` counts bytes, not characters. `toupper` cannot turn *straße* into *STRASSE*. `strcmp` puts *Zoo* before *apple* and *Łódź* after both, and nothing in libc knows that *é* has two spellings. None of these is a function you forgot to write: each is a table — often a very large one, from the Unicode Character Database or the CLDR locale data — plus an algorithm a Unicode standard specifies. **ICU** is where those tables and algorithms are implemented and kept current. It is a Unicode Consortium project with a C and C++ edition (ICU4C, this page), a Java edition (ICU4J) and a newer Rust one (ICU4X). The encodings library's ["Handles Unicode" is four questions ↗](https://masiarek.github.io/encodings-learning-library/10_Best_Practices/what_your_language_gives_you/index.html) makes the case that most languages' text handling is ICU underneath; this page is the C programmer's side of it — install it, link it, and use the six parts a C program most often needs.

## What you installed

```text title="Real output — brew install icu4c pkg-config, Homebrew 7.0.1, x86-64 macOS 26, 2026-09-13; the two lines that matter"
Warning: icu4c@78 78.3 is already installed and up-to-date.
Warning: pkgconf 3.0.7 is already installed and up-to-date.
```

Both names in that command are aliases. **`icu4c` is `icu4c@78`:** Homebrew keeps ICU as a versioned formula, because ICU's major version is part of the name of every C function it exports — step 1 below shows the linker asking for `ucasemap_open_78` — so a program is built against one major version, and the next one is a different library. **`pkg-config` is `pkgconf`**, a compatible reimplementation that answers the same questions.

And the formula is **keg-only**:

```text title="Real output — brew info icu4c, Homebrew 7.0.1, x86-64 macOS 26; the first two lines of its caveat"
icu4c@78 is keg-only, which means it was not symlinked into /usr/local,
because macOS provides libicucore.dylib (but nothing else).
```

macOS uses ICU itself, as `libicucore`, and its SDK ships a sliver of the headers — 23, against 205 in Homebrew's keg and 197 in Ubuntu's `libicu-dev` 74:

```text title="Real output — ls usr/include/unicode in the Command Line Tools SDK, macOS 26.6"
localpointer.h parseerr.h platform.h ptypes.h putil.h stringoptions.h uchar.h uconfig.h ucpmap.h uidna.h uiter.h umachine.h uregex.h urename.h ustring.h utext.h utf.h utf16.h utf8.h utf_old.h utypes.h uvernum.h uversion.h
```

Strings, character properties, a regex engine, IDNA — and no case mapping, normalization, break iteration, collation or conversion, which is most of this page. Because macOS already has an ICU, Homebrew leaves its own in the keg instead of linking it into its prefix, and `pkg-config` does not look inside kegs until told:

```bash
export PKG_CONFIG_PATH="$(brew --prefix icu4c)/lib/pkgconfig"    # bash, zsh
```

```fish
set -gx PKG_CONFIG_PATH (brew --prefix icu4c)/lib/pkgconfig      # fish
```

On Debian or Ubuntu, `sudo apt install libicu-dev pkg-config` puts everything where `pkg-config` already looks, and there is nothing to set.

ICU is several libraries, and a program links the ones it uses:

| Library | `pkg-config` module | What is in it | Headers on this page |
|---|---|---|---|
| `libicuuc` — *common* | `icu-uc` | UTF-16 strings, code-page converters, normalization, case mapping, break iteration | `ustring.h` `utext.h` `ubrk.h` `ucasemap.h` `unorm2.h` `ucnv.h` |
| `libicui18n` — *internationalization* | `icu-i18n` | collation, date and number formatting, transliteration | `ucol.h` |
| `libicudata` | comes with `icu-uc` | the tables themselves — character properties, CLDR locale data, converter mappings: 33 MB in Homebrew's 78.3, 31 MB in Ubuntu's 74.2 | none |
| `libicuio` | `icu-io` | `u_printf` and friends, for UTF-16 text | not used here |

`nm -gU` on Homebrew's libraries puts `ucol_open` in `libicui18n` and every other function this page calls in `libicuuc`.

## Step 1 — Build against it

[`demo/hello_icu.c`](demo/hello_icu.c) is the smallest program that needs ICU: the upper-casing of *straße* that [`toupper` could not do](../char_is_a_byte_not_a_character/README.md).

```c
/* The smallest program that needs ICU: upper-case a German word properly.
   <ctype.h>'s toupper works one byte at a time and cannot turn ß into SS;
   ICU's case mapping works on the whole UTF-8 string, in a language.

   Build: cc -std=c17 -Wall -Wextra -pedantic hello_icu.c $(pkg-config --cflags --libs icu-uc) -o hello_icu */
#include <stdio.h>
#include <unicode/ucasemap.h>

int main(void)
{
    const char *word = "Straße";
    char upper[32];
    UErrorCode status = U_ZERO_ERROR;                 /* must start at zero */

    UCaseMap *map = ucasemap_open("", 0, &status);    /* "" is the root locale */
    int32_t len = ucasemap_utf8ToUpper(map, upper, sizeof upper, word, -1, &status);
    ucasemap_close(map);

    if (U_FAILURE(status)) {
        fprintf(stderr, "ICU: %s\n", u_errorName(status));
        return 1;
    }
    printf("%s -> %.*s\n", word, (int)len, upper);
    return 0;
}
```

[`demo/Makefile`](demo/Makefile) builds it — or any one-file ICU program copied beside it — with no recipe of its own:

```make
# Build any one-file ICU program in this folder: `make hello_icu`, or copy an
# example beside it and `make icu_collate_c`. pkg-config supplies both halves:
# where the headers are (--cflags) and which libraries to link (--libs).
#
# On a Mac, Homebrew's ICU is keg-only and pkg-config cannot see it until told:
#   export PKG_CONFIG_PATH="$(brew --prefix icu4c)/lib/pkgconfig"
ICU    := icu-uc icu-i18n
CFLAGS := -std=c17 -Wall -Wextra -pedantic $(shell pkg-config --cflags $(ICU))
LDLIBS := $(shell pkg-config --libs $(ICU))

# No recipe: make's built-in rule turns hello_icu.c into hello_icu, and adds
# CFLAGS before the source and LDLIBS after it.
hello_icu:
```

[`icu_build_sh.sh`](examples/icu_build_sh.sh) builds the demo in a scratch copy three ways: by hand, with `make`, and with half the flags.

<!-- output:icu_build_sh -->
*Verified output of [`icu_build_sh.sh`](examples/icu_build_sh.sh) — regenerated by `tools/run_examples.py`, never hand-typed.*

```text
$ pkg-config --exists icu-uc icu-i18n && echo "pkg-config finds ICU"
pkg-config finds ICU
$ cc -std=c17 -Wall -Wextra -pedantic hello_icu.c $(pkg-config --cflags --libs icu-uc) -o hello_icu
$ ./hello_icu
Straße -> STRASSE
$ rm hello_icu
$ make -s hello_icu && ./hello_icu
Straße -> STRASSE
$ cc -std=c17 hello_icu.c $(pkg-config --cflags icu-uc) -o no_libs 2>/dev/null; echo "exit status $?"
exit status 1
```
<!-- /output -->

**`pkg-config` answers two questions.** `--cflags` says where the headers are and `--libs` what to link, and the answers differ between the two machines — which is the reason to ask rather than type them:

```text title="Real output — pkg-config --libs icu-uc icu-i18n, pkgconf 3.0.7 with Homebrew's icu4c@78, x86-64 macOS"
-L/usr/local/opt/icu4c@78/lib -licui18n -licuuc
```

```text title="Real output — the same question, pkg-config 1.8.1 with libicu-dev 74.2, ubuntu:24.04 in Docker"
-licui18n -licuuc -licudata
```

On Apple silicon the keg is under `/opt/homebrew` instead. Put the flags after the source file, as every command here does: GNU ld reads libraries in command-line order, and under Ubuntu's default `--as-needed` it drops a library that nothing before it has asked for. In `ubuntu:24.04`, `cc $(pkg-config --libs icu-uc) hello_icu.c` fails with the undefined references below, and the same flags after the source link.

**Half the flags gets past the compiler and fails at the linker.** With `--cflags` and no `--libs`, the compiler finds every declaration and the program compiles; only the linker notices that nothing defines them. Its message is worded differently on each machine, which is why the script asks only for the exit status:

```text title="Real output — cc -std=c17 hello_icu.c $(pkg-config --cflags icu-uc) -o no_libs, Apple clang 21 and ld, x86-64 macOS 26"
Undefined symbols for architecture x86_64:
  "_u_errorName_78", referenced from:
      _main in hello_icu-d33157.o
  "_ucasemap_close_78", referenced from:
      _main in hello_icu-d33157.o
  "_ucasemap_open_78", referenced from:
      _main in hello_icu-d33157.o
  "_ucasemap_utf8ToUpper_78", referenced from:
      _main in hello_icu-d33157.o
ld: symbol(s) not found for architecture x86_64
clang: error: linker command failed with exit code 1 (use -v to see invocation)
```

```text title="Real output — the same command, GCC 13.3 and GNU ld, ubuntu:24.04 in Docker with libicu-dev 74.2"
/usr/bin/ld: /tmp/ccR6X1C6.o: in function `main':
hello_icu.c:(.text+0x44): undefined reference to `ucasemap_open_74'
/usr/bin/ld: hello_icu.c:(.text+0x71): undefined reference to `ucasemap_utf8ToUpper_74'
/usr/bin/ld: hello_icu.c:(.text+0x80): undefined reference to `ucasemap_close_74'
/usr/bin/ld: hello_icu.c:(.text+0x91): undefined reference to `u_errorName_74'
collect2: error: ld returned 1 exit status
```

**Every name carries the major version.** The program calls `ucasemap_open`; the linker looks for `ucasemap_open_78` on the Mac and `ucasemap_open_74` on Ubuntu. ICU's `urename.h` renames each C function this way, so a binary built against one major version cannot quietly load another: it fails to start instead of misbehaving. It is also why the Homebrew formula is `icu4c@78` and Ubuntu's runtime package is `libicu74`.

Leave `pkg-config` out altogether and the Mac stops a step earlier, at the compiler, because `ucasemap.h` is not among the SDK's 23 headers. On Ubuntu, `libicu-dev` puts its headers in `/usr/include`, so the same command compiles and fails at the link, as above.

```text title="Real output — cc -std=c17 hello_icu.c -o hello_icu with no flags and no PKG_CONFIG_PATH, Apple clang 21, x86-64 macOS 26"
hello_icu.c:7:10: fatal error: 'unicode/ucasemap.h' file not found
    7 | #include <unicode/ucasemap.h>
      |          ^~~~~~~~~~~~~~~~~~~~
```

## Step 2 — UTF-16, a status, and asking for the size first

Every ICU program uses the same three conventions, and [`icu_strings_c.c`](examples/icu_strings_c.c) shows all three on one short string — *café* followed by the Polish flag — before counting it four ways:

<!-- source:icu_strings_c -->
*[`icu_strings_c.c`](examples/icu_strings_c.c) in full — pasted here by `tools/run_examples.py` from the file CI runs.*

```c
/* ICU's C API mostly works in UTF-16, so UTF-8 text is converted first. Every
   call takes a UErrorCode that must start at U_ZERO_ERROR, and the way to size
   a buffer is to ask with no buffer at all. Then four lengths of one short
   string -- the last of which, what a reader calls a character, only a break
   iterator can count -- and the byte offsets where it is safe to cut.

   Build: cc -std=c17 -Wall -Wextra -pedantic icu_strings_c.c $(pkg-config --cflags --libs icu-uc) -o icu_strings_c */
#include <stdio.h>
#include <string.h>
#include <unicode/ustring.h>
#include <unicode/ubrk.h>
#include <unicode/utext.h>

int main(void)
{
    /* "café" and the Polish flag. The é is spelled e + COMBINING ACUTE ACCENT
       (cc 81), and the flag is two regional-indicator code points. */
    const char *text = "cafe\xcc\x81 \xf0\x9f\x87\xb5\xf0\x9f\x87\xb1";

    /* 1. Preflight: no buffer, capacity 0. The length comes back, and the
          status says the buffer overflowed -- expected here, not a failure. */
    UErrorCode status = U_ZERO_ERROR;
    int32_t units = 0;
    u_strFromUTF8(NULL, 0, &units, text, -1, &status);
    printf("preflight:      need %d UTF-16 units, status %s\n", (int)units, u_errorName(status));

    /* 2. Reset the status, then convert for real into a buffer that fits. */
    UChar buf[32];
    status = U_ZERO_ERROR;
    u_strFromUTF8(buf, 32, &units, text, -1, &status);
    printf("convert:        status %s\n", u_errorName(status));

    /* 3. Four lengths of the same text. */
    printf("UTF-8 bytes     strlen          %zu\n", strlen(text));
    printf("UTF-16 units    u_strlen        %d\n", (int)u_strlen(buf));
    printf("code points     u_countChar32   %d\n", (int)u_countChar32(buf, units));

    /* A character break iterator walks the UTF-8 bytes directly through a
       UText, and every boundary it reports is a byte offset where cutting
       leaves whole characters on both sides. */
    UText *ut = utext_openUTF8(NULL, text, -1, &status);
    UBreakIterator *chars = ubrk_open(UBRK_CHARACTER, "", NULL, 0, &status);
    ubrk_setUText(chars, ut, &status);
    int count = 0;
    printf("safe cuts at byte");
    for (int32_t at = ubrk_first(chars); at != UBRK_DONE; at = ubrk_next(chars)) {
        printf(" %d", (int)at);
        count += at > 0;
    }
    printf("\ncharacters      ubrk_next       %d  (status %s)\n", count, u_errorName(status));
    ubrk_close(chars);
    utext_close(ut);

    /* 4. A status already holding a failure makes an ICU call return at once
          and touch nothing. That is what lets a chain of calls be checked once
          at the end -- and what bites when a status is not reset. */
    int32_t untouched = -1;
    status = U_BUFFER_OVERFLOW_ERROR;               /* left over from step 1 */
    u_strFromUTF8(buf, 32, &untouched, "abc", -1, &status);
    printf("stale status:   length still %d, status %s\n", (int)untouched, u_errorName(status));

    /* 5. Bytes that are not UTF-8 are reported, not guessed at. */
    status = U_ZERO_ERROR;
    u_strFromUTF8(buf, 32, &units, "caf\xc3", -1, &status);
    printf("\"caf\\xc3\":      status %s\n", u_errorName(status));
    return 0;
}
```
<!-- /source -->

<!-- output:icu_strings_c -->
*Verified output of [`icu_strings_c.c`](examples/icu_strings_c.c) — regenerated by `tools/run_examples.py`, never hand-typed.*

```text
preflight:      need 10 UTF-16 units, status U_BUFFER_OVERFLOW_ERROR
convert:        status U_ZERO_ERROR
UTF-8 bytes     strlen          15
UTF-16 units    u_strlen        10
code points     u_countChar32   8
safe cuts at byte 0 1 2 3 6 7 15
characters      ubrk_next       6  (status U_ZERO_ERROR)
stale status:   length still -1, status U_BUFFER_OVERFLOW_ERROR
"caf\xc3":      status U_INVALID_CHAR_FOUND
```
<!-- /output -->

**ICU thinks in UTF-16.** `UChar` is a 16-bit code unit, and most of the C API takes a `UChar *` and a length. Some parts take UTF-8 directly — case mapping, collation and break iteration below all have UTF-8 entry points, and a `UText` wraps UTF-8 for any iterator — but everything else means converting in and out, with `u_strFromUTF8` here and `u_strToUTF8` in step 4.

**Ask for the size first.** Called with no buffer and a capacity of 0, `u_strFromUTF8` writes nothing, stores the length the result needs, and sets `U_BUFFER_OVERFLOW_ERROR`. Allocate that many units plus one for the NUL, reset the status, and call again. It is the preflight `snprintf(NULL, 0, …)` offers in [the functions that do not check](../the_functions_that_do_not_check/README.md), and every ICU function that fills a buffer supports it.

**Four lengths, and only the last is what a reader counts.** 15 UTF-8 bytes. 10 UTF-16 units, because each of the flag's two regional indicators lies outside the Basic Multilingual Plane and takes a surrogate pair. 8 code points. And 6 characters — `c`, `a`, `f`, `é`, the space, the flag — each a *grapheme cluster*, whose rules (Unicode's UAX #29) are what a character break iterator carries. They are the same four numbers as [Python's four answers ↗](https://masiarek.github.io/python-learning-library/01_Text_and_Bytes/counting_characters/index.html).

**A boundary is where a cut is safe.** The iterator reports byte offsets 0 1 2 3 6 7 15. There is none at 4 or 5, inside the `e` and its accent, and none from 8 to 14, inside the flag. A C program truncating UTF-8 to fit a buffer should cut at the last boundary at or below the limit — the fix for the cut [the `char` lesson](../char_is_a_byte_not_a_character/README.md) made through the middle of *é*.

**A status that already failed makes the next call do nothing.** Every ICU function starts by checking the status it was handed and returns at once if it holds a failure: the length stayed `-1`, and nothing was converted. That is deliberate — step 4 chains three calls and checks the status once. It is also the classic ICU bug, because the preflight *leaves a failure behind*, and a program that forgets to reset the status before the real call gets nothing back and an error that looks as if the buffer were still too small.

**Warnings are successes; failures are the positive codes.** `U_ZERO_ERROR` is 0, warnings are negative and errors positive, so test with `U_SUCCESS(status)` or `U_FAILURE(status)` and never with `status == U_ZERO_ERROR` — steps 3, 5 and 6 each get a warning back from an ordinary success. Invalid UTF-8 is an error, `U_INVALID_CHAR_FOUND` for the lone lead byte `c3`; `u_strFromUTF8WithSub` puts U+FFFD in its place instead, if that is what you want.

## Step 3 — Case needs a language

[`icu_case_c.c`](examples/icu_case_c.c) maps case with `UCaseMap`, which works on UTF-8 directly and takes a locale when it is opened:

<!-- source:icu_case_c -->
*[`icu_case_c.c`](examples/icu_case_c.c) in full — pasted here by `tools/run_examples.py` from the file CI runs.*

```c
/* Case mapping needs a table and a language, not one byte at a time. ICU's
   UCaseMap works on UTF-8 directly: ß upper-cases to two letters, Turkish has
   a dotted and a dotless i, and a case-insensitive comparison folds rather
   than lower-cases. The caller sizes the output, and ICU reports a buffer one
   byte short -- the byte the NUL needed -- as a warning, which is a success.

   Build: cc -std=c17 -Wall -Wextra -pedantic icu_case_c.c $(pkg-config --cflags --libs icu-uc) -o icu_case_c */
#include <stdio.h>
#include <string.h>
#include <unicode/ucasemap.h>

/* ucasemap_utf8ToUpper, ...ToLower and ...FoldCase share one signature. */
typedef int32_t CaseFn(const UCaseMap *, char *, int32_t, const char *, int32_t, UErrorCode *);

static void map_case(const char *name, CaseFn *fn, const char *locale, const char *in)
{
    UErrorCode status = U_ZERO_ERROR;
    UCaseMap *map = ucasemap_open(locale, 0, &status);
    char out[64];
    int32_t len = fn(map, out, sizeof out, in, -1, &status);
    ucasemap_close(map);
    printf("%s, %s locale: %s -> %.*s\n", name, locale[0] ? locale : "root", in, (int)len, out);
}

int main(void)
{
    map_case("upper", ucasemap_utf8ToUpper, "", "straße");
    map_case("upper", ucasemap_utf8ToUpper, "", "istanbul");
    map_case("upper", ucasemap_utf8ToUpper, "tr", "istanbul");
    map_case("lower", ucasemap_utf8ToLower, "", "DİYARBAKIR");
    map_case("lower", ucasemap_utf8ToLower, "tr", "DİYARBAKIR");
    map_case("fold", ucasemap_utf8FoldCase, "", "Straße");
    map_case("fold", ucasemap_utf8FoldCase, "", "STRASSE");

    /* "STRASSE" is 7 bytes and its NUL makes 8. The return value is always
       the length the whole answer needs; the status says what fitted. */
    UErrorCode status = U_ZERO_ERROR;
    UCaseMap *root = ucasemap_open("", 0, &status);
    char out[8];
    for (int32_t capacity = 8; capacity >= 6; capacity--) {
        status = U_ZERO_ERROR;
        int32_t need = ucasemap_utf8ToUpper(root, out, capacity, "straße", -1, &status);
        printf("capacity %d: returns %d, status %s, U_SUCCESS %d\n",
               (int)capacity, (int)need, u_errorName(status), U_SUCCESS(status) ? 1 : 0);
    }
    ucasemap_close(root);
    return 0;
}
```
<!-- /source -->

<!-- output:icu_case_c -->
*Verified output of [`icu_case_c.c`](examples/icu_case_c.c) — regenerated by `tools/run_examples.py`, never hand-typed.*

```text
upper, root locale: straße -> STRASSE
upper, root locale: istanbul -> ISTANBUL
upper, tr locale: istanbul -> İSTANBUL
lower, root locale: DİYARBAKIR -> di̇yarbakir
lower, tr locale: DİYARBAKIR -> diyarbakır
fold, root locale: Straße -> strasse
fold, root locale: STRASSE -> strasse
capacity 8: returns 7, status U_ZERO_ERROR, U_SUCCESS 1
capacity 7: returns 7, status U_STRING_NOT_TERMINATED_WARNING, U_SUCCESS 1
capacity 6: returns 7, status U_BUFFER_OVERFLOW_ERROR, U_SUCCESS 0
```
<!-- /output -->

**`ß` upper-cases to two letters.** Six letters in, seven out. Case mapping does not preserve length, so it cannot be done in place, and ICU returns the length the result needs. The byte count happens to stay at 7, because `ß` is two bytes in UTF-8 and `SS` is two ASCII bytes; in general it changes.

**The locale changes the answer.** In the root locale, `""`, *istanbul* upper-cases to `ISTANBUL`; in Turkish, `"tr"`, to `İSTANBUL`, because Turkish pairs a dotted `i` with `İ` and a dotless `ı` with `I`. Lower-casing `DİYARBAKIR` without Turkish gives `i` followed by U+0307 COMBINING DOT ABOVE — the dot kept as a character of its own, so nothing is lost — and with Turkish gives `diyarbakır`. Upper-casing a Turkish user's input without the Turkish locale is the "Turkish i" bug, and Python's `str.upper()` [cannot be given the locale at all ↗](https://masiarek.github.io/python-learning-library/01_Text_and_Bytes/lowercasing_is_not_folding/index.html).

**Compare without case by folding, not by lower-casing.** `ucasemap_utf8FoldCase` maps both `Straße` and `STRASSE` to `strasse`, so `strcmp` on the folded bytes finds them equal. Lower-casing would give `straße` and `strasse`, which are not. Folded text is for comparing, never for showing to a person.

**One byte short is a warning, and the missing byte is the NUL.** The return value is 7 every time: the length of the whole answer. With room for 8 bytes ICU writes the answer and its NUL. With 7 it writes the answer and **no NUL**, and reports `U_STRING_NOT_TERMINATED_WARNING` — a warning, so `U_SUCCESS` is 1 and a check for failure passes. `printf("%s", out)` would then read past the end of the buffer, the bug [the first lesson of this chapter](../a_string_is_bytes_up_to_a_nul/README.md) is about. With 6 it is `U_BUFFER_OVERFLOW_ERROR`. Size a buffer from the preflight, plus one.

## Step 4 — Normalization: one word, two spellings

[`icu_normalize_c.c`](examples/icu_normalize_c.c) takes both spellings of *café* through ICU's normalizer, which works in UTF-16:

<!-- source:icu_normalize_c -->
*[`icu_normalize_c.c`](examples/icu_normalize_c.c) in full — pasted here by `tools/run_examples.py` from the file CI runs.*

```c
/* One word, two byte sequences: é as one code point, or as e followed by a
   combining acute. strcmp sees two strings; a reader sees one. Normalization
   picks a single spelling -- NFC composes, NFD decomposes -- and NFKC also
   flattens compatibility characters such as the fi ligature. ICU's C
   normalizer works in UTF-16, so UTF-8 goes through a round trip.

   Build: cc -std=c17 -Wall -Wextra -pedantic icu_normalize_c.c $(pkg-config --cflags --libs icu-uc) -o icu_normalize_c */
#include <stdio.h>
#include <string.h>
#include <unicode/ustring.h>
#include <unicode/unorm2.h>

static void print_bytes(const char *label, const char *s)
{
    printf("%-18s", label);
    for (; *s; s++)
        printf(" %02x", (unsigned char)*s);
    printf("\n");
}

/* UTF-8 in, UTF-16 through the normalizer, UTF-8 out. The status is checked
   by the caller: once it holds a failure, every later call here does nothing. */
static void normalize_utf8(const UNormalizer2 *form, const char *in, char *out, int32_t capacity,
                           UErrorCode *status)
{
    UChar src[64], dst[64];
    int32_t len = 0;
    u_strFromUTF8(src, 64, &len, in, -1, status);
    len = unorm2_normalize(form, src, len, dst, 64, status);
    u_strToUTF8(out, capacity, &len, dst, len, status);
}

int main(void)
{
    const char *composed = "caf\xc3\xa9";       /* é is U+00E9, two bytes         */
    const char *decomposed = "cafe\xcc\x81";    /* e, then U+0301: three bytes    */
    print_bytes("composed", composed);
    print_bytes("decomposed", decomposed);
    printf("strcmp:            %s\n", strcmp(composed, decomposed) == 0 ? "equal" : "different");

    UErrorCode status = U_ZERO_ERROR;
    const UNormalizer2 *nfc = unorm2_getNFCInstance(&status);
    const UNormalizer2 *nfd = unorm2_getNFDInstance(&status);
    const UNormalizer2 *nfkc = unorm2_getNFKCInstance(&status);

    char a[64], b[64];
    normalize_utf8(nfc, composed, a, sizeof a, &status);
    normalize_utf8(nfc, decomposed, b, sizeof b, &status);
    print_bytes("NFC(composed)", a);
    print_bytes("NFC(decomposed)", b);
    printf("strcmp after NFC:  %s\n", strcmp(a, b) == 0 ? "equal" : "different");

    normalize_utf8(nfd, composed, a, sizeof a, &status);
    print_bytes("NFD(composed)", a);

    normalize_utf8(nfkc, "ﬁle №①", a, sizeof a, &status);
    printf("NFKC(\"ﬁle №①\"):   %s\n", a);
    printf("status:            %s\n", u_errorName(status));
    return 0;
}
```
<!-- /source -->

<!-- output:icu_normalize_c -->
*Verified output of [`icu_normalize_c.c`](examples/icu_normalize_c.c) — regenerated by `tools/run_examples.py`, never hand-typed.*

```text
composed           63 61 66 c3 a9
decomposed         63 61 66 65 cc 81
strcmp:            different
NFC(composed)      63 61 66 c3 a9
NFC(decomposed)    63 61 66 c3 a9
strcmp after NFC:  equal
NFD(composed)      63 61 66 65 cc 81
NFKC("ﬁle №①"):   file No1
status:            U_ZERO_ERROR
```
<!-- /output -->

**Two spellings, both correct.** The composed one is `c3 a9`, the single code point U+00E9. The decomposed one is `65 cc 81`, an `e` followed by U+0301 COMBINING ACUTE ACCENT. A keyboard, a file system or a paste from another program can produce either, and they look identical on screen. `strcmp` calls them different, and so does every hash table and index that compares bytes.

**Normalize, then compare.** NFC composes: both spellings come out as `63 61 66 c3 a9`, and `strcmp` agrees they are equal. NFD decomposes: the composed spelling comes out as the decomposed bytes. Pick one form — NFC is the usual choice for storage and interchange — and apply it where text enters the program, so everything after that point can go on comparing bytes.

**NFKC also flattens look-alikes, and that loses information.** `ﬁle №①` becomes `file No1`: the ligature splits into two letters, the numero sign becomes `N` and `o`, the circled digit a plain `1`. That is right for a search key or an identifier, and wrong for storing what someone typed.

**The status is checked once.** `normalize_utf8` makes three ICU calls and checks none of them. If the first fails, the next two return at once — step 2's rule — and the one status tells the caller whether any of it happened. Real code checks that status before it uses the output; this program prints it last.

## Step 5 — Sorting: order belongs to a language

[`icu_collate_c.c`](examples/icu_collate_c.c) sorts the same eight words by bytes and then by four collators:

<!-- source:icu_collate_c -->
*[`icu_collate_c.c`](examples/icu_collate_c.c) in full — pasted here by `tools/run_examples.py` from the file CI runs.*

```c
/* Alphabetical order belongs to a language. strcmp orders bytes, which puts
   every capital before every small letter and every accented letter after z.
   An ICU collator applies the Unicode Collation Algorithm and a language's
   tailoring on top: Swedish files Ö after Z, Polish makes Ł a letter of its
   own after L, and German phonebook order reads Ö as OE. And a locale ICU has
   no data for is not an error -- it is a warning, and root order.

   Build: cc -std=c17 -Wall -Wextra -pedantic icu_collate_c.c $(pkg-config --cflags --libs icu-uc icu-i18n) -o icu_collate_c */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unicode/ucol.h>

static const char *words[] = {"zebra", "Zoo", "apple", "Ofen", "Öl", "Orange", "Łódź", "Lwów"};
enum { N = sizeof words / sizeof words[0] };

static UCollator *collator;           /* qsort's comparator has no context */

static int by_bytes(const void *a, const void *b)
{
    return strcmp(*(const char *const *)a, *(const char *const *)b);
}

static int by_collator(const void *a, const void *b)
{
    UErrorCode status = U_ZERO_ERROR;
    return ucol_strcollUTF8(collator, *(const char *const *)a, -1, *(const char *const *)b, -1,
                            &status);
}

static void print_sorted(const char *label, int (*compare)(const void *, const void *))
{
    const char *list[N];
    memcpy(list, words, sizeof words);
    qsort(list, N, sizeof list[0], compare);
    printf("%-23s", label);
    for (int i = 0; i < N; i++)
        printf(" %s", list[i]);
    printf("\n");
}

int main(void)
{
    print_sorted("strcmp", by_bytes);

    const char *locales[] = {"en", "sv", "pl", "de@collation=phonebook"};
    for (size_t i = 0; i < sizeof locales / sizeof locales[0]; i++) {
        UErrorCode status = U_ZERO_ERROR;
        collator = ucol_open(locales[i], &status);
        if (U_FAILURE(status)) {
            printf("%s: %s\n", locales[i], u_errorName(status));
            return 1;
        }
        print_sorted(locales[i], by_collator);
        ucol_close(collator);
    }

    /* What ucol_open says, and which data it actually loaded. */
    const char *asked[] = {"sv", "de", "xx"};
    for (size_t i = 0; i < sizeof asked / sizeof asked[0]; i++) {
        UErrorCode status = U_ZERO_ERROR;
        UCollator *c = ucol_open(asked[i], &status);
        UErrorCode ignored = U_ZERO_ERROR;
        printf("ucol_open(\"%s\"): %s, rules from %s\n", asked[i], u_errorName(status),
               ucol_getLocaleByType(c, ULOC_ACTUAL_LOCALE, &ignored));
        ucol_close(c);
    }

    /* Strength: at primary strength only the base letters count. */
    UErrorCode status = U_ZERO_ERROR;
    collator = ucol_open("", &status);
    ucol_setStrength(collator, UCOL_PRIMARY);
    printf("primary strength: \"cafe\" %s \"CAFÉ\"\n",
           ucol_strcollUTF8(collator, "cafe", -1, "CAFÉ", -1, &status) == UCOL_EQUAL ? "==" : "!=");
    ucol_close(collator);
    return 0;
}
```
<!-- /source -->

<!-- output:icu_collate_c -->
*Verified output of [`icu_collate_c.c`](examples/icu_collate_c.c) — regenerated by `tools/run_examples.py`, never hand-typed.*

```text
strcmp                  Lwów Ofen Orange Zoo apple zebra Öl Łódź
en                      apple Łódź Lwów Ofen Öl Orange zebra Zoo
sv                      apple Łódź Lwów Ofen Orange zebra Zoo Öl
pl                      apple Lwów Łódź Ofen Öl Orange zebra Zoo
de@collation=phonebook  apple Łódź Lwów Öl Ofen Orange zebra Zoo
ucol_open("sv"): U_ZERO_ERROR, rules from sv
ucol_open("de"): U_USING_DEFAULT_WARNING, rules from root
ucol_open("xx"): U_USING_DEFAULT_WARNING, rules from root
primary strength: "cafe" == "CAFÉ"
```
<!-- /output -->

**`strcmp` is byte order.** Capitals come first, because `Z` is 0x5A and `a` is 0x61, and every letter outside ASCII comes last, because its UTF-8 lead byte is above 0x7F — so *Łódź* sorts after *zebra*. That is nobody's alphabet.

**A collator sorts the way a dictionary does.** Root order — `en` uses it unchanged — looks at the letters first and at accents and case only to break ties: `Łódź` files among the L words, `Öl` between `Ofen` and `Orange`, and `zebra` before `Zoo`. Each language then tailors it:

- **Swedish** (`sv`) — `Ö` is a letter of its own, after `Z`, so `Öl` goes last.
- **Polish** (`pl`) — `Ł` is a letter of its own, after `L`, so `Lwów` now comes before `Łódź`.
- **German phonebook** (`de@collation=phonebook`) — `Ö` sorts as `OE`, so `Öl` comes before `Ofen`.

**"No rules of its own" is a warning, and so is a typo.** `ucol_open("de")` returns `U_USING_DEFAULT_WARNING` and rules from `root`, because standard German order *is* the root order and there is no German tailoring to load. `ucol_open("xx")`, a language that does not exist, returns exactly the same. A misspelt locale name is a silent success, so when the name comes from configuration, check it against the locales you mean to support.

**Strength decides what counts as different.** At primary strength only the base letters are compared, so `cafe` and `CAFÉ` are equal — the comparison behind a search that ignores case and accents. Secondary strength adds accents; tertiary, the default, adds case.

**Open once, compare many times.** `ucol_open` loads locale data, so open one collator per locale and reuse it. `qsort`'s comparator takes no context pointer, which is why the collator here is a file-scope variable; `qsort_r` would take one, but BSD and glibc put its arguments in different orders. To keep an order in a database column, `ucol_getSortKey` turns a string into bytes that `memcmp` orders correctly — and those bytes change when ICU's data does, which is the problem the encodings library's [sorting and collation ↗](https://masiarek.github.io/encodings-learning-library/07_Real_Data/sorting_and_collation/index.html) follows into a corrupted PostgreSQL index.

## Step 6 — Code pages: bytes do not say what they are

[`icu_convert_c.c`](examples/icu_convert_c.c) reads a line of Windows-1250 — the code page of Polish text on 1990s Windows — two ways, and then writes *café* to ASCII:

<!-- source:icu_convert_c -->
*[`icu_convert_c.c`](examples/icu_convert_c.c) in full — pasted here by `tools/run_examples.py` from the file CI runs.*

```c
/* Bytes in a legacy code page become Unicode through a converter, and nothing
   in the bytes says which converter. The same bytes read as Windows-1250 are
   Polish; read as ISO-8859-1 they are mojibake, and neither read is an error.
   The other way, a character the target cannot hold becomes a substitute byte
   -- again with no error -- until you install the callback that stops.

   Build: cc -std=c17 -Wall -Wextra -pedantic icu_convert_c.c $(pkg-config --cflags --libs icu-uc) -o icu_convert_c */
#include <stdio.h>
#include <string.h>
#include <unicode/ucnv.h>
#include <unicode/ustring.h>

int main(void)
{
    /* "Kraków, Wrocław, Gdańsk" in Windows-1250: ó f3, ł b3, ń f1. */
    const char cp1250[] = "Krak\xf3w, Wroc\xb3" "aw, Gda\xf1sk";
    char utf8[64];

    UErrorCode status = U_ZERO_ERROR;
    int32_t len = ucnv_convert("UTF-8", "cp1250", utf8, sizeof utf8, cp1250, -1, &status);
    printf("read as cp1250:     %s  (%zu bytes -> %d, %s)\n", utf8, strlen(cp1250), (int)len,
           u_errorName(status));

    status = U_ZERO_ERROR;
    ucnv_convert("UTF-8", "ISO-8859-1", utf8, sizeof utf8, cp1250, -1, &status);
    printf("read as ISO-8859-1: %s  (%s)\n", utf8, u_errorName(status));

    /* A name is an alias for a table, and some aliases are claimed twice. */
    const char *names[] = {"cp1250", "windows-1250", "ISO-8859-2"};
    for (size_t i = 0; i < sizeof names / sizeof names[0]; i++) {
        status = U_ZERO_ERROR;
        UConverter *cnv = ucnv_open(names[i], &status);
        UErrorCode ignored = U_ZERO_ERROR;
        printf("ucnv_open(\"%s\"): %s, table %s\n", names[i], u_errorName(status),
               ucnv_getName(cnv, &ignored));
        ucnv_close(cnv);
    }

    /* Unicode to US-ASCII: é has no ASCII byte. */
    UChar text[16];
    int32_t units = 0;
    status = U_ZERO_ERROR;
    u_strFromUTF8(text, 16, &units, "café", -1, &status);
    UConverter *ascii = ucnv_open("US-ASCII", &status);
    char out[16];

    len = ucnv_fromUChars(ascii, out, sizeof out, text, units, &status);
    printf("to US-ASCII, default callback:");
    for (int32_t i = 0; i < len; i++)
        printf(" %02x", (unsigned char)out[i]);
    printf("  (%s)\n", u_errorName(status));

    ucnv_setFromUCallBack(ascii, UCNV_FROM_U_CALLBACK_STOP, NULL, NULL, NULL, &status);
    len = ucnv_fromUChars(ascii, out, sizeof out, text, units, &status);
    printf("to US-ASCII, STOP callback:    %d bytes  (%s)\n", (int)len, u_errorName(status));
    ucnv_close(ascii);
    return 0;
}
```
<!-- /source -->

<!-- output:icu_convert_c -->
*Verified output of [`icu_convert_c.c`](examples/icu_convert_c.c) — regenerated by `tools/run_examples.py`, never hand-typed.*

```text
read as cp1250:     Kraków, Wrocław, Gdańsk  (23 bytes -> 26, U_ZERO_ERROR)
read as ISO-8859-1: Kraków, Wroc³aw, Gdañsk  (U_ZERO_ERROR)
ucnv_open("cp1250"): U_ZERO_ERROR, table ibm-5346_P100-1998
ucnv_open("windows-1250"): U_AMBIGUOUS_ALIAS_WARNING, table ibm-5346_P100-1998
ucnv_open("ISO-8859-2"): U_ZERO_ERROR, table ibm-912_P100-1995
to US-ASCII, default callback: 63 61 66 1a  (U_ZERO_ERROR)
to US-ASCII, STOP callback:    3 bytes  (U_INVALID_CHAR_FOUND)
```
<!-- /output -->

**The code page is a claim about the bytes, and ICU cannot check it.** Read as `cp1250`, the 23 bytes are *Kraków, Wrocław, Gdańsk* — 26 bytes of UTF-8. Read as ISO-8859-1 they are *Kraków, Wroc³aw, Gdañsk*, with `U_ZERO_ERROR`: every byte means *something* in Latin-1, so there is nothing to report. Only `ó` survived, because 0xF3 is `ó` in both tables. Which code page a file is in has to come from outside the file — [code pages ↗](https://masiarek.github.io/encodings-learning-library/02_Characters/code_pages/index.html) and [Windows-1252 is not Latin-1 ↗](https://masiarek.github.io/encodings-learning-library/07_Real_Data/windows_1252_vs_latin1/index.html) in the encodings library are this trap at length.

**A name is an alias for a table, and some aliases are claimed twice.** `cp1250` and `windows-1250` open the same table, `ibm-5346_P100-1998`, but `windows-1250` also reports `U_AMBIGUOUS_ALIAS_WARNING`: more than one table in ICU's alias data answers to that name, ICU picked one, and `ucnv_getName` says which. ISO-8859-2, the other Polish code page, is a different table altogether.

**Going out, the default is to substitute and say nothing.** US-ASCII has no `é`, and the default callback writes the ASCII substitute character, `1a`, in its place with `U_ZERO_ERROR` — `caf` and a control character, and no sign that a letter was lost. With `UCNV_FROM_U_CALLBACK_STOP` installed, the conversion stops after 3 bytes with `U_INVALID_CHAR_FOUND`. Python's default is the opposite, as the next section shows.

## The same jobs in Python

Python's standard library covers more of this than libc does, and [`icu_stdlib_py.py`](examples/icu_stdlib_py.py) runs the same inputs to show where it stops:

<!-- source:icu_stdlib_py -->
*[`icu_stdlib_py.py`](examples/icu_stdlib_py.py) in full — pasted here by `tools/run_examples.py` from the file CI runs.*

```python
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
```
<!-- /source -->

<!-- output:icu_stdlib_py -->
*Verified output of [`icu_stdlib_py.py`](examples/icu_stdlib_py.py) — regenerated by `tools/run_examples.py`, never hand-typed.*

```text
UTF-8 bytes 15, UTF-16 units 10, code points 8, characters: not in the standard library
'straße'.upper()   = 'STRASSE'
'istanbul'.upper() = 'ISTANBUL'  -- upper() takes no locale
'Straße'.casefold() == 'STRASSE'.casefold(): True
composed == decomposed: False; after NFC: True
NFKC('ﬁle №①') = 'file No1'
sorted(): Lwów Ofen Orange Zoo apple zebra Öl Łódź  -- code point order, strcmp's answer
decode('cp1250')  = 'Kraków, Wrocław, Gdańsk'
decode('latin-1') = 'Kraków, Wroc³aw, Gdañsk'  -- no error here either
'café'.encode('ascii') -> UnicodeEncodeError: ordinal not in range(128)  -- strict by default
'café'.encode('ascii', errors='replace') = b'caf?'
```
<!-- /output -->

A `str` is already code points, so there is no conversion step and no status to reset; `upper()` and `casefold()` carry the full Unicode tables, `STRASSE` included; and `unicodedata.normalize` is step 4 in one call. What Python does not have is the *language*: no locale reaches `str.upper()`, `sorted()` gives `strcmp`'s order, and nothing in the standard library counts the 6 characters. For those, Python programs install [PyICU ↗](https://pypi.org/project/PyICU/), a binding to this same library — the answer the Python library's [sorting is not comparing ↗](https://masiarek.github.io/python-learning-library/01_Text_and_Bytes/sorting_is_not_comparing/index.html) gives too. And the defaults point the other way: reading Latin-1 is silent in both, but `'café'.encode('ascii')` raises where ICU substituted.

## The calls on this page

| Job | Calls | Header | Library |
|---|---|---|---|
| UTF-8 to UTF-16 and back | `u_strFromUTF8`, `u_strToUTF8` | `ustring.h` | `libicuuc` |
| count code points | `u_countChar32` | `ustring.h` | `libicuuc` |
| character boundaries in UTF-8 | `utext_openUTF8`, `ubrk_open(UBRK_CHARACTER, …)`, `ubrk_setUText`, `ubrk_next` | `utext.h`, `ubrk.h` | `libicuuc` |
| upper, lower and fold UTF-8 | `ucasemap_open`, `ucasemap_utf8ToUpper`, `ucasemap_utf8ToLower`, `ucasemap_utf8FoldCase` | `ucasemap.h` | `libicuuc` |
| normalize | `unorm2_getNFCInstance` (and NFD, NFKC, NFKD), `unorm2_normalize` | `unorm2.h` | `libicuuc` |
| compare and sort UTF-8 | `ucol_open`, `ucol_strcollUTF8`, `ucol_setStrength`, `ucol_getSortKey` | `ucol.h` | `libicui18n` |
| code pages | `ucnv_convert`, `ucnv_open`, `ucnv_fromUChars`, `ucnv_setFromUCallBack` | `ucnv.h` | `libicuuc` |
| what went wrong | `U_SUCCESS`, `U_FAILURE`, `u_errorName` | `utypes.h` | `libicuuc` |

## If you are coming from another language

**Rust.** `std` stops where libc does, with the types right: `to_uppercase` has the full tables, and there is no locale, normalization, grapheme segmentation or collation. Crates fill the gap, and the ICU project's own answer is ICU4X, a Rust rewrite published as the [`icu` ↗](https://docs.rs/icu) crate; [`rust_icu` ↗](https://docs.rs/rust_icu) binds the C library on this page instead. The Rust library's [string crates ↗](https://masiarek.github.io/rust-learning-library/14_Strings/string_crates/index.html) and [comparing and sorting text ↗](https://masiarek.github.io/rust-learning-library/14_Strings/comparing_strings/index.html) cover both.

**Python.** The section above: normalization and the full case tables in the standard library, the language-dependent half in PyICU.

**Java.** `java.text.Normalizer`, `Collator` and `BreakIterator` are in the JDK, and ICU4J is the full library for Java. The Java text library's [case is locale-sensitive ↗](https://masiarek.github.io/java-text-learning-library/03_Locale/case_is_locale_sensitive/index.html) and [normalization and equality ↗](https://masiarek.github.io/java-text-learning-library/03_Locale/normalization_and_equality/index.html) are steps 3 and 4 in Java.

**ABAP.** *(Not machine-checked — CI cannot run ABAP.)* ABAP keeps this layer out of sight. `TRANSLATE … TO UPPER CASE` and `SORT … AS TEXT` take their language from the text environment (`SET LOCALE LANGUAGE`), and code pages go through `CL_ABAP_CONV_CODEPAGE`, so an ABAP program never opens a collator or resets a status. Underneath, the SAP kernel of a Unicode system does that work with ICU libraries it ships itself — the [SAP code pages ↗](https://masiarek.github.io/encodings-learning-library/07_Real_Data/sap_code_pages/index.html) page covers what reaches the application.

## See also

- [A `char` is a byte, not a character](../char_is_a_byte_not_a_character/README.md) — where libc runs out, and the reason this lesson exists
- [The functions that do not check](../the_functions_that_do_not_check/README.md) — `snprintf`'s preflight, the same idiom ICU uses for every buffer
- ["Handles Unicode" is four questions ↗](https://masiarek.github.io/encodings-learning-library/10_Best_Practices/what_your_language_gives_you/index.html) — ICU underneath Java, JavaScript, .NET and Swift, and what each language keeps for itself
- [Case is not a per-character operation ↗](https://masiarek.github.io/encodings-learning-library/02_Characters/case_is_not_per_character/index.html) — why step 3 needs whole strings
- [Normalization ↗](https://masiarek.github.io/python-learning-library/01_Text_and_Bytes/normalization/index.html) — step 4 in Python, with the four forms side by side
- [ICU User Guide ↗](https://unicode-org.github.io/icu/userguide/) and [ICU4C API reference ↗](https://unicode-org.github.io/icu-docs/apidoc/released/icu4c/) — the project's own documentation
