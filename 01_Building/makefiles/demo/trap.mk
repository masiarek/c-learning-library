CC     = cc
CFLAGS = -Wall -O2

hello: hello.o greet.o
	$(CC) -o hello hello.o greet.o

hello.o: hello.c
	$(CC) $(CFLAGS) -c hello.c

greet.o: greet.c
	$(CC) $(CFLAGS) -c greet.c
