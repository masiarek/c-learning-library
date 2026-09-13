/* A file and a socket carry bytes; nothing else. "Text" is a promise about
   which bytes -- printable ones, and a newline you agree on -- and "binary" is
   the absence of that promise. The number 1000 shows the split: four bytes as
   text, four as a raw integer, and they share not one byte in common. */
#include <stdio.h>
#include <string.h>
#include <stdint.h>

static void dump(const char *label, const void *p, size_t n)
{
    const unsigned char *b = p;
    printf("%-28s", label);
    for (size_t i = 0; i < n; i++)
        printf(" %02x", b[i]);
    printf("\n");
}

int main(void)
{
    /* As text: the digits '1' '0' '0' '0', every byte in the printable range. */
    char text[16];
    int len = snprintf(text, sizeof text, "%d", 1000);
    printf("1000 as text \"%s\": %d bytes\n", text, len);
    dump("  bytes:", text, (size_t)len);

    /* As a 4-byte integer, big-endian (the order agreed on the wire). One of
       the bytes is 0x00 -- a NUL, which ends a C string and stops text tools. */
    uint32_t n = 1000;
    unsigned char raw[4] = { n >> 24, n >> 16, n >> 8, n };
    printf("1000 as a big-endian int32: 4 bytes\n");
    dump("  bytes:", raw, sizeof raw);
    printf("  a NUL (00) is in there: %s -- so this is not text\n",
           memchr(raw, 0, sizeof raw) ? "yes" : "no");

    /* A newline is one byte, 0x0a. That is the only structure a text file has;
       CR+LF is two bytes, and which one a line ends with is a convention, not
       a property of the file. */
    printf("'\\n' is one byte: %02x   \"\\r\\n\" is two: ", (unsigned char)'\n');
    dump("", "\r\n", 2);
    return 0;
}
