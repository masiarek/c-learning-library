/* Six functions of different shapes and a main that calls five of them -- the
   program this page builds, strips, and hands to Ghidra to see what a function
   start looks like from the outside. */
#include <stdio.h>
#include <string.h>

/* A leaf: no calls, one parameter, nothing worth a stack frame. */
int twice(int x)
{
    return 2 * x;
}

/* Branches. */
int clamp(int x, int lo, int hi)
{
    if (x < lo) {
        return lo;
    }
    if (x > hi) {
        return hi;
    }
    return x;
}

/* A loop over an array. */
long sum(const int *a, int n)
{
    long total = 0;
    for (int i = 0; i < n; i++) {
        total += a[i];
    }
    return total;
}

/* A stack buffer and two library calls. */
int greet_len(const char *who)
{
    char buf[64];
    snprintf(buf, sizeof buf, "hello, %s", who);
    return (int)strlen(buf);
}

/* One call, in tail position. */
void say(const char *word)
{
    printf("%s\n", word);
}

/* Nothing calls this. The compiler keeps it because it has external linkage,
   the linker keeps it because nothing told it not to -- and after strip, its
   bytes are all that is left of it. */
int orphan(int x)
{
    printf("nobody calls this: %d\n", x);
    return x + 1;
}

int main(void)
{
    int a[] = { 1, 2, 3, 4 };
    printf("twice(21) = %d\n", twice(21));
    printf("clamp(15, 0, 10) = %d\n", clamp(15, 0, 10));
    printf("sum({1, 2, 3, 4}) = %ld\n", sum(a, 4));
    printf("greet_len(\"world\") = %d\n", greet_len("world"));
    say("prologue");
    return 0;
}
