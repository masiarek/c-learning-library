/* A Ghidra pattern is a value and a mask: a bit is fixed where the mask has a 1
   and a "dit" -- a don't-care, written '.' -- where it has a 0. Two operations
   are the whole arithmetic of the Function Bit Patterns Explorer: merge some
   byte sequences into a pattern, keeping a bit only where every sequence agrees,
   and match a pattern against bytes. Both are here, on the first bytes Ghidra
   gathered from the clang -O0 build on this page. */
#include <stdint.h>
#include <stdio.h>

enum { WIDTH = 8 };   /* the window this example works on: eight bytes */

struct pattern {
    uint8_t value[WIDTH];
    uint8_t mask[WIDTH];    /* 1 = fixed bit, 0 = dit */
};

/* Merge: a bit stays fixed only where every sequence has the same bit. */
static struct pattern merge(const uint8_t seqs[][WIDTH], int count)
{
    struct pattern p;
    for (int i = 0; i < WIDTH; i++) {
        uint8_t agree = 0xff;
        for (int s = 1; s < count; s++) {
            agree &= (uint8_t)~(seqs[0][i] ^ seqs[s][i]);   /* 1 where s agrees with sequence 0 */
        }
        p.mask[i] = agree;
        p.value[i] = seqs[0][i] & agree;
    }
    return p;
}

/* Print as Ghidra writes it: 0xNN for a byte with no dits, eight bit characters otherwise. */
static void print_pattern(const struct pattern *p)
{
    for (int i = 0; i < WIDTH; i++) {
        if (p->mask[i] == 0xff) {
            printf("0x%02x", p->value[i]);
        } else {
            for (int bit = 7; bit >= 0; bit--) {
                int fixed = (p->mask[i] >> bit) & 1;
                int value = (p->value[i] >> bit) & 1;
                putchar(fixed ? '0' + value : '.');
            }
        }
        putchar(i + 1 < WIDTH ? ' ' : '\n');
    }
}

static int fixed_bits(const struct pattern *p)
{
    int n = 0;
    for (int i = 0; i < WIDTH; i++) {
        for (int bit = 0; bit < 8; bit++) {
            n += (p->mask[i] >> bit) & 1;
        }
    }
    return n;
}

/* Match: every fixed bit must agree; a dit matches either bit. */
static int matches(const struct pattern *p, const uint8_t *bytes)
{
    for (int i = 0; i < WIDTH; i++) {
        if ((bytes[i] ^ p->value[i]) & p->mask[i]) {
            return 0;
        }
    }
    return 1;
}

int main(void)
{
    /* The first eight bytes of the four functions on the tree's PUSH:1 > MOV:3 > SUB:4 path
       in the clang -O0 build -- push rbp; mov rbp, rsp; sub rsp, N -- as Ghidra gathered them. */
    static const uint8_t frame[][WIDTH] = {
        { 0x55, 0x48, 0x89, 0xe5, 0x48, 0x83, 0xec, 0x60 },   /* greet_len: a 64-byte buffer */
        { 0x55, 0x48, 0x89, 0xe5, 0x48, 0x83, 0xec, 0x10 },   /* say */
        { 0x55, 0x48, 0x89, 0xe5, 0x48, 0x83, 0xec, 0x10 },   /* orphan */
        { 0x55, 0x48, 0x89, 0xe5, 0x48, 0x83, 0xec, 0x30 },   /* main */
    };
    struct pattern p = merge(frame, 4);
    printf("merged:  ");
    print_pattern(&p);
    printf("%d of %d bits fixed\n", fixed_bits(&p), 8 * WIDTH);

    /* Then hold it against bytes it has not seen. */
    static const struct {
        const char *what;
        uint8_t bytes[WIDTH];
    } probes[] = {
        { "sub rsp, 0x20 -- a frame none of the four had",  { 0x55, 0x48, 0x89, 0xe5, 0x48, 0x83, 0xec, 0x20 } },
        { "sub rsp, 0x28 -- not a multiple of 16",          { 0x55, 0x48, 0x89, 0xe5, 0x48, 0x83, 0xec, 0x28 } },
        { "twice, which sets up no frame at all",           { 0x55, 0x48, 0x89, 0xe5, 0x89, 0x7d, 0xfc, 0x8b } },
        { "the first eight bytes of \"nobody calls this\"", { 'n', 'o', 'b', 'o', 'd', 'y', ' ', 'c' } },
    };
    for (size_t i = 0; i < sizeof probes / sizeof probes[0]; i++) {
        printf("%-9s %s\n", matches(&p, probes[i].bytes) ? "match" : "no match", probes[i].what);
    }
    return 0;
}
