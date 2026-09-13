/* A length-prefixed frame is two decisions: how many bytes the length field
   is, and -- the one everyone forgets -- whether to believe it. The length
   comes from the other end, which may be an attacker. A reader that trusts it
   copies as many bytes as the sender claims, out of a buffer that holds far
   fewer. The fix is one comparison: the claimed length against what actually
   arrived, and against a ceiling of your own. */
#include <stdio.h>
#include <stdint.h>
#include <string.h>

#define MAX_PAYLOAD 1024

/* A frame is [len: 2 bytes big-endian][payload: len bytes]. Return the payload
   length on success, or -1 with a reason, having copied nothing on failure. */
static long read_frame(const unsigned char *buf, size_t available,
                       unsigned char *out, size_t out_cap, const char **why)
{
    if (available < 2) { *why = "not even a length field arrived"; return -1; }
    uint16_t claimed = (uint16_t)(buf[0] << 8 | buf[1]);

    if (claimed > out_cap)          { *why = "claimed length exceeds our buffer"; return -1; }
    if (2u + claimed > available)   { *why = "claimed length runs past the bytes we have"; return -1; }

    memcpy(out, buf + 2, claimed);   /* now provably safe */
    *why = NULL;
    return claimed;
}

static void try_frame(const char *name, const unsigned char *buf, size_t available)
{
    unsigned char out[MAX_PAYLOAD];
    const char *why;
    long n = read_frame(buf, available, out, sizeof out, &why);
    if (n < 0)
        printf("%-24s rejected: %s\n", name, why);
    else
        printf("%-24s accepted: %ld-byte payload \"%.*s\"\n", name, n, (int)n, out);
}

int main(void)
{
    /* A good frame: length 5, then five bytes. */
    unsigned char good[] = { 0x00, 0x05, 'h','e','l','l','o' };
    try_frame("length 5, 5 present:", good, sizeof good);

    /* The hostile one: the field claims 60000 bytes; three arrived. A trusting
       reader would memcpy 60000 bytes out of this 5-byte array. */
    unsigned char lie[] = { 0xea, 0x60, 'h','i' };
    uint16_t claimed = (uint16_t)(lie[0] << 8 | lie[1]);
    printf("hostile frame claims %u bytes; %zu arrived\n", claimed, sizeof lie - 2);
    printf("  a trusting memcpy would read %u bytes from a %zu-byte buffer\n",
           claimed, sizeof lie);
    try_frame("claims 60000, 2 present:", lie, sizeof lie);

    /* Truncated: a length field promising more than a header's worth, cut off
       before its payload. */
    unsigned char cut[] = { 0x00, 0x08, 'a','b','c' };
    try_frame("length 8, 3 present:", cut, sizeof cut);
    return 0;
}
