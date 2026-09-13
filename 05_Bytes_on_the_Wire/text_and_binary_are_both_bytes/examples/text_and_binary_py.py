"""Python makes the split a type: text goes through str and a codec, bytes go
out as they are. open() in "w" mode encodes for you; "wb" does not, and hands
back exactly the bytes you wrote. The same number, both ways, shares no byte."""
n = 1000
as_text = str(n).encode("utf-8")
as_int = n.to_bytes(4, "big")
print(f"1000 as text: {as_text!r}  ({len(as_text)} bytes)")
print(f"1000 as int32 big-endian: {as_int!r}  ({len(as_int)} bytes)")
print(f"a NUL byte in the integer form: {(0 in as_int)}")

# "text mode" is str + an encoding; "binary mode" is bytes, verbatim.
import io
text_file = io.StringIO()
text_file.write("1000")
print(f'open(..., "w").write(\"1000\") stored the str {text_file.getvalue()!r}')

binary_file = io.BytesIO()
binary_file.write(as_int)
print(f'open(..., "wb").write(int) stored the bytes {binary_file.getvalue()!r}')

# The one byte a text file agrees on -- and the two-byte version Windows uses.
print(rf"newline '\n' is {chr(10).encode()!r}; Windows '\r\n' is {chr(13)+chr(10)!r}"
      f" = {(chr(13)+chr(10)).encode()!r}")
