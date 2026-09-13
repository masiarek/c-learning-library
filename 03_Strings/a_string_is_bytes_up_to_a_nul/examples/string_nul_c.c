/* A C string is a run of bytes, and the only thing that says where it ends
   is a zero byte. Nothing else -- not the array, not the pointer -- carries a
   length. This program dumps the bytes behind four declarations so the NUL is
   visible rather than assumed. */
#include <stdio.h>
#include <string.h>

/* The bytes of `p`, `n` of them, as hex -- no library function, so nothing
   here stops at a NUL. */
static void dump(const char *label, const void *p, size_t n)
{
    const unsigned char *b = p;
    printf("%-26s", label);
    for (size_t i = 0; i < n; i++) {
        printf(" %02x", b[i]);
    }
    printf("\n");
}

/* An array parameter is a pointer: inside the function `sizeof s` is the
   size of a pointer, whatever the array was. */
static int sizeof_is_the_pointer(const char s[])
{
    return sizeof s == sizeof (const char *);
}

int main(void)
{
    /* 1. The literal is five letters; the compiler adds the sixth byte. */
    char word[] = "hello";
    printf("char word[] = \"hello\"\n");
    printf("  sizeof word = %zu, strlen(word) = %zu\n", sizeof word, strlen(word));
    dump("  bytes:", word, sizeof word);

    /* 2. Five bytes asked for, five letters supplied: legal C, and no NUL.
       This is an array of char, not a string -- strlen(five) would read past
       it, so this program only dumps it. */
    char five[5] = "hello";
    printf("char five[5] = \"hello\"\n");
    printf("  sizeof five = %zu, and no strlen: there is no NUL to stop at\n", sizeof five);
    dump("  bytes:", five, sizeof five);

    /* 3. A NUL in the middle ends the string there. The array is still six
       bytes long; every string function believes it is two. */
    char cut[] = "ab\0cd";
    printf("char cut[] = \"ab\\0cd\"\n");
    printf("  sizeof cut = %zu, strlen(cut) = %zu, printf(\"%%s\") prints \"%s\"\n",
           sizeof cut, strlen(cut), cut);
    dump("  bytes:", cut, sizeof cut);
    printf("  memcmp sees all six: memcmp(cut, \"ab\\0cd\", 6) == 0 is %s\n",
           memcmp(cut, "ab\0cd", 6) == 0 ? "true" : "false");

    /* 4. A pointer into the middle is a string too -- the same NUL ends it. */
    const char *tail = word + 2;
    printf("const char *tail = word + 2\n");
    printf("  tail = \"%s\", strlen(tail) = %zu\n", tail, strlen(tail));

    /* 5. Pass the array to a function and its length is gone. */
    printf("inside a function, sizeof s == sizeof (const char *): %s\n",
           sizeof_is_the_pointer(word) ? "true" : "false");
    return 0;
}
