# Change: Render mathematical notation in Chat

## Why

Model responses can contain LaTeX-delimited mathematics. The Chat currently
renders Markdown only, so delimiters and commands such as `$...$`, `$$...$$`,
`\frac`, and `\theta` are displayed literally instead of as readable formulas.

## What Changes

- Recognize inline and display mathematical notation in assistant Markdown.
- Render supported LaTeX locally with a native Android renderer that follows
  the active theme and does not require network access or a WebView.
- Preserve ordinary currency dollar signs and malformed or incomplete
  expressions as readable text, including while an answer is streaming.
- Cover delimiter parsing and fallback behavior with automated tests.

## Impact

- Affected spec: `local-llm-hub`
- Affected code: Chat Markdown parsing/rendering and Android dependencies
- Privacy/networking: unchanged; formula rendering remains on-device

