# Design: Native Chat text selection

## Context

Jetpack Compose `Text` does not opt into selection by default. Compose provides
`SelectionContainer`, which delegates long-press selection, handles, and the
platform contextual toolbar to Android.

## Decision

Wrap each message's presented content in one `SelectionContainer`. Keep the
text-to-speech button outside that boundary so selection gestures cannot
interfere with message actions. Text across Markdown blocks and reasoning can be
selected; images and native rendered formula drawables remain non-text elements
inside the content flow.

Use the platform behavior instead of implementing a custom long-click clipboard
action. This enables partial selection and preserves Android accessibility and
contextual copy conventions.

Give the selection container exactly one vertical layout child. This preserves
the existing order and spacing of reasoning, attachments, and final text;
multiple direct children of the selection layout can otherwise be measured at
the same origin. Provide message-specific `TextSelectionColors`: user bubbles
use translucent `onPrimary` selection with `onPrimary` handles, while assistant
messages retain the theme primary selection colors.

Formula drawables use `LocalContentColor` rather than the global `onSurface`
color so formulas inherit the correct contrast inside reasoning surfaces,
message cards, and future nested containers.

## Validation

- Build and lint verify the Compose integration.
- Existing Chat and Markdown tests guard message rendering compatibility.
- Physical-device validation must confirm long press, selection handles,
  partial selection, and the system Copy action because those are platform UI
  interactions not executed by the repository's generic quality gate.
