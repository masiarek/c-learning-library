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
