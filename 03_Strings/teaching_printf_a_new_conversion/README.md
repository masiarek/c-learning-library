# Teaching `printf` a new conversion

**Level:** 301 · deep dive

**One line:** `printf`'s conversions are a table inside the C library, not part of the C language, and both libraries CI runs on let a program add a row — a `%W` drawn by a function of yours, which is handed the format's `#`, `-`, width and precision — but glibc adds it to every `printf` in the program, while Apple's libc adds it only to the `xprintf` calls handed a *printf domain*, documented in `xprintf(5)`.

[A format string is a program](../a_format_string_is_a_program/README.md) showed that `printf`'s first argument is a small language the library runs. Its words — `%d`, `%s`, `%n` — are not built into C. They are a table the C library keeps, and the two libraries this library is tested against both let a program add a word to it. This page adds `%W`, which prints a coordinate, on both, and then deals with what stops most people before they start: opening the manual page that explains it.

None of this is standard C. A portable program writes a `coordinate_to_string` function and prints its result with `%s`. The extension is worth seeing anyway, because it shows what a conversion *is* — two callbacks and a struct of flags — and because Apple's version of it comes with an argument about global state that reaches well beyond `printf`.

## A conversion is two functions

[`new_conversion_c.c`](examples/new_conversion_c.c) registers `%W` and prints one coordinate six ways, then once beside standard conversions:

<!-- source:new_conversion_c -->
*[`new_conversion_c.c`](examples/new_conversion_c.c) in full — pasted here by `tools/run_examples.py` from the file CI runs.*

```c
/* printf can be taught a conversion it does not have. Both C libraries CI
   runs on let a program register a function for a conversion character --
   here %W, which prints a struct coordinate -- and that function is handed
   the flags the format carried: '#', '-', the width and the precision.

   None of this is standard C, and the two libraries spell it differently.
   glibc's register_printf_specifier adds %W to every printf in the program.
   Apple's libc keeps the change inside a "printf domain" (see xprintf(5)):
   only the xprintf calls handed that domain know %W, and plain printf never
   does. The renderer, and everything this program prints, is the same on
   both. */
#include <printf.h>
#include <stdarg.h>
#include <stdio.h>

struct coordinate {
    double x;
    double y;
};

/* The renderer: called once per %W with the flags parsed out of the format
   and a pointer to the argument. It writes to the stream and returns the
   number of bytes it wrote, as printf does. */
static int render_coordinate(FILE *stream, const struct printf_info *info,
                             const void *const *args)
{
    const struct coordinate *c = *(const struct coordinate *const *)args[0];
    int width = info->left ? -info->width : info->width;
    int prec = info->prec >= 0 ? info->prec : 6;

    if (info->alt)
        return fprintf(stream, "(%*.*f, %*.*f)", width, prec, c->x, width, prec, c->y);
    return fprintf(stream, "%*.*f %*.*f", width, prec, c->x, width, prec, c->y);
}

/* The arginfo function: says how many arguments %W takes, and of what kind,
   so the library can pull them off the argument list before rendering.
   glibc's version has one more parameter, for a user-defined type's size. */
#ifdef __APPLE__
static int coordinate_arginfo(const struct printf_info *info, size_t n, int *argtypes)
#else
static int coordinate_arginfo(const struct printf_info *info, size_t n, int *argtypes, int *size)
#endif
{
    (void)info;
#ifndef __APPLE__
    (void)size;
#endif
    if (n > 0)
        argtypes[0] = PA_POINTER;
    return 1;
}

#ifdef __APPLE__
static printf_domain_t domain;
#endif

static int teach_printf_W(void)
{
#ifdef __APPLE__
    domain = new_printf_domain();
    if (domain == NULL)
        return -1;
    return register_printf_domain_function(domain, 'W', render_coordinate,
                                           coordinate_arginfo, NULL);
#else
    return register_printf_specifier('W', render_coordinate, coordinate_arginfo);
#endif
}

/* One printing call for both libraries. Taking the format as a parameter also
   keeps %W away from the compiler's format check, which knows only the
   standard conversions and would warn that W is not one of them. */
static int print_w(const char *format, ...)
{
    va_list ap;
    va_start(ap, format);
#ifdef __APPLE__
    int n = vxprintf(domain, NULL, format, ap);
#else
    int n = vprintf(format, ap);
#endif
    va_end(ap);
    return n;
}

int main(void)
{
    struct coordinate c = {12345.6789, 3.141593};

    if (teach_printf_W() != 0) {
        fputs("could not register %W\n", stderr);
        return 1;
    }

    /* The six conversions from the EXAMPLE section of xprintf(5), without
       its ' flag: the renderer applies the width and precision to each
       number, and '#' switches to the parenthesised form. */
    const char *specs[] = {"%W", "%14W", "%-14.2W", "%#W", "%#14W", "%#-14.2W"};
    for (size_t i = 0; i < sizeof specs / sizeof specs[0]; i++) {
        char format[32];
        snprintf(format, sizeof format, "|%s|\n", specs[i]);
        printf("%-10s ", specs[i]);
        fflush(stdout);
        print_w(format, &c);
    }

    /* %W sits in one format beside the standard conversions, and the count
       printf returns includes the bytes the renderer wrote. */
    int n = print_w("point %d is %#.1W, %s\n", 1, &c, "done");
    printf("that call returned %d\n", n);

#ifdef __APPLE__
    free_printf_domain(domain);
#endif
    return 0;
}
```
<!-- /source -->

<!-- output:new_conversion_c -->
*Verified output of [`new_conversion_c.c`](examples/new_conversion_c.c) — regenerated by `tools/run_examples.py`, never hand-typed.*

```text
%W         |12345.678900 3.141593|
%14W       |  12345.678900       3.141593|
%-14.2W    |12345.68       3.14          |
%#W        |(12345.678900, 3.141593)|
%#14W      |(  12345.678900,       3.141593)|
%#-14.2W   |(12345.68      , 3.14          )|
point 1 is (12345.7, 3.1), done
that call returned 32
```
<!-- /output -->

To run it yourself, from this lesson's `examples/` folder:

```bash
cc -std=c17 -Wall -Wextra -pedantic new_conversion_c.c -o new_conversion_c
./new_conversion_c
```

Registering a conversion hands the library two functions, and `xprintf(5)` describes the three phases they are called in. First the format is parsed, and each conversion's flags land in a `struct printf_info`: `alt` for `#`, `left` for `-`, `width`, and `prec`, which is `-1` when the format gave no precision. Then the **arginfo** function is asked how many arguments the conversion consumes and of what kind — here one, a `PA_POINTER` — so the library can take them off the variable argument list in order. Then the **renderer** is called with the same flags and a pointer to each argument; it writes to the stream and returns how many bytes it wrote. That count is folded into `printf`'s own return value, which is why the last call returns 32: eleven bytes of `point 1 is `, fourteen from the renderer, and seven after it.

The six conversions are the ones in the EXAMPLE section of `xprintf(5)` without its `'` flag, and the six lines are the first six lines of output that page shows, here checked by CI on both machines. The width and precision apply to each number rather than to the pair only because the renderer passes them on to its own `fprintf`. A conversion's flags mean whatever its renderer decides they mean.

The `print_w` wrapper is there for more than portability. Both compilers check a literal format against the standard conversions — the check [the previous lesson](../a_format_string_is_a_program/README.md) turned into a build error — and neither knows about `%W`, registered or not:

```text title="Real output — a literal format using %W passed straight to printf, built with -std=c17 -Wall -Wextra -pedantic"
GCC 13.3, ubuntu:24.04:
direct.c:13:15: warning: unknown conversion type character 'W' in format [-Wformat=]
direct.c:13:12: warning: too many arguments for format [-Wformat-extra-args]

Apple clang 21, macOS 26.6.2 x86-64:
direct.c:9:15: warning: invalid conversion specifier 'W' [-Wformat-invalid-specifier]
```

Handing the format to a function as a parameter takes it out of the compiler's sight, and on a Mac so does `xprintf` itself: clang said nothing about an `xprintf(d, NULL, "|%W|\n", &c)` on the line above that `printf`, because `<printf.h>` does not declare `xprintf` as a format function. A format language you have extended is one the compiler cannot read for you. That is the price.

## Program-wide, or inside a domain

The two libraries agree on the renderer — the `printf_function` type and every field of `struct printf_info` the example reads are the same on both — and disagree about who else gets the new conversion:

| | glibc — the Ubuntu runner | Apple's libc — the macOS runner |
|---|---|---|
| registering `%W` | `register_printf_specifier('W', render, arginfo)` | `register_printf_domain_function(domain, 'W', render, arginfo, context)` |
| who sees `%W` afterwards | every `printf`, `fprintf`, `snprintf` … in the process | only the `xprintf`, `fxprintf`, `sxprintf` … calls handed `domain` |
| the arginfo function | `(info, n, argtypes, size)` | `(info, n, argtypes)` |
| data for the renderer | no parameter for it | `context`, read back as `info->context` |
| the man page | `printf.h(3head)`, from the Linux man-pages project | `xprintf(5)`, with the calls in `xprintf(3)` and `xprintf_domain(3)` |

On glibc, *every* means every: a test beside this page registered `%W` and then called plain `snprintf(buf, sizeof buf, "<%W>", &c)`, and in `ubuntu:24.04` it wrote `<1.500000 2.500000>`. A library you link, or another thread, gets your `%W` too — and can register its own over it. `xprintf(5)` calls that unsafe, because *frameworks, libraries or some other thread could change printf behavior*.

Apple's fix is the one this library keeps arriving at from other directions: state that one part of a program changes and every other part reads is state nobody can reason about locally, so make it a value instead. A domain is created, passed and freed — `new_printf_domain`, `xprintf(domain, …)`, `free_printf_domain` — and the only calls that can see `%W` are the ones holding it. Apple answered `setlocale` the same way, with the per-call `locale_t` of `xlocale(3)`; that is the `NULL` passed as `xprintf`'s second argument here, meaning "the current locale".

## Opening the page

`apropos printf` on a Mac lists the page among dozens of others, in this notation:

```text title="Real output — two lines of apropos printf, macOS 26.6.2"
asxprintf(3), dxprintf(3), fxprintf(3), sxprintf(3), xprintf(3), vasxprintf(3), vdxprintf(3), vfxprintf(3), vsxprintf(3), vxprintf(3) - extensible printf
xprintf(5)               - extensible printf
```

`xprintf(5)` is how a manual *writes* a reference — the name, then in parentheses the section of the manual it is in. Typed back at a shell, it never reaches `man`:

```text title="Real output — man xprintf(5) at an interactive fish 4.3.2 prompt, macOS 26.6.2; exit status 127"
fish: Unknown command: 5
in command substitution
fish: Unknown command
man xprintf(5)
           ^~^
```

```text title="Real output — the same line through zsh -c (zsh 5.9, exit status 1) and /bin/bash -c (bash 3.2.57, exit status 2), macOS 26.6.2"
zsh:1: unknown file attribute: 5
/bin/bash: -c: line 0: syntax error near unexpected token `('
/bin/bash: -c: line 0: `man xprintf(5)'
```

```text title="Real output — the same line through bash -c (bash 5.2.21) and sh -c (dash), ubuntu:24.04; exit status 2 from both"
bash: -c: line 1: syntax error near unexpected token `('
bash: -c: line 1: `man xprintf(5)'
sh: 1: Syntax error: "(" unexpected
```

Every shell stops at the parenthesis, each for its own reason, and none of the messages is from `man`:

- **fish** reads `(…)` as a command substitution: it tries to run `5` as a command, fails, and points at the parentheses.
- **zsh** reads `(…)` at the end of a word as *glob qualifiers* — flags that filter which files a pattern matches — and it has no qualifier `5`.
- **bash** and **dash** follow the POSIX shell grammar, in which `(` is an operator that cannot appear there, so the whole line is a syntax error before any of it runs.

Quoting gets the characters through, and then it depends on which `man` receives them:

```text title="Real output — man -w with a quoted name(section), 2026-09-13"
macOS 26.6.2, /usr/bin/man:
$ man -w 'printf(3)'
No manual entry for printf(3)

ubuntu:24.04, man-db 2.12.0:
$ man -w 'printf(3)'
/usr/share/man/man3/printf.3.gz
```

The spelling every `man` accepts, in every shell, puts the section first as a word of its own:

```bash
man 5 xprintf          # the idea: domains, the two callbacks, the three phases, the example
man 3 xprintf          # the calls: xprintf, fxprintf, sxprintf, asxprintf and their v forms
man 3 xprintf_domain   # new_printf_domain, register_printf_domain_function, free_printf_domain
```

Leave the section out and `man` opens the first page it finds in its search order, which on this Mac is `1:8:2:3:3lua:n:4:5:6:7:9:l`, set in `/usr/bin/man`. Section 3 comes before section 5, so a bare `man xprintf` opens `xprintf(3)` — the list of calls, not the page that explains them. `man -a -w` prints every match, in that order:

```text title="Real output — man -a -w xprintf, macOS 26.6.2"
/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/usr/share/man/man3/xprintf.3
/usr/share/man/man5/xprintf.5
/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/usr/share/man/man5/xprintf.5
```

On Linux there is no `xprintf` page, because the domains are Apple's. glibc's interface is on `printf.h(3head)`, which `man register_printf_specifier` also finds; the fuller account is the glibc manual's [Customizing Printf ↗](https://sourceware.org/glibc/manual/latest/html_node/Customizing-Printf.html).

## The same idea in Python

Python has no table to add to. Everything after the colon in an f-string is handed to the value's own `__format__`, so [`new_conversion_py.py`](examples/new_conversion_py.py) gives the coordinate one:

<!-- source:new_conversion_py -->
*[`new_conversion_py.py`](examples/new_conversion_py.py) in full — pasted here by `tools/run_examples.py` from the file CI runs.*

```python
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
```
<!-- /source -->

<!-- output:new_conversion_py -->
*Verified output of [`new_conversion_py.py`](examples/new_conversion_py.py) — regenerated by `tools/run_examples.py`, never hand-typed.*

```text
{}         |12345.678900 3.141593|
{:14}      |  12345.678900       3.141593|
{:-14.2}   |12345.68       3.14          |
{:#}       |(12345.678900, 3.141593)|
{:#14}     |(  12345.678900,       3.141593)|
{:#-14.2}  |(12345.68      , 3.14          )|
point 1 is (12345.7, 3.1), done
point 1 is (12345.7, 3.1), done
ValueError: bad Coordinate format 'x'
```
<!-- /output -->

The flags are the type's to interpret, and nothing outside the type changes, so there is nothing to keep in a domain. The `-` here means *left-align* only because this class says so; in a `float`'s spec the same character means "a sign for negative numbers only". And a spec the type does not understand is a `ValueError` raised by the type's own code, where in C a conversion the library does not know is undefined behaviour.

## The same idea in Rust

Rust's [`Formatter` ↗](https://doc.rust-lang.org/std/fmt/struct.Formatter.html) is `struct printf_info` with methods, and [`new_conversion_rs.rs`](examples/new_conversion_rs.rs) reads the same four flags from it in a `Display` impl:

<!-- source:new_conversion_rs -->
*[`new_conversion_rs.rs`](examples/new_conversion_rs.rs) in full — pasted here by `tools/run_examples.py` from the file CI runs.*

```rust
// Rust has no table of conversions either: `{}` calls the value's Display
// impl, and the Formatter it is handed carries the flags from the braces --
// `#` as alternate(), a width, a precision, and `<` for left alignment, which
// Rust spells where printf spells `-`. The impl lives with the type, and the
// compiler checks every format string against it.
use std::fmt;

struct Coordinate {
    x: f64,
    y: f64,
}

impl fmt::Display for Coordinate {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        let width = f.width().unwrap_or(0);
        let prec = f.precision().unwrap_or(6);
        let left = matches!(f.align(), Some(fmt::Alignment::Left));
        let number = |v: f64| {
            if left {
                format!("{v:<width$.prec$}")
            } else {
                format!("{v:>width$.prec$}")
            }
        };
        if f.alternate() {
            write!(f, "({}, {})", number(self.x), number(self.y))
        } else {
            write!(f, "{} {}", number(self.x), number(self.y))
        }
    }
}

fn main() {
    let c = Coordinate { x: 12345.6789, y: 3.141593 };

    // The same six as the C program, with the flags where Rust puts them.
    println!("{:10} |{}|", "{}", c);
    println!("{:10} |{:14}|", "{:14}", c);
    println!("{:10} |{:<14.2}|", "{:<14.2}", c);
    println!("{:10} |{:#}|", "{:#}", c);
    println!("{:10} |{:#14}|", "{:#14}", c);
    println!("{:10} |{:<#14.2}|", "{:<#14.2}", c);

    // Beside the standard conversions in one format string.
    println!("point {} is {:#.1}, {}", 1, c, "done");
}
```
<!-- /source -->

<!-- output:new_conversion_rs -->
*Verified output of [`new_conversion_rs.rs`](examples/new_conversion_rs.rs) — regenerated by `tools/run_examples.py`, never hand-typed.*

```text
{}         |12345.678900 3.141593|
{:14}      |  12345.678900       3.141593|
{:<14.2}   |12345.68       3.14          |
{:#}       |(12345.678900, 3.141593)|
{:#14}     |(  12345.678900,       3.141593)|
{:<#14.2}  |(12345.68      , 3.14          )|
point 1 is (12345.7, 3.1), done
```
<!-- /output -->

`alternate()` is `#`, `width()` and `precision()` are what they say, and `align()` returning `Left` does the work of printf's `-`. As in Python the behaviour belongs to the type, so nothing is registered and nothing global changes — and, unlike C, every format string that prints a `Coordinate` is checked when it is compiled, because all `{}` asks of a type is that it implements `Display`.

## If you are coming from another language

**ABAP.** *(Not machine-checked — CI cannot run ABAP.)* String templates have a fixed set of formatting options — `WIDTH`, `ALIGN`, `DECIMALS` and the rest — and no way to add one. The nearest thing to a registered conversion is a *conversion routine*: a pair of function modules, `CONVERSION_EXIT_<name>_INPUT` and `CONVERSION_EXIT_<name>_OUTPUT`, attached to a domain in the ABAP Dictionary and run by `WRITE` on any field of that domain. Like Apple's printf domain it is scoped: it changes how fields of that domain are shown, not how every output statement behaves.

## See also

- [A format string is a program](../a_format_string_is_a_program/README.md) — the directive language this page adds a word to, and why a format the user controls is dangerous
- [The functions that do not check](../the_functions_that_do_not_check/README.md) — `snprintf`, which `sxprintf` behaves like
- [`printf(3)`, `printf(1)` and `echo(1)`, annotated ↗](https://masiarek.github.io/encodings-learning-library/13_Documentation/manual_pages/printf.html) — the standard conversions read line by line: width and precision count bytes, and `%ls` meets the locale
- [The encoding man pages nobody opens ↗](https://masiarek.github.io/encodings-learning-library/13_Documentation/the_encoding_man_pages/index.html) — what the sections of the manual are for, and why an encoding is a section 5 file format on a Mac
- [`locale(1)`, `setlocale(3)` and `xlocale(3)`, annotated ↗](https://masiarek.github.io/encodings-learning-library/13_Documentation/manual_pages/locale.html) — the `locale_t` that `xprintf` takes as its second argument
- [The format mini-language ↗](https://masiarek.github.io/rust-learning-library/14_Strings/the_format_language/index.html) — Rust's `{:>8.3}` grammar, and the surprise that a type may ignore any of it
- [The format mini-language ↗](https://masiarek.github.io/python-learning-library/01_Text_and_Bytes/the_format_mini_language/index.html) — Python's one grammar behind f-strings, `str.format`, `format()` and `__format__`
