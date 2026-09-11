CC     = cc
CFLAGS = -Wall -O2

hello: hello.o greet.o
	$(CC) -o hello hello.o greet.o

hello.o: hello.c greet.h
	$(CC) $(CFLAGS) -c hello.c

greet.o: greet.c greet.h
	$(CC) $(CFLAGS) -c greet.c

.PHONY: clean
clean:
	rm -f hello hello.o greet.o
