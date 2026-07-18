# Design: Native Chat mathematics rendering

## Context

`MarkdownText` owns a small deterministic block/inline Markdown parser and is
used for completed and streaming assistant content. Mathematical notation must
compose with that parser without interpreting ordinary dollar amounts as TeX or
making transient, unfinished streamed delimiters disappear.

## Decisions

### Parse mathematics into explicit segments and blocks

Display mathematics (`$$...$$` and `\[...\]`) is recognized at block level.
Inline mathematics (`$...$` and `\(...\)`) is recognized inside paragraphs,
headings, list items, and quotes. A single-dollar expression must contain
non-whitespace content, and a dollar followed by a digit is treated as currency
unless a valid closing delimiter is found after mathematical syntax. Escaped
delimiters remain text.

Unclosed delimiters are never consumed. This makes streamed partial responses
remain visible until the closing delimiter arrives.

### Use a native local TeX renderer

Use `ru.noties:jlatexmath-android` to create Android drawables and bridge them
into Compose images. The renderer is deterministic, offline, and avoids one
WebView per formula. Formula bitmaps/drawables are remembered by expression,
text size, color, and display mode to avoid repeated parsing during recomposition.

### Fail soft

TeX parsing/rendering failures display the original delimited source as normal
text. A model response must never make the Chat composition fail.

## Trade-offs

- The selected renderer is mature but not recent. It is isolated behind one
  composable so it can be replaced without changing Chat parsing contracts.
- Inline formulas are emitted as separate Compose children rather than embedded
  in a single text baseline. This keeps the implementation native and testable,
  while allowing wrapping between text and formula segments.

## Validation

- Unit tests for block and inline delimiter parsing, currency, escaping, and
  incomplete streaming input.
- Existing Markdown tests to guard compatibility.
- Project quality gate for tests, lint, builds, and strict OpenSpec validation.
- Device inspection remains desirable for typography, wrapping, and theme color.

