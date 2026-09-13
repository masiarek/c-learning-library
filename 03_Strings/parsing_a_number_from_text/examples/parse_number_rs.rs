// `str::parse` returns a Result, and the Err says which of strtol's three
// checks failed. Nothing is trimmed and nothing is guessed: " 42" is an error,
// and so is a number the type cannot hold.
fn main() {
    let inputs = ["42", " 42", "42 ", "42abc", "", "abc", "-7", "+7", "0x1A",
                  "99999999999999999999999"];
    for text in inputs {
        match text.parse::<i32>() {
            Ok(v) => println!("{:28} parse::<i32>() = Ok({v})", format!("{text:?}")),
            Err(e) => println!("{:28} parse::<i32>() = Err: {e}", format!("{text:?}")),
        }
    }
    println!("\" 42 \".trim().parse::<i32>() = {:?}", " 42 ".trim().parse::<i32>());
    println!("i32::from_str_radix(\"1A\", 16) = {:?}", i32::from_str_radix("1A", 16));
    println!("\"99999999999999999999999\".parse::<u128>() = {:?}",
             "99999999999999999999999".parse::<u128>());
}
