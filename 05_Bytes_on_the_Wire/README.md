# Bytes on the wire

**One line:** A file, a socket and a pipe carry one thing — bytes — and every disagreement about what those bytes mean is a bug waiting at the other end: text read as binary, a number read big-endian instead of little, a struct written straight to disk with its padding and its machine's byte order baked in, and a length off the wire that was believed.

The previous chapter was a string sitting in your own memory. This one is what happens when bytes have to leave the program — written to a file, sent to another machine, handed to code someone else wrote. Nothing travels with them to say what they are. So a wire format is a set of decisions you make explicit and write down: is this text or binary, which byte comes first, where does each field begin, and how many bytes is the next thing — really, and says who.

C is where those decisions are the most visible, because it does none of them for you. That is the lesson and the hazard at once: the honesty of `memcpy` is also the sharp edge of every remote exploit.

| Lesson | Level | What it settles |
|---|---|---|
| [Text and binary are both bytes](text_and_binary_are_both_bytes/README.md) | 101 → 201 | "Text" is a promise about which bytes; the same number as digits and as an integer shares none of them, and one of the two has a NUL in it |
| [Byte order on the wire](byte_order_on_the_wire/README.md) | 201 | Why a wide number has to pick an order, why the machine's order is not one to send, and how `htonl` and a handful of shifts fix it on any machine |
| [A record on the wire](a_record_on_the_wire/README.md) | 201 → 301 | Why `sizeof(struct)` is bigger than its fields, why writing a struct's memory to a file is not a format, and what to write instead |
| [A length you did not check](a_length_you_did_not_check/README.md) | 301 | A length-prefixed frame, the one comparison that is always missing, and the difference between a parser and a way in |

## Where this connects

The [encodings library ↗](https://masiarek.github.io/encodings-learning-library/) is the deep treatment of this chapter's subject, from the other languages' side:

- [The bytes do not say which end ↗](https://masiarek.github.io/encodings-learning-library/01_Bits_and_Bytes/which_end_comes_first/index.html) and [byte order and the BOM ↗](https://masiarek.github.io/encodings-learning-library/03_Encodings/byte_order_and_bom/index.html) — endianness in full.
- [Packing a record ↗](https://masiarek.github.io/encodings-learning-library/07_Real_Data/packing_a_record/index.html) — the same fourteen bytes in four languages.
- [A record has to say what it is, how long it is, and whether it arrived ↗](https://masiarek.github.io/encodings-learning-library/08_Build_Your_Own/framing_a_format/index.html) — the framing layer this chapter's last lesson is the C view of.
- [The byte that means something to somebody else ↗](https://masiarek.github.io/encodings-learning-library/12_Adversarial/in_band_signals/index.html) — where trusting the wire goes wrong on purpose.
