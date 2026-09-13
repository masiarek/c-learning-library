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
