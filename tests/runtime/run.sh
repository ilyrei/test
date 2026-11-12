#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
TEST_BUILD="$(mktemp -d)"
rg --files "$ROOT_DIR/tests/runtime" -g '*.java' > "$TEST_BUILD/sources"
javac --release 8 -d "$TEST_BUILD" @"$TEST_BUILD/sources" "$ROOT_DIR/src/dev/codexboard/diagnostic/RuntimeTools.java"
java -cp "$TEST_BUILD" dev.codexboard.diagnostic.RuntimeToolsTest
