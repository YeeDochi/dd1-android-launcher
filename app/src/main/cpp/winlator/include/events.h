#ifndef WINLATOR_EVENTS_H
#define WINLATOR_EVENTS_H

#include <stdbool.h>
#include <poll.h>
#include <errno.h>
#include <threads.h>
#include <sys/eventfd.h>

#define EVENT_RESULT_SUCCESS 0
#define EVENT_RESULT_TIMEOUT 1
#define EVENT_RESULT_ERROR 2

#define BUSY_WAIT_MAX_ITER 500
#define BUSY_WAIT_SLEEP_US 100

static inline int createEvent(bool initialState) {
    return eventfd(initialState ? 1 : 0, EFD_CLOEXEC | EFD_NONBLOCK);
}

static inline bool setEvent(int fd) {
    uint64_t value = 1;
    ssize_t res = write(fd, &value, sizeof(uint64_t));
    return res >= 0 ? true : false;
}

static inline bool resetEvent(int fd) {
    uint64_t value;
    ssize_t res = read(fd, &value, sizeof(uint64_t));
    return res >= 0 ? true : false;
}

static inline int waitForEvents(int* fds, int numFds, bool waitAll, int timeoutMs) {
    if (timeoutMs <= 0) timeoutMs = -1;
    struct pollfd pfds[numFds];
    int i, j, res;

    bool signaled[numFds];
    for (i = 0, j = 0; i < numFds; i++) {
        if (fds[i] < 0) continue;
        pfds[j].fd = fds[i];
        pfds[j].events = POLLIN;
        pfds[j].revents = 0;
        signaled[j] = false;
        j++;
    }

    numFds = j;
    if (numFds == 0) return EVENT_RESULT_SUCCESS;

    int remaining = numFds;
    do {
        res = poll(pfds, remaining, timeoutMs);
        if (res < 0) return EVENT_RESULT_ERROR;

        bool anySignaled = false;
        for (i = 0; i < remaining; i++) {
            if ((pfds[i].revents & POLLIN)) {
                if (!waitAll || numFds == 1) return EVENT_RESULT_SUCCESS;
                remaining--;

                for (j = 0; j < numFds; j++) {
                    if (pfds[i].fd == fds[j]) {
                        signaled[j] = true;
                        anySignaled = true;
                        break;
                    }
                }
            }
        }

        if (!anySignaled && timeoutMs > 0) return EVENT_RESULT_TIMEOUT;

        for (i = 0, j = 0; i < numFds; i++) {
            if (!signaled[i]) {
                pfds[j].fd = fds[i];
                pfds[j].events = POLLIN;
                pfds[j].revents = 0;
                j++;
            }
        }
    }
    while (remaining > 0);

    return EVENT_RESULT_SUCCESS;
}

// dd1: the ring buffers between the guest and the renderers wait for each
// other here, hundreds of times a frame. Upstream yielded the core on every
// turn. Under the EEVDF scheduler (Linux 6.6+, e.g. the Galaxy S26 Ultra's
// 6.12) a yielding thread goes behind whatever else is runnable on that core
// until its slice ends, so with anything else awake each round trip stretched
// and Darkest Dungeon fell to ~22 FPS on screens it otherwise draws at ~200
// (issue #5). Spin on the CPU's own pause for a short time budget instead -
// the other side usually answers within it - and sleep after that, so a long
// wait still costs nothing. The 32-bit iteration counter holds the start of
// the spin, read from the generic timer, which user space can read on arm64.
#define BUSY_WAIT_SPIN_US 50

#if defined(__aarch64__)
static inline uint64_t busyWaitTicks(void) {
    uint64_t ticks;
    __asm__ volatile("mrs %0, cntvct_el0" : "=r"(ticks));
    return ticks;
}

static inline uint64_t busyWaitTicksPerSecond(void) {
    uint64_t frequency;
    __asm__ volatile("mrs %0, cntfrq_el0" : "=r"(frequency));
    return frequency;
}

static inline void busyWait(uint32_t* iter) {
    uint32_t now = (uint32_t)busyWaitTicks();
    if (*iter == 0) {
        // Zero means "not started"; a start that happens to read as zero
        // only shortens this one spin by a tick.
        *iter = now | 1;
        return;
    }
    uint64_t budget = busyWaitTicksPerSecond() * BUSY_WAIT_SPIN_US / 1000000;
    if ((uint32_t)(now - *iter) < budget) {
        __asm__ volatile("isb" ::: "memory");
    }
    else usleep(BUSY_WAIT_SLEEP_US);
}
#else
static inline void busyWait(uint32_t* iter) {
    (*iter)++;
    if (*iter < BUSY_WAIT_MAX_ITER) {
        thrd_yield();
    }
    else usleep(BUSY_WAIT_SLEEP_US);
}
#endif

#endif