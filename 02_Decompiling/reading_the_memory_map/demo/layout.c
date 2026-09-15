/* Three globals, one for each way a loader places bytes, and two functions that
   touch them -- the program this page builds for macOS, Linux and Windows and
   then reads in Ghidra's Memory Map.

   Everywhere but Windows: cc -std=c17 -Wall -Wextra -O2 -o layout layout.c
   For Windows: demo/build_pe.sh, which needs no Windows and no C runtime. */

/* Read-only: the bytes are in the file, and nothing may write them. */
const char greeting[] = "memory map\n";

/* Writable, with a value the file has to carry. */
int limit = 42;

/* Writable and all zeros: a mebibyte the program gets at load time and the file
   does not hold. */
char scratch[1 << 20];

int over_limit(int n)
{
    return n > limit;
}

int remember(int n)
{
    scratch[n & 0xfffff] += 1;
    return scratch[n & 0xfffff];
}

#ifdef _WIN32
/* No C runtime in this build, so no printf and no main: the linker's /entry
   names start, and kernel32.dll does the writing. */
typedef void *HANDLE;
__declspec(dllimport) HANDLE __stdcall GetStdHandle(unsigned long which);
__declspec(dllimport) int __stdcall WriteFile(HANDLE file, const void *bytes, unsigned long count,
                                              unsigned long *written, void *overlapped);
__declspec(dllimport) void __stdcall ExitProcess(unsigned int code);

void start(void)
{
    unsigned long written;
    HANDLE out = GetStdHandle((unsigned long)-11);   /* STD_OUTPUT_HANDLE */
    WriteFile(out, greeting, sizeof greeting - 1, &written, 0);
    ExitProcess((unsigned int)(over_limit(50) + remember(7)));
}
#else
#include <stdio.h>

int main(void)
{
    int nonzero = 0;
    for (unsigned long i = 0; i < sizeof scratch; i++) {
        nonzero += scratch[i] != 0;
    }
    printf("%s", greeting);
    printf("limit = %d, over_limit(50) = %d\n", limit, over_limit(50));
    printf("scratch: %lu bytes, %d of them nonzero\n", (unsigned long)sizeof scratch, nonzero);
    printf("remember(7) = %d\n", remember(7));
    return 0;
}
#endif
