"""Python's struct module IS the by-hand serialisation, written as a format
string. '>' is big-endian and pins down both the order and the absence of
padding; the codes B, I, H are the field widths. Native mode ('@') brings the
gaps back, which is exactly what you do not want on the wire."""
import struct

wire = struct.pack(">BIH", 7, 0x01020304, 1000)
print(f"struct.pack('>BIH', 7, 0x01020304, 1000) = {wire.hex(' ')}  ({len(wire)} bytes)")
print(f"struct.calcsize('>BIH') = {struct.calcsize('>BIH')}  -- packed, no padding")
print(f"struct.calcsize('@BIH') = {struct.calcsize('@BIH')}  -- native, with padding")

back = struct.unpack(">BIH", wire)
print(f"struct.unpack('>BIH', ...) = {back}")
print(f"round trip ok: {back == (7, 0x01020304, 1000)}")
