#!/bin/bash
set -e
if ! command -v aarch64-linux-gnu-gcc >/dev/null; then
  apt-get update -qq >/dev/null
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq gcc-aarch64-linux-gnu cmake make libx11-dev >/dev/null 2>&1 || true
fi
rm -rf /out/build && mkdir -p /out/build && cd /out/build
cmake /src -DCMAKE_SYSTEM_NAME=Linux -DCMAKE_SYSTEM_PROCESSOR=aarch64 \
  -DCMAKE_C_COMPILER=aarch64-linux-gnu-gcc -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_FLAGS_RELEASE="-O2 -Wl,-rpath=/data/data/com.winlator/files/rootfs/lib" >/dev/null
make -j8 2>&1 | grep -E "error|warning: implicit|Linking" || true
ls -la /out/build/libGL.so*
