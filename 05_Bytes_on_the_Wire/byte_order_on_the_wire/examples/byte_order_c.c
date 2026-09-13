/* A value wider than one byte has to be written in some order, and the machine
   picks one you did not choose. The wire picks the other kind of choice: one
   fixed order both ends agree on, "network byte order", which is big-endian.
   Serialising with shifts gives that order on every machine -- which is the
   point of doing it with shifts rather than by copying the integer's memory. */
#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <arpa/inet.h>

static void dump(const char *label, const void *p, size_t n)
{
    const unsigned char *b = p;
    printf("%-34s", label);
    for (size_t i = 0; i < n; i++)
        printf(" %02x", b[i]);
    printf("\n");
}

int main(void)
{
    uint32_t v = 0x01020304u;

    /* Big-endian, by shifting -- most significant byte first. Same four bytes
       on a little-endian machine, a big-endian one, anywhere. */
    unsigned char be[4] = { v >> 24, v >> 16, v >> 8, v };
    dump("0x01020304 big-endian (shifts):", be, 4);

    /* Little-endian, the same way. */
    unsigned char le[4] = { v, v >> 8, v >> 16, v >> 24 };
    dump("0x01020304 little-endian (shifts):", le, 4);

    /* htonl converts host order to network (big-endian) order. Dumped as bytes
       the result is big-endian whatever the host is -- so this line is the
       same on both machines, while the raw memory of `v` would not be. */
    uint32_t net = htonl(v);
    dump("htonl(0x01020304), as bytes:", &net, 4);

    /* Read it back with shifts: no endianness assumption on the way in. */
    uint32_t back = (uint32_t)be[0] << 24 | (uint32_t)be[1] << 16
                  | (uint32_t)be[2] << 8  | be[3];
    printf("read back from big-endian bytes: 0x%08x, round trip ok: %s\n",
           back, back == v ? "yes" : "no");
    return 0;
}
