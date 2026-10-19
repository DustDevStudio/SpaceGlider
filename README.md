# SpaceGlider
![Version](https://img.shields.io/badge/version-0.1.1-green) ![License](https://img.shields.io/badge/license-MIT-green)

A small word-based file compressor written in Java. Frequent words get short byte codes, rare words get longer ones, and the result is saved as a `.space` file.

## Download

You can find the executable in [release](https://github.com/ElAjbariYoussef/SpaceGlider/releases/latest)

## Performance Testing

A test on the 1 GB [enwik9](https://mattmahoney.net/dc/textdata.html) dataset reduced the size to 567.0 MB (enwik9.space). The compression is completely lossless.

## Usage

```
space-glider -c <filepath> <directory>    Compress a file into <directory>
space-glider -e <filepath> <directory>    Extract a .space file into <directory>
```

## How it works

1. The text is split into tokens: words, and each whitespace character (space, newline, tab) as its own token.
2. Tokens are counted and ranked by frequency.
3. Each token is replaced by a variable-length code based on its rank:

| Bytes | Bit pattern | Codes available |
|---|---|---|
| 1 | `1xxxxxxx` | 128 |
| 2 | `01xxxxxx xxxxxxxx` | 16,384 |
| 3 | `001xxxxx ...` | 2,097,152 |
| n | `(n-1)` zeros, a `1`, then payload | 2^(7n) |

The leading zeros tell the decoder how many bytes the code uses, so no separators are needed.

## Note

For text files
