# Change: Render Basic Markdown in Chat Messages

## Why

Local models frequently return structured text using Markdown. Chat currently
shows those markers literally, which makes headings, emphasis, lists, and code
harder to scan than the model intended.

## What Changes

- Render a documented basic Markdown subset in text messages and visible
  reasoning content.
- Support headings, bold, italic, unordered and ordered lists, block quotes,
  inline code, fenced code blocks, links, and horizontal rules.
- Preserve plain text and unsupported Markdown syntax without failing message
  rendering.
- Keep stored message content unchanged; formatting is a presentation concern.
- Add focused parser tests without introducing a new runtime dependency.

## Impact

- Touches Chat message presentation and adds a small repository-owned Markdown
  parser/renderer with focused unit tests.
- Does not change prompts, generated content, session persistence, or exported
  message text.
