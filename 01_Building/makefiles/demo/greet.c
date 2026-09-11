#include <stdio.h>
#include "greet.h"

void greet(const char *name) {
    printf("%s, ", GREETING);
    puts(name);
}
