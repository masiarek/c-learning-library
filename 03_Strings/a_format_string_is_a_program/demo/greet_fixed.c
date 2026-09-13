/* The fix: the format is a literal the compiler can read, and the user's
   text is an argument to it. A % in the text is now just a character. */
#include <stdio.h>

int main(int argc, char **argv)
{
    if (argc < 2) {
        return 1;
    }
    printf("%s\n", argv[1]);
    return 0;
}
