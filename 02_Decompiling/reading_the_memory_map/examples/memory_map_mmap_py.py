"""The two requests a loader makes, asked from Python. mmap maps a range of
memory either onto a file's bytes or onto nothing -- zeros the OS supplies --
and with or without permission to write: the Byte Source, Initialized and W
columns of Ghidra's Memory Map, as a program can ask for them at run time."""

import mmap
import os
import tempfile

# Backed by no file: every byte zero, like the tail of .data or all of .bss.
zeros = mmap.mmap(-1, 1 << 20)
print("anonymous map:", len(zeros), "bytes,", len(zeros) - bytes(zeros).count(0), "nonzero")

with tempfile.TemporaryDirectory() as folder:
    path = os.path.join(folder, "limit.bin")
    with open(path, "wb") as f:
        f.write((42).to_bytes(4, "little"))

    # Backed by the file, and read-only: the bytes are the file's, and a write is refused.
    with open(path, "rb") as f:
        loaded = mmap.mmap(f.fileno(), 0, access=mmap.ACCESS_READ)
        print("file map:", len(loaded), "bytes, limit =", int.from_bytes(loaded[:4], "little"))
        try:
            loaded[0] = 0
        except TypeError as e:
            print("write refused:", e)
        loaded.close()
