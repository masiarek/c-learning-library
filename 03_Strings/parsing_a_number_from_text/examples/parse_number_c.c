/* Turning text into a number is deserialization in miniature, and C's three
   ways of doing it fail three different ways: atoi cannot report failure at
   all, sscanf reports how many items it matched and leaves the rest, strtol
   tells you exactly where it stopped and why. */
#include <errno.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>

/* strtol with every check the manual page asks for. */
static const char *parse_long(const char *text, long *out)
{
    char *end;
    errno = 0;
    long v = strtol(text, &end, 10);
    if (end == text) return "no digits";
    if (*end != '\0') return "trailing characters";
    if (errno == ERANGE) return "out of range";
    *out = v;
    return NULL;
}

int main(void)
{
    /* Every input here is inside int's range, because atoi and sscanf's %d are
       undefined on a value that overflows -- so the overflow case is handled
       below, through strtol alone, which is the only one of the three that is
       allowed to see it. */
    const char *inputs[] = { "42", " 42", "42 ", "42abc", "", "abc",
                             "-7", "+7", "0x1A" };
    size_t count = sizeof inputs / sizeof inputs[0];

    printf("%-12s %-8s %-18s %s\n", "input", "atoi", "sscanf %d", "strtol, checked");
    for (size_t i = 0; i < count; i++) {
        const char *in = inputs[i];
        char shown[16];
        snprintf(shown, sizeof shown, "\"%s\"", in);

        int a = atoi(in);

        int n = 0;
        int items = sscanf(in, "%d", &n);
        char scanned[20];
        if (items == 1) snprintf(scanned, sizeof scanned, "1 item, %d", n);
        else            snprintf(scanned, sizeof scanned, "%d items", items);

        long v = 0;
        const char *why = parse_long(in, &v);
        char checked[40];
        if (why) snprintf(checked, sizeof checked, "error: %s", why);
        else     snprintf(checked, sizeof checked, "%ld", v);

        printf("%-12s %-8d %-18s %s\n", shown, a, scanned, checked);
    }

    /* The overflow case, on strtol only: atoi would be undefined here, and
       sscanf's %d with it too. strtol sets errno to ERANGE and reports it. */
    long v = 0;
    const char *why = parse_long("99999999999999999999999", &v);
    printf("\nstrtol(\"99999999999999999999999\", checked) -> error: %s\n",
           why ? why : "none");

    /* Base 0 lets the text choose: 0x for hex, a leading 0 for octal. */
    printf("strtol(\"0x1A\", base 0) = %ld, strtol(\"012\", base 0) = %ld, base 10 = %ld\n",
           strtol("0x1A", NULL, 0), strtol("012", NULL, 0), strtol("012", NULL, 10));

    /* The width on %s is the only thing between scanf and gets. */
    char name[6];
    int got = sscanf("Zbigniew", "%5s", name);
    printf("sscanf(\"Zbigniew\", \"%%5s\") -> %d item, name = \"%s\"\n", got, name);
    return 0;
}
