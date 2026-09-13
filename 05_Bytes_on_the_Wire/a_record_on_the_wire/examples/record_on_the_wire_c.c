/* A struct is a layout in memory, not a layout on the wire. The compiler is
   free to leave gaps between the fields so each is aligned, and it stores the
   multi-byte fields in the machine's byte order -- so writing the struct's
   memory straight to a file sends padding and an endianness no reader can rely
   on. The wire format is the fields, in order, each serialised by hand. */
#include <stdio.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

struct order {
    uint8_t  type;      /* 1 byte  */
    uint32_t id;        /* 4 bytes */
    uint16_t qty;       /* 2 bytes */
};

static void dump(const char *label, const void *p, size_t n)
{
    const unsigned char *b = p;
    printf("%-32s", label);
    for (size_t i = 0; i < n; i++)
        printf(" %02x", b[i]);
    printf("\n");
}

int main(void)
{
    printf("fields add up to %zu bytes, but sizeof(struct order) = %zu\n",
           sizeof(uint8_t) + sizeof(uint32_t) + sizeof(uint16_t),
           sizeof(struct order));
    printf("the gaps: offsetof type=%zu id=%zu qty=%zu\n",
           offsetof(struct order, type),
           offsetof(struct order, id),
           offsetof(struct order, qty));

    struct order o = { .type = 7, .id = 0x01020304u, .qty = 1000 };

    /* Serialise the fields into a 7-byte frame, big-endian, no padding. */
    unsigned char wire[7];
    wire[0] = o.type;
    wire[1] = o.id >> 24; wire[2] = o.id >> 16; wire[3] = o.id >> 8; wire[4] = o.id;
    wire[5] = o.qty >> 8; wire[6] = o.qty;
    dump("on the wire (7 bytes):", wire, sizeof wire);

    /* Read it back, again by hand. */
    struct order r;
    r.type = wire[0];
    r.id = (uint32_t)wire[1] << 24 | (uint32_t)wire[2] << 16
         | (uint32_t)wire[3] << 8  | wire[4];
    r.qty = (uint16_t)(wire[5] << 8 | wire[6]);
    printf("read back: type=%u id=0x%08x qty=%u, round trip ok: %s\n",
           r.type, r.id, r.qty,
           (r.type == o.type && r.id == o.id && r.qty == o.qty) ? "yes" : "no");
    return 0;
}
