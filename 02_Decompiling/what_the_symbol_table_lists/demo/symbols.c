/* A program with one of each kind of name Ghidra's Symbol Table lists -- the
   file this page builds, strips, and hands to Ghidra's headless analyzer. */
#include <stdio.h>

/* Global data with a value. */
int counter = 7;

/* A string, and a pointer seven bytes into it: an address inside a data item
   rather than at its start. */
const char greeting[] = "hello, symbols";
const char *tail = greeting + 7;

/* static: a name only this file can use -- and still a name in the file. */
static int helper(int x)
{
    return x * 2;
}

int twice(int x)
{
    return helper(x);
}

/* Reached only through the table below: no call instruction names it. */
int via_table(int x)
{
    return x + counter;
}

int (*const table[])(int) = { via_table };

/* Nothing calls this, and nothing holds its address. */
int orphan(int x)
{
    printf("nobody calls this: %d\n", x);
    return x + 1;
}

int main(void)
{
    printf("twice(21) = %d\n", twice(21));
    printf("table[0](1) = %d\n", table[0](1));
    puts(tail);
    return 0;
}
