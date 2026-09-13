// The same frame in Rust. A length off the wire is a `usize` like any other,
// but the slice it indexes is bounds-checked: `get(..len)` returns None instead
// of reading past the buffer, so the check you must remember to write in C is
// the only way to get a payload out at all here.
fn read_frame(buf: &[u8], max_payload: usize) -> Result<&[u8], &'static str> {
    if buf.len() < 2 {
        return Err("not even a length field arrived");
    }
    let claimed = u16::from_be_bytes([buf[0], buf[1]]) as usize;
    if claimed > max_payload {
        return Err("claimed length exceeds our ceiling");
    }
    // get() is the check: too-long a claim yields None, never an over-read.
    buf.get(2..2 + claimed).ok_or("claimed length runs past the bytes we have")
}

fn main() {
    let good = [0x00, 0x05, b'h', b'e', b'l', b'l', b'o'];
    let lie = [0xea, 0x60, b'h', b'i'];
    let cut = [0x00, 0x08, b'a', b'b', b'c'];

    for (name, frame) in [("length 5, 5 present", &good[..]),
                          ("claims 60000, 2 present", &lie[..]),
                          ("length 8, 3 present", &cut[..])] {
        match read_frame(frame, 1024) {
            Ok(p) => println!("{name:24} accepted: {:?}", std::str::from_utf8(p).unwrap()),
            Err(e) => println!("{name:24} rejected: {e}"),
        }
    }
}
