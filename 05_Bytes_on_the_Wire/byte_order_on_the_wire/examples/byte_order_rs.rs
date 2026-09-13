// Rust names the choice in the method. `to_be_bytes` and `to_le_bytes` write a
// number's bytes in a stated order, `from_be_bytes` reads them back, and none
// of them depends on the machine this runs on -- the order is in the name.
fn main() {
    let v: u32 = 0x0102_0304;
    println!("0x01020304 to_be_bytes() = {:02x?}", v.to_be_bytes());
    println!("0x01020304 to_le_bytes() = {:02x?}", v.to_le_bytes());
    println!("0x01020304 to_ne_bytes() = {:02x?}  (native: this machine's order)",
             v.to_ne_bytes());

    let wire = v.to_be_bytes();
    let back = u32::from_be_bytes(wire);
    println!("from_be_bytes(...) = 0x{back:08x}, round trip ok: {}", back == v);

    // The native order, named rather than assumed.
    println!("cfg!(target_endian = \"little\") = {}", cfg!(target_endian = "little"));
}
