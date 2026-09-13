/* The bug: the first argument is printed AS the format. Anything the user
   types that contains a % is an instruction to printf, not text. */
#include <stdio.h>

int main(int argc, char **argv)
{
    if (argc < 2) {
        return 1;
    }
    printf(argv[1]);
    printf("\n");
    return 0;
}
