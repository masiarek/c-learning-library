# The functions that do not check

**Level:** 201 → 301 · for anyone who has read one CVE

**One line:** `strcpy`, `strcat` and `sprintf` take a destination and a source and no length, so they write until the source's NUL — wherever that is, including past the end of the destination — and the functions written to replace them each fix part of the problem and leave a different sharp edge: `strncpy` may not terminate, `snprintf` and `strlcpy` truncate but you have to notice, and `fgets` stops at the buffer but hands the line back in pieces.

This is the family behind the phrase *buffer overflow*. The functions were not badly written; they were written for a machine where the programmer knew every string's length, and they take the length as a thing you already checked. When the source comes from a file, a network socket, or a person, that assumption is the vulnerability. Chromium's security team has put a number on where this leads: around 70% of their high-severity bugs are memory-unsafety problems, [measured across 912 high or critical bugs since 2015 ↗](https://www.chromium.org/Home/chromium-security/memory-safety/).

## The functions with no length

`strcpy(dst, src)` copies bytes from `src` until it reaches a `\0`, into `dst`, and writes exactly as many bytes as `src` is long plus one. It never looks at how big `dst` is, because it was never told. `strcat` is the same, starting at `dst`'s existing terminator. `sprintf(buf, fmt, ...)` formats into `buf` with no idea how big `buf` is. And `gets(buf)` reads a line from input into `buf` with no limit at all — it was so impossible to use safely that C11 removed it from the language, though the compilers still let you call it with a warning.

Running any of them past the end of a buffer is undefined behaviour, so this page does not do it — a program with no defined output has no answer key, and the Rust library is where that crash is shown, labelled as the undefined behaviour it is: [the write past the end ↗](https://masiarek.github.io/rust-learning-library/31_C_and_Cpp/buffer_overflow/index.html) that `strcpy` performs here, beside its quieter [read cousin ↗](https://masiarek.github.io/rust-learning-library/31_C_and_Cpp/buffer_overruns/index.html). What this page runs are the functions that were meant to replace the unchecked ones, so you can see exactly what each one does when the data does not fit.

## What replaced them, and what each still gets wrong

[`unchecked_copy_c.c`](examples/unchecked_copy_c.c) runs four replacements against a source that is too long for its six-byte buffer. The source strings are chosen through `argc` so the compiler cannot measure them at compile time — GCC's `-Wstringop-truncation` warns about exactly what this program is here to demonstrate at run time:

<!-- source:unchecked_copy_c -->
*[`unchecked_copy_c.c`](examples/unchecked_copy_c.c) in full — pasted here by `tools/run_examples.py` from the file CI runs.*

```c
/* strcpy, strcat and sprintf take no length and stop at the source's NUL,
   wherever that is. This program runs the functions that were meant to
   replace them, and shows what each one does when the source does not fit:
   strncpy leaves no NUL, snprintf truncates and reports, strlcpy does both.

   The source strings are picked through argc so the compiler cannot see their
   length: GCC's -Wstringop-truncation and -Wformat-truncation warn at compile
   time about exactly what this program is here to show at run time. */
#define _DEFAULT_SOURCE   /* glibc 2.38+ declares strlcpy only outside strict ISO mode */
#include <stdio.h>
#include <string.h>

static const char *const words[] = { "hello world", "hi" };

static void dump(const char *label, const void *p, size_t n)
{
    const unsigned char *b = p;
    printf("%-30s", label);
    for (size_t i = 0; i < n; i++) {
        printf(" %02x", b[i]);
    }
    printf("\n");
}

int main(int argc, char **argv)
{
    (void)argv;
    const char *longer = words[argc - 1];       /* "hello world", 11 bytes */
    const char *shorter = words[argc];          /* "hi", 2 bytes */
    char buf[6];

    /* 1. strncpy: copies at most n bytes and stops. If the source was longer,
       the NUL is not among them -- buf is now not a string. */
    printf("strncpy(buf, \"%s\", 6)\n", longer);
    strncpy(buf, longer, sizeof buf);
    dump("  bytes:", buf, sizeof buf);
    printf("  a NUL among them: %s\n", memchr(buf, '\0', sizeof buf) ? "yes" : "no");

    /* 2. strncpy the other way: a short source, and every byte after it is
       zero-filled -- it was written to lay out fixed-width fields, not to
       copy strings safely. */
    printf("strncpy(buf, \"%s\", 6)\n", shorter);
    strncpy(buf, shorter, sizeof buf);
    dump("  bytes:", buf, sizeof buf);

    /* 3. snprintf: never writes past n, always terminates, and returns the
       length the whole output would have had -- so the caller can tell. */
    printf("snprintf(buf, 6, \"%%s\", \"%s\")\n", longer);
    int need = snprintf(buf, sizeof buf, "%s", longer);
    dump("  bytes:", buf, sizeof buf);
    printf("  returned %d, buffer holds \"%s\"; truncated: %s\n",
           need, buf, need >= (int)sizeof buf ? "yes" : "no");

    /* 4. strlcpy (BSD 1998, glibc 2.38, POSIX 2024): the same contract with
       the source length as the return value. */
    printf("strlcpy(buf, 6, \"%s\")\n", longer);
    size_t src_len = strlcpy(buf, longer, sizeof buf);
    dump("  bytes:", buf, sizeof buf);
    printf("  returned %zu, buffer holds \"%s\"; truncated: %s\n",
           src_len, buf, src_len >= sizeof buf ? "yes" : "no");

    /* 5. memcpy with a length you computed is the honest primitive: it does
       exactly what it is told, and the check is yours to write. */
    size_t n = strlen(longer);
    if (n >= sizeof buf) {
        n = sizeof buf - 1;
    }
    memcpy(buf, longer, n);
    buf[n] = '\0';
    printf("memcpy of min(strlen, 5) bytes, then a NUL: \"%s\"\n", buf);
    return 0;
}
```
<!-- /source -->

<!-- output:unchecked_copy_c -->
*Verified output of [`unchecked_copy_c.c`](examples/unchecked_copy_c.c) — regenerated by `tools/run_examples.py`, never hand-typed.*

```text
strncpy(buf, "hello world", 6)
  bytes:                       68 65 6c 6c 6f 20
  a NUL among them: no
strncpy(buf, "hi", 6)
  bytes:                       68 69 00 00 00 00
snprintf(buf, 6, "%s", "hello world")
  bytes:                       68 65 6c 6c 6f 00
  returned 11, buffer holds "hello"; truncated: yes
strlcpy(buf, 6, "hello world")
  bytes:                       68 65 6c 6c 6f 00
  returned 11, buffer holds "hello"; truncated: yes
memcpy of min(strlen, 5) bytes, then a NUL: "hello"
```
<!-- /output -->

**`strncpy` takes a length but may not terminate.** Given a source longer than the buffer, it copies exactly `n` bytes and stops — and the terminating `\0` was not among them. The buffer is now full of characters and is *not a string*: the next `strlen` or `printf("%s")` runs off the end. `strncpy` was written to fill fixed-width fields, not to copy strings safely, which is why the short-source case zero-fills the rest of the buffer. If you use it, you write the terminator yourself.

**`snprintf` truncates and tells you.** It never writes past the buffer, always terminates, and returns the length the whole output *would* have had — 11 here, which is greater than the buffer, so the caller can see that truncation happened. That return value is the check; ignoring it is how truncation becomes a silent bug rather than a handled one.

**`strlcpy` is the same contract, stated more plainly.** From BSD in 1998, glibc since 2.38, and POSIX since 2024, it always terminates and returns the source length so you can compare it to the buffer size. It needs `#define _DEFAULT_SOURCE` on glibc to be declared outside strict ISO mode, which is the line at the top of the program.

**`memcpy` with a length you computed is the honest primitive.** It does exactly what it is told and no more; the bound is yours to write, and once you have written it — `min(strlen, size - 1)`, then a terminator — the result is a correct string and nothing was undefined. Everything above is a convenience over this, and each convenience hides a different part of the bookkeeping.

## Reading a line without a length

The same problem in its most common form: reading input. `gets` had no limit; `fgets` takes the buffer size and stops one byte short of it, always terminates, and keeps the newline *when the line fitted* — which is how the caller tells "the line ended" from "the buffer filled and there is more". [`demo/read_line.c`](demo/read_line.c):

```c
/* Read standard input with fgets into a buffer eight bytes long, and report
   each call: how many bytes arrived, and whether they ended the line. fgets
   stops at the buffer's size minus one, always writes the NUL, and keeps the
   newline when the line fitted -- which is how a caller can tell "the line
   ended" from "the buffer filled". */
#include <stdio.h>
#include <string.h>

int main(void)
{
    char buf[8];
    while (fgets(buf, sizeof buf, stdin) != NULL) {
        size_t n = strlen(buf);
        int complete = n > 0 && buf[n - 1] == '\n';
        if (complete) {
            buf[n - 1] = '\0';
        }
        printf("fgets: %zu byte(s) \"%s\" -- %s\n", n, buf,
               complete ? "line complete" : "buffer full, line continues");
    }
    return 0;
}
```

[`fgets_line_sh.sh`](examples/fgets_line_sh.sh) builds it and feeds it lines shorter and longer than the buffer:

<!-- output:fgets_line_sh -->
*Verified output of [`fgets_line_sh.sh`](examples/fgets_line_sh.sh) — regenerated by `tools/run_examples.py`, never hand-typed.*

```text
$ cc -std=c17 -Wall -Wextra -pedantic -o read_line read_line.c
$ printf 'hi\n' | ./read_line
fgets: 3 byte(s) "hi" -- line complete
$ printf 'hello world\n' | ./read_line
fgets: 7 byte(s) "hello w" -- buffer full, line continues
fgets: 5 byte(s) "orld" -- line complete
$ printf 'no newline at all' | ./read_line
fgets: 7 byte(s) "no newl" -- buffer full, line continues
fgets: 7 byte(s) "ine at " -- buffer full, line continues
fgets: 3 byte(s) "all" -- buffer full, line continues
$ printf 'ab\ncd\n' | ./read_line
fgets: 3 byte(s) "ab" -- line complete
fgets: 3 byte(s) "cd" -- line complete
```
<!-- /output -->

A line that fits arrives once, newline included. A line longer than seven bytes arrives in seven-byte pieces, and only the piece with the newline is a complete line. `fgets` never writes past the buffer, so the cost of a fixed buffer is not a crash — it is that you have to be willing to read a line in pieces.

## The same job in Rust

Rust has no `strcpy`, and a copy into a fixed buffer must state its length; a length that does not match is a panic at the copy, not a write past the end. [`unchecked_copy_rs.rs`](examples/unchecked_copy_rs.rs):

<!-- source:unchecked_copy_rs -->
*[`unchecked_copy_rs.rs`](examples/unchecked_copy_rs.rs) in full — pasted here by `tools/run_examples.py` from the file CI runs.*

```rust
// Rust has no strcpy. A copy into a fixed buffer must state its length, and
// a length that does not match is a panic at the copy, not a write past the
// end. The panic hook is silenced so the program's own lines are its output.
fn main() {
    let longer = b"hello world";
    let mut buf = [0u8; 6];

    // The checked form: take as much as fits, and know that you did.
    let n = longer.len().min(buf.len() - 1);
    buf[..n].copy_from_slice(&longer[..n]);
    println!("copied {n} of {} bytes: {:?}", longer.len(), std::str::from_utf8(&buf[..n]).unwrap());

    // The unchecked form does not exist: copy_from_slice insists on equal lengths.
    std::panic::set_hook(Box::new(|_| {}));
    let outcome = std::panic::catch_unwind(|| {
        let mut b = [0u8; 6];
        b.copy_from_slice(longer);
        b
    });
    println!("buf.copy_from_slice(11 bytes into 6): {}",
             if outcome.is_err() { "panicked -- nothing was written" } else { "ok" });

    // Growing strings simply grow: String::push_str is the strcat that cannot overflow.
    let mut s = String::from("hello");
    s.push_str(" world");
    println!("push_str: len {} capacity {}", s.len(), s.capacity() >= s.len());
}
```
<!-- /source -->

<!-- output:unchecked_copy_rs -->
*Verified output of [`unchecked_copy_rs.rs`](examples/unchecked_copy_rs.rs) — regenerated by `tools/run_examples.py`, never hand-typed.*

```text
copied 5 of 11 bytes: "hello"
buf.copy_from_slice(11 bytes into 6): panicked -- nothing was written
push_str: len 11 capacity true
```
<!-- /output -->

`copy_from_slice` insists the two slices are the same length and panics otherwise — the unchecked form simply does not exist. And `String::push_str` is the `strcat` that cannot overflow, because the string owns its buffer and grows it. The bound C asks you to remember is, in Rust, the only thing the API will let you express.

## If you are coming from another language

**Python.** A `str` and a `bytes` own their storage and grow as needed; there is no destination buffer to overrun, so the whole family of bugs is absent. The cost moved elsewhere — to encoding and decoding at the edges, which is the [Python text chapter's ↗](https://masiarek.github.io/python-learning-library/01_Text_and_Bytes/index.html) subject.

**ABAP.** *(Not machine-checked — CI cannot run ABAP.)* ABAP strings grow on their own and there is no terminator and no fixed destination to overflow. Fixed-length `c` fields exist and truncate or pad rather than overrun, and the interesting failure there is silent truncation at a field width measured in the wrong unit — the [fixed-width byte fields ↗](https://masiarek.github.io/encodings-learning-library/07_Real_Data/fixed_width_byte_fields/index.html) problem, not this one.

## See also

- [A string is bytes up to a NUL](../a_string_is_bytes_up_to_a_nul/README.md) — the terminator these functions trust, and what a missing one means
- [A format string is a program](../a_format_string_is_a_program/README.md) — `sprintf`'s other hazard, where the format itself is the attack
- [A length you did not check](../../05_Bytes_on_the_Wire/a_length_you_did_not_check/README.md) — the same missing check one layer out, on a length that came off the wire
- [The bugs Rust is a reply to ↗](https://masiarek.github.io/rust-learning-library/31_C_and_Cpp/index.html) — nine of these run as real programs, each beside the Rust that will not build it
- [Buffer overflow ↗](https://masiarek.github.io/rust-learning-library/31_C_and_Cpp/buffer_overflow/index.html) — the write this page declines to run: `strcpy` past the end, shown and labelled as the undefined behaviour it is
- [Buffer overruns ↗](https://masiarek.github.io/rust-learning-library/31_C_and_Cpp/buffer_overruns/index.html) — its read cousin, an off-by-one that returns a wrong number instead of smashing the frame
- [C or Rust for text ↗](https://masiarek.github.io/encodings-learning-library/10_Best_Practices/c_or_rust_for_text/index.html) — when the unchecked edge is the right tool, and when it is not
