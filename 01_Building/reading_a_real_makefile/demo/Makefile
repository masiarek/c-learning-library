# A small twin of the C++Now 2026 benchmarks Makefile walked through on the page:
# the same define / call / eval / foreach machinery, building one program per
# variant from one C file.

CC       = cc
CFLAGS   = -std=c17 -Wall -Wextra -O2

VARIANTS = alpha beta gamma
SOURCES  = $(wildcard *.c)

PROGRAMS = $(foreach v,$(VARIANTS),variant.$(v))

all: $(PROGRAMS)

# One rule per variant, written by the Makefile itself
define VARIANT_RULE
variant.$(1): $(SOURCES) Makefile
	$(CC) $(CFLAGS) -DVARIANT=$(1) $(SOURCES) -o $$@
endef

$(foreach v,$(VARIANTS),$(eval $(call VARIANT_RULE,$(v))))

run: all
	@for p in $(PROGRAMS); do \
		echo "=== $$p ==="; \
		./$$p $(ARGS); \
	done

clean:
	rm -f $(PROGRAMS)

.PHONY: all run clean
