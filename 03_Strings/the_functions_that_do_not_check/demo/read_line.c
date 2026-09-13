/* Read standard input with fgets into a buffer eight bytes long, and report
   each call: how many bytes arrived, and whether they ended the line. fgets
   stops at the buffer's size minus one, always writes the NUL, and keeps the
   newline when the line fitted -- which is how a caller can tell "the line
   ended" from "the buffer filled". */
#include <stdio.h>
#include <string.h>

int main(void)
{
    char buf[8];
    while (fgets(buf, sizeof buf, stdin) != NULL) {
        size_t n = strlen(buf);
        int complete = n > 0 && buf[n - 1] == '\n';
        if (complete) {
            buf[n - 1] = '\0';
        }
        printf("fgets: %zu byte(s) \"%s\" -- %s\n", n, buf,
               complete ? "line complete" : "buffer full, line continues");
    }
    return 0;
}
