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
