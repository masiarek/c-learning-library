/* A format that promises an int and an argument that is a long. The program
   is undefined behaviour, and the compiler can see it because the format is a
   literal -- which is the whole case for keeping it one. */
#include <stdio.h>

int main(void)
{
    long big = 1L << 40;
    printf("%d\n", big);
    return 0;
}
