#!/usr/bin/env bash
set -euo pipefail
# Rebuild Bash 5.2.37 with the pinned x86_64-hosted aarch64-musl toolchain.
# Usage: bash scripts/build-bash.sh /path/to/bash-5.2.37 /path/to/aarch64-unknown-linux-musl /path/to/output
SOURCE_DIR="$(realpath "$1")"
TOOLCHAIN_DIR="$(realpath "$2")"
OUTPUT_FILE="$(realpath -m "$3")"
BUILD_TEMP="$(mktemp -d)"
cd "$BUILD_TEMP"
env CC="$TOOLCHAIN_DIR/bin/aarch64-unknown-linux-musl-gcc" \
    AR="$TOOLCHAIN_DIR/bin/aarch64-unknown-linux-musl-ar" \
    RANLIB="$TOOLCHAIN_DIR/bin/aarch64-unknown-linux-musl-ranlib" \
    CFLAGS='-O2 -std=gnu17 -Wno-error=incompatible-pointer-types -Wno-error=implicit-function-declaration' \
    LDFLAGS='-static' "$SOURCE_DIR/configure" --build=x86_64-pc-linux-gnu \
    --host=aarch64-unknown-linux-musl --disable-nls --without-bash-malloc --enable-static-link
make -j4 CFLAGS='-O2 -std=gnu17 -Wno-error=incompatible-pointer-types -Wno-error=implicit-function-declaration'
"$TOOLCHAIN_DIR/bin/aarch64-unknown-linux-musl-strip" --strip-unneeded -o "$OUTPUT_FILE" bash
printf 'Bash written to %s; intermediate files: %s\n' "$OUTPUT_FILE" "$BUILD_TEMP"
