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
