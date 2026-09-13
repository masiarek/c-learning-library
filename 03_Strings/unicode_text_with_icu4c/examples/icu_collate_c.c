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
