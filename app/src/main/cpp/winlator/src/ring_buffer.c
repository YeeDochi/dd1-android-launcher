#include <unistd.h>
#include <stdio.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stddef.h>
#include <pthread.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <linux/futex.h>
#include <time.h>

#include "ring_buffer.h"
#include "time_utils.h"
#include "events.h"

#define STRUCT_OFFSETS() \
    struct Offsets { \
        uint32_t head; \
        uint32_t tail; \
        uint32_t status; \
        void* buffer; \
    }

#ifdef __ANDROID__
#include <android/log.h>
#define debug_printf(...) __android_log_print(ANDROID_LOG_DEBUG, "System.out", __VA_ARGS__)
#else
#define debug_printf(...) fprintf(stderr, __VA_ARGS__)
#endif

void RingBuffer_setHead(RingBuffer* ring, uint32_t head) {
    atomic_store_explicit(ring->head, head, memory_order_release);
}

uint32_t RingBuffer_getHead(RingBuffer* ring) {
    return atomic_load_explicit(ring->head, memory_order_acquire);
}

/* dd1: the reader of a ring used to poll it - spin, then yield or sleep 20 us
 * at a time - for as long as the other side had nothing to say, which on a
 * game drawing at 60 FPS is most of every frame on two cores. Now a reader
 * that has waited RING_SPIN_NS sets READER_SLEEPS and sleeps on the tail word
 * with a futex; the writer, after moving the tail, clears that bit and wakes
 * it. The store of the tail and the load of the status are both seq_cst, as
 * are the reader's setting of the bit and its load of the tail, so either the
 * writer sees the bit or the reader sees the new tail - and the futex itself
 * refuses to sleep if the tail has moved.
 *
 * A reader only sleeps once the writer has said it wakes (WRITER_WAKES, set on
 * its first write). The renderer also serves Vortek, whose client is not ours
 * and never wakes anybody; for it the old wait is kept. The sleep is bounded,
 * so a missed wake costs time, not a hang. */
#define RING_SPIN_NS 200000ULL
#define RING_SLEEP_NS 20000000L

static inline uint64_t ringNow(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ULL + ts.tv_nsec;
}

static inline void ringRelax(void) {
#if defined(__aarch64__)
    __asm__ volatile("isb" ::: "memory");
#endif
}

void RingBuffer_setTail(RingBuffer* ring, uint32_t tail) {
    if (!ring->dd1Advertised) {
        atomic_fetch_or_explicit(ring->status, RING_STATUS_DD1_WRITER_WAKES, memory_order_seq_cst);
        ring->dd1Advertised = true;
    }
    atomic_store_explicit(ring->tail, tail, memory_order_seq_cst);
    if (atomic_load_explicit(ring->status, memory_order_seq_cst) & RING_STATUS_DD1_READER_SLEEPS) {
        atomic_fetch_and_explicit(ring->status, ~RING_STATUS_DD1_READER_SLEEPS, memory_order_seq_cst);
        syscall(SYS_futex, (uint32_t*)ring->tail, FUTEX_WAKE, 1, NULL, NULL, 0);
    }
}

uint32_t RingBuffer_getTail(RingBuffer* ring) {
    return atomic_load_explicit(ring->tail, memory_order_acquire);
}

void RingBuffer_setStatus(RingBuffer* ring, uint32_t status) {
    atomic_fetch_or_explicit(ring->status, status, memory_order_seq_cst);
}

void RingBuffer_unsetStatus(RingBuffer* ring, uint32_t status) {
    atomic_fetch_and_explicit(ring->status, ~status, memory_order_seq_cst);
}

bool RingBuffer_hasStatus(RingBuffer* ring, uint32_t status) {
    return (atomic_load_explicit(ring->status, memory_order_seq_cst) & status);
}

RingBuffer* RingBuffer_create(int shmFd, uint32_t bufferSize) {
    RingBuffer* ring = calloc(1, sizeof(RingBuffer));

    STRUCT_OFFSETS();

    int shmSize = RingBuffer_getSHMemSize(bufferSize);
    void* sharedData = mmap(NULL, shmSize, PROT_READ | PROT_WRITE, MAP_SHARED, shmFd, 0);
    if (sharedData == MAP_FAILED) return NULL;
    memset(sharedData, 0, shmSize);

    ring->head = sharedData + offsetof(struct Offsets, head);
    ring->tail = sharedData + offsetof(struct Offsets, tail);
    ring->status = sharedData + offsetof(struct Offsets, status);
    ring->buffer = sharedData + offsetof(struct Offsets, buffer);
    ring->bufferSize = bufferSize;

    RingBuffer_setStatus(ring, RING_STATUS_IDLE);
    return ring;
}

uint32_t RingBuffer_size(RingBuffer* ring) {
    return RingBuffer_getTail(ring) - RingBuffer_getHead(ring);
}

uint32_t RingBuffer_freeSpace(RingBuffer* ring) {
    return ring->bufferSize - RingBuffer_size(ring);
}

bool RingBuffer_read(RingBuffer* ring, void* data, uint32_t size) {
    if (size > ring->bufferSize) {
        debug_printf("ring: buffer overflow on read (%d/%d)\n", size, ring->bufferSize);
        return false;
    }

    if (!RingBuffer_waitForRead(ring, size)) return false;
    uint32_t head = RingBuffer_getHead(ring);
    uint32_t offset = head & (ring->bufferSize - 1);

    if ((offset + size) <= ring->bufferSize) {
        memcpy(data, ring->buffer + offset, size);
    }
    else {
        uint32_t start = ring->bufferSize - offset;
        memcpy(data, ring->buffer + offset, start);
        memcpy(data + start, ring->buffer, size - start);
    }

    RingBuffer_setHead(ring, head + size);
    return true;
}

bool RingBuffer_write(RingBuffer* ring, const void* data, uint32_t size) {
    if (size > ring->bufferSize) {
        debug_printf("ring: buffer overflow on write (%d/%d)\n", size, ring->bufferSize);
        return false;
    }

    if (!RingBuffer_waitForWrite(ring, size)) return false;
    uint32_t tail = RingBuffer_getTail(ring);
    uint32_t offset = tail & (ring->bufferSize - 1);

    if ((offset + size) <= ring->bufferSize) {
        memcpy(ring->buffer + offset, data, size);
    }
    else {
        uint32_t start = ring->bufferSize - offset;
        memcpy(ring->buffer + offset, data, start);
        memcpy(ring->buffer, data + start, size - start);
    }

    RingBuffer_setTail(ring, tail + size);
    return true;
}

uint32_t RingBuffer_getSHMemSize(uint32_t bufferSize) {
    STRUCT_OFFSETS();

    return bufferSize + offsetof(struct Offsets, buffer);
}

void RingBuffer_free(RingBuffer* ring) {
    if (!ring) return;

    if (ring->sharedData) {
        RingBuffer_setStatus(ring, RING_STATUS_EXIT);
        munmap(ring->sharedData, RingBuffer_getSHMemSize(ring->bufferSize));
        ring->sharedData = NULL;
    }

    free(ring);
}

bool RingBuffer_waitForRead(RingBuffer* ring, uint32_t size) {
    if (RingBuffer_size(ring) >= size) return true;
    if (!RingBuffer_hasStatus(ring, RING_STATUS_DD1_WRITER_WAKES)) {
        uint32_t busyWaitIter = 0;
        do {
            if (RingBuffer_size(ring) >= size) break;
            busyWait(&busyWaitIter);
            if (RingBuffer_hasStatus(ring, RING_STATUS_EXIT)) return false;
        }
        while (1);
        return true;
    }

    uint64_t spinUntil = ringNow() + RING_SPIN_NS;
    uint32_t polls = 0;
    while (1) {
        if (RingBuffer_size(ring) >= size) return true;
        if (RingBuffer_hasStatus(ring, RING_STATUS_EXIT)) return false;
        if ((++polls & 31) != 0 || ringNow() < spinUntil) {
            ringRelax();
            continue;
        }

        atomic_fetch_or_explicit(ring->status, RING_STATUS_DD1_READER_SLEEPS, memory_order_seq_cst);
        uint32_t tail = atomic_load_explicit(ring->tail, memory_order_seq_cst);
        if (tail - RingBuffer_getHead(ring) < size && !RingBuffer_hasStatus(ring, RING_STATUS_EXIT)) {
            struct timespec timeout = {0, RING_SLEEP_NS};
            syscall(SYS_futex, (uint32_t*)ring->tail, FUTEX_WAIT, tail, &timeout, NULL, 0);
        }
        atomic_fetch_and_explicit(ring->status, ~RING_STATUS_DD1_READER_SLEEPS, memory_order_seq_cst);
    }
}

bool RingBuffer_waitForWrite(RingBuffer* ring, uint32_t size) {
    uint32_t busyWaitIter = 0;
    do {
        if (RingBuffer_freeSpace(ring) >= size) break;
        busyWait(&busyWaitIter);
        if (RingBuffer_hasStatus(ring, RING_STATUS_EXIT)) return false;
    }
    while (1);
    return true;
}