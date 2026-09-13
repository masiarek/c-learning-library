// A Rust `str` carries its length and may hold a NUL; a `CString` is the
// C shape -- bytes plus a terminating zero -- and building one from data
// with a NUL inside is refused, because C would end the string there.
use std::ffi::{CStr, CString};

fn main() {
    let word = "hello";
    println!("\"hello\".len() = {} -- the length is stored, no terminator", word.len());

    let cut = "ab\0cd";
    println!("\"ab\\0cd\".len() = {}, find('\\0') = {:?}", cut.len(), cut.find('\0'));

    // Crossing into C: the NUL has to go on the end, and only there.
    let c = CString::new(word).unwrap();
    println!("CString::new(\"hello\") -> {} bytes with the NUL: {:?}",
             c.as_bytes_with_nul().len(), c.as_bytes_with_nul());
    match CString::new(cut) {
        Ok(_) => println!("CString::new(\"ab\\0cd\") -> ok"),
        Err(e) => println!("CString::new(\"ab\\0cd\") -> Err: {e}"),
    }

    // Since Rust 1.77 a C-string literal writes the terminator for you.
    let lit: &CStr = c"hello";
    println!("c\"hello\": count_bytes() = {}, to_bytes_with_nul().len() = {}",
             lit.count_bytes(), lit.to_bytes_with_nul().len());
}
