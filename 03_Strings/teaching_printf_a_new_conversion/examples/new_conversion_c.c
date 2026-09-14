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
