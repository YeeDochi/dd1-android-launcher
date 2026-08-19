#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

int main(int argc, char **argv) {
    if (argc < 2) {
        fputs("usage: dd1_runner <loader> [args...]\n", stderr);
        return 64;
    }
    execv(argv[1], &argv[1]);
    fprintf(stderr, "dd1_runner: execv failed: %s\n", strerror(errno));
    return 126;
}
