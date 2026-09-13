// Rust has no strcpy. A copy into a fixed buffer must state its length, and
// a length that does not match is a panic at the copy, not a write past the
// end. The panic hook is silenced so the program's own lines are its output.
fn main() {
    let longer = b"hello world";
    let mut buf = [0u8; 6];

    // The checked form: take as much as fits, and know that you did.
    let n = longer.len().min(buf.len() - 1);
    buf[..n].copy_from_slice(&longer[..n]);
    println!("copied {n} of {} bytes: {:?}", longer.len(), std::str::from_utf8(&buf[..n]).unwrap());

    // The unchecked form does not exist: copy_from_slice insists on equal lengths.
    std::panic::set_hook(Box::new(|_| {}));
    let outcome = std::panic::catch_unwind(|| {
        let mut b = [0u8; 6];
        b.copy_from_slice(longer);
        b
    });
    println!("buf.copy_from_slice(11 bytes into 6): {}",
             if outcome.is_err() { "panicked -- nothing was written" } else { "ok" });

    // Growing strings simply grow: String::push_str is the strcat that cannot overflow.
    let mut s = String::from("hello");
    s.push_str(" world");
    println!("push_str: len {} capacity {}", s.len(), s.capacity() >= s.len());
}
