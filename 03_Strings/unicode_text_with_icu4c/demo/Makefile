# Build any one-file ICU program in this folder: `make hello_icu`, or copy an
# example beside it and `make icu_collate_c`. pkg-config supplies both halves:
# where the headers are (--cflags) and which libraries to link (--libs).
#
# On a Mac, Homebrew's ICU is keg-only and pkg-config cannot see it until told:
#   export PKG_CONFIG_PATH="$(brew --prefix icu4c)/lib/pkgconfig"
ICU    := icu-uc icu-i18n
CFLAGS := -std=c17 -Wall -Wextra -pedantic $(shell pkg-config --cflags $(ICU))
LDLIBS := $(shell pkg-config --libs $(ICU))

# No recipe: make's built-in rule turns hello_icu.c into hello_icu, and adds
# CFLAGS before the source and LDLIBS after it.
hello_icu:
