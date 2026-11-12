#!/usr/bin/env python3
"""Adapt the pinned upstream CLI's helper filename to Android's lib*.so naming.

The upstream 0.167.1 binary contains fixed-length Rust strings. Only the
20-byte helper name is replaced; this does not change the ELF's layout.
"""
import hashlib
import pathlib
import sys

source = pathlib.Path(sys.argv[1])
destination = pathlib.Path(sys.argv[2])
data = source.read_bytes()
expected = "c912f1179d0d8b8e18ee8c4dd856d92bd54a98f1b430b243da819f2a4e63841f"
if hashlib.sha256(data).hexdigest() != expected:
    raise SystemExit("Unexpected CLI binary: only official 0.167.1 ARM64 is supported")
old = b"codex-code-mode-host"
new = b"libcode-mode-host.so"
if len(old) != len(new) or data.count(old) != 3:
    raise SystemExit("Unexpected helper name layout; refusing to patch")
patched = data.replace(old, new)
if len(patched) != len(data) or patched.count(old) != 0:
    raise SystemExit("Helper filename patch validation failed")
destination.write_bytes(patched)
print("Patched CLI helper filename; sha256=" + hashlib.sha256(patched).hexdigest())
