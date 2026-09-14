// Rust has no table of conversions either: `{}` calls the value's Display
// impl, and the Formatter it is handed carries the flags from the braces --
// `#` as alternate(), a width, a precision, and `<` for left alignment, which
// Rust spells where printf spells `-`. The impl lives with the type, and the
// compiler checks every format string against it.
use std::fmt;

struct Coordinate {
    x: f64,
    y: f64,
}

impl fmt::Display for Coordinate {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        let width = f.width().unwrap_or(0);
        let prec = f.precision().unwrap_or(6);
        let left = matches!(f.align(), Some(fmt::Alignment::Left));
        let number = |v: f64| {
            if left {
                format!("{v:<width$.prec$}")
            } else {
                format!("{v:>width$.prec$}")
            }
        };
        if f.alternate() {
            write!(f, "({}, {})", number(self.x), number(self.y))
        } else {
            write!(f, "{} {}", number(self.x), number(self.y))
        }
    }
}

fn main() {
    let c = Coordinate { x: 12345.6789, y: 3.141593 };

    // The same six as the C program, with the flags where Rust puts them.
    println!("{:10} |{}|", "{}", c);
    println!("{:10} |{:14}|", "{:14}", c);
    println!("{:10} |{:<14.2}|", "{:<14.2}", c);
    println!("{:10} |{:#}|", "{:#}", c);
    println!("{:10} |{:#14}|", "{:#14}", c);
    println!("{:10} |{:<#14.2}|", "{:<#14.2}", c);

    // Beside the standard conversions in one format string.
    println!("point {} is {:#.1}, {}", 1, c, "done");
}
