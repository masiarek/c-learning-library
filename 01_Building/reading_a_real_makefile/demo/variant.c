#include <stdio.h>

/* The Makefile passes -DVARIANT=alpha (or beta, or gamma). Without it, this is
   the fallback -- the same move the talk's harness.hpp makes for BENCH_CLOCK. */
#ifndef VARIANT
#define VARIANT unset
#endif

/* Two steps, so that the macro's VALUE becomes a string, not its name. */
#define TEXT(x) #x
#define NAME(x) TEXT(x)

int main(int argc, char **argv) {
    printf("variant %s", NAME(VARIANT));
    for (int i = 1; i < argc; i++)
        printf(" %s", argv[i]);
    puts("");
    return 0;
}
