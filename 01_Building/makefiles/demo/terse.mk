CFLAGS = -Wall -O2 -MMD -MP
OBJS   = hello.o greet.o

hello: $(OBJS)

-include $(OBJS:.o=.d)
