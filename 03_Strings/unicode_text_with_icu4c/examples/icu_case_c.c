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
