/* A program for a debugger to watch: four kinds of memory, one function that
   changes each of them, and a line of output the shell can check. */
#include <stdio.h>
#include <stdlib.h>

int table[4] = { 10, 20, 30, 40 };  /* .data: initialized, so its bytes are in the file */
int counter;                        /* .bss: not in the file; the loader supplies zeroes */

int bump(int *slot, int by)
{
    *slot += by;
    counter++;
    return *slot;
}

int main(void)
{
    int local = 5;                     /* the stack */
    int *heap = malloc(sizeof *heap);  /* the heap */
    if (heap == NULL) {
        return 1;
    }
    *heap = 7;
    bump(&table[1], 1);
    bump(&local, 2);
    bump(heap, 3);
    printf("table[1]=%d local=%d *heap=%d counter=%d\n", table[1], local, *heap, counter);
    free(heap);
    return 0;
}
