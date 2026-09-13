/* `char` is the machine's byte, not a letter. A string counts bytes; an
   accented letter costs more than one; `toupper` touches ASCII only; and the
   <ctype.h> functions must be handed a value that fits in unsigned char, or
   the call is undefined. The str/bytes split Python and Rust give you, C does
   not: there is only the byte. */
#include <stdio.h>
#include <string.h>
#include <ctype.h>
#include <limits.h>

int main(void)
{
    const char *s = "caf\xc3\xa9";           /* "cafe" + U+00E9, in UTF-8 */
    size_t len = strlen(s);

    printf("\"cafe-with-accent\": strlen = %zu bytes, a reader counts 4 letters\n", len);
    printf("bytes:            ");
    for (size_t i = 0; i < len; i++)
        printf(" %02x", (unsigned char)s[i]);
    printf("\n");

    /* toupper changes ASCII letters and passes every other byte through -- the
       two accent bytes are untouched, exactly like Python's bytes.upper() and
       Rust's to_ascii_uppercase(). */
    printf("toupper each byte:");
    for (size_t i = 0; i < len; i++)
        printf(" %02x", (unsigned char)toupper((unsigned char)s[i]));
    printf("\n");

    /* The first four bytes are "caf" and then the accent's lead byte, alone.
       C has no idea a character was cut in half: it hands back four bytes and
       asks nothing. Python's decode() and Rust's from_utf8() refuse this. */
    printf("first 4 bytes:    ");
    for (size_t i = 0; i < 4; i++)
        printf(" %02x", (unsigned char)s[i]);
    printf("   (ends inside the accent; C does not notice)\n");

    /* CHAR_BIT is fixed at 8; whether a plain char is signed is not, and it
       differs between this library's two machines. So a byte with the high bit
       set (0xc3) may reach <ctype.h> as a negative int -- undefined unless it
       is cast to unsigned char first, which is why every call above did. */
    printf("a char is %d bits; whether plain char is signed is implementation-defined\n",
           CHAR_BIT);
    unsigned char lead = (unsigned char)s[3];
    printf("byte 0x%02x asked through unsigned char: isalpha = %d, isprint = %d\n",
           lead, isalpha(lead) != 0, isprint(lead) != 0);
    return 0;
}
