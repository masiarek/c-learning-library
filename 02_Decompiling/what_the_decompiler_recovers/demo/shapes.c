/* Four small functions and a main that calls them -- the program this page
   builds, runs, and then hands to Ghidra's decompiler. */
#include <stdio.h>
#include <string.h>

struct point {
    int x;
    int y;
};

/* An expression the optimizer takes apart: a multiply, an add, and a signed
   division by two that becomes a shift with a sign fix-up. */
int weighted(int a, int b)
{
    return (a * 3 + b) / 2;
}

/* A loop with two locals. */
int sum_to(int n)
{
    int total = 0;
    for (int i = 1; i <= n; i++) {
        total += i;
    }
    return total;
}

/* A struct reached through a pointer: the file keeps offsets, not names. */
int norm2(const struct point *p)
{
    return p->x * p->x + p->y * p->y;
}

/* A string the library functions know the type of. */
void shout(const char *word)
{
    size_t n = strlen(word);
    printf("%s has %zu letters\n", word, n);
}

int main(void)
{
    struct point p = { 3, 4 };
    printf("weighted(4, 6) = %d\n", weighted(4, 6));
    printf("sum_to(10) = %d\n", sum_to(10));
    printf("norm2({3, 4}) = %d\n", norm2(&p));
    shout("decompiler");
    return 0;
}
