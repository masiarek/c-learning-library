// `&str` is UTF-8 bytes that the type has checked. Byte length and character
// count are two methods; a cut inside a character is refused by `get`, and the
// ASCII-only case change is a separately named method rather than the default.
fn main() {
    let s = "café";
    let bytes = s.as_bytes();
    println!("\"{s}\": len() = {} bytes, chars().count() = {}", s.len(), s.chars().count());
    println!("bytes: {:02x?}", bytes);

    println!("to_uppercase() = {:?}", s.to_uppercase());
    println!("to_ascii_uppercase() = {:?}  -- the C answer, by name", s.to_ascii_uppercase());

    println!("is_char_boundary(4) = {}", s.is_char_boundary(4));
    println!("s.get(..4) = {:?}, s.get(..3) = {:?}", s.get(..4), s.get(..3));

    let cut = &bytes[..4];
    match std::str::from_utf8(cut) {
        Ok(t) => println!("from_utf8(first 4 bytes) = Ok({t:?})"),
        Err(e) => println!("from_utf8(first 4 bytes) = Err: valid_up_to {}, error_len {:?}",
                           e.valid_up_to(), e.error_len()),
    }

    let reversed: String = s.chars().rev().collect();
    println!("chars().rev() = {reversed:?}");
    let mut rev_bytes = bytes.to_vec();
    rev_bytes.reverse();
    println!("bytes reversed: {}", String::from_utf8_lossy(&rev_bytes));
}
