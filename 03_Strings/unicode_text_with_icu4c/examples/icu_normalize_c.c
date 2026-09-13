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
