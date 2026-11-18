# The Gladio client the launcher ships

`app/src/main/assets/dd1/gladio-client.tzst` holds one file,
`opt/dd1-gladio/libGL.so.1`: Gladio's OpenGL client - the `libGL` the game
loads inside Wine - built from
[brunodev85/gladio](https://github.com/brunodev85/gladio) at commit `eaa2a8d`
with `dd1.patch` applied. Gladio is under the GNU Lesser General Public
License, version 2.1; this directory is the corresponding source of the
modified library. `DD1GladioClient` unpacks it beside the rootfs's own
`libGL` and puts it first on `LD_LIBRARY_PATH` when the chosen driver pair
ends in `gladio`.

## Why

Issue #5: on a Galaxy S26 Ultra (Adreno 840, Linux 6.12) Darkest Dungeon fell
to ~20 FPS and swallowed taps on every busy screen. The game's main thread
spent 95% of its time waiting on the renderer, and nearly all of those waits
were round trips the game did not need:

- `glGetUniformLocation` asked the renderer every time, hundreds of times a
  frame (71% of the waiting). Locations are now cached per context, program
  and name, and forgotten when the program is linked again or deleted.
- every buffer update (`glBufferData`, `glBufferSubData`, a flush of a mapped
  range) waited for its acknowledgement (~21%). The renderer answers them in
  order, so the client counts them and reads them before the next real reply,
  before it writes to shared buffer memory again, and before the context goes
  away.
- both sides polled the shared ring buffers with `thrd_yield()`, which the
  EEVDF scheduler (Linux 6.6+) answers by parking the thread behind anything
  else runnable. A reader now spins for 200 us and then sleeps on the ring's
  tail with a futex, and the writer wakes it. The renderer half of that is in
  `app/src/main/cpp/winlator/src/ring_buffer.c`; a reader only sleeps once the
  writer has said it wakes, so Vortek's unmodified client keeps the old wait.
- the game drew 400+ FPS on screens that do not move. The client paces
  `glXSwapBuffers` to `DD1_FPS_LIMIT` (0 is no limit, 60 when unset), which
  the launcher sets from its frame limit setting.

Measured in the same Farmstead fight: Galaxy S22 from ~22 to 60 FPS, Galaxy
S26 Ultra from ~20 to a steady 60 at 1280x720 and at 1920x1080.

## Building

```
git clone https://github.com/brunodev85/gladio && cd gladio
git checkout eaa2a8d
git apply --ignore-whitespace /path/to/docs/gladio-client/dd1.patch
docker run --rm -v "$PWD":/src -v /path/to/out:/out \
  -v /path/to/docs/gladio-client/build-in-docker.sh:/out/build-in-docker.sh \
  ubuntu:22.04 bash /out/build-in-docker.sh
```

That cross-compiles for aarch64 glibc with the rootfs's library directory as
the run path; the result is `/path/to/out/build/libGL.so`. Pack it as
`opt/dd1-gladio/libGL.so.1` in a zstd tar, replace the asset, and bump
`DD1GladioClient.MARKER` so installed copies are replaced. The side-by-side
build needs nothing extra: the asset goes through the runtime's unpacker, and
`DD1Relocation` rewrites the run path like every other runtime file.
