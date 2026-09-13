/* A format string is a small program that printf runs. The directives it can
   hold include one that writes to memory (%n), one that takes its width from
   an argument (%*d), and one whose meaning depends on whether it is text or
   format (%%). All of this is defined behaviour -- which is why a format the
   user controls is a program the user wrote. */
#include <stdio.h>

int main(void)
{
    const char *text = "100%% done";

    /* The same bytes as an argument and as the format. */
    printf("printf(\"%%s\\n\", text) prints: ");
    printf("%s\n", text);
    printf("printf(text) prints:           ");
    printf(text);
    printf("\n");

    /* %n writes the number of bytes printed so far into an int*. */
    int written = -1;
    printf("hello%n\n", &written);
    printf("%%n stored %d\n", written);

    /* Width and precision can come from arguments, so a format can read
       further down the argument list than the text suggests. */
    printf("[%*d] [%-*d] [%.*s]\n", 6, 42, 6, 42, 3, "abcdef");

    /* snprintf with a null buffer measures without writing. */
    int need = snprintf(NULL, 0, "%d bottles of %s", 99, "beer");
    printf("snprintf(NULL, 0, ...) says the text needs %d bytes\n", need);
    return 0;
}
