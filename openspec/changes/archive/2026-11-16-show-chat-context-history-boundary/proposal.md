# Show the bounded Chat history boundary

## Why

Chat already projects stored history into a context budget, but it chooses fitting messages individually. A short old message can be sent after a newer long message is omitted, so users cannot tell which part of a session the model actually received. As sessions grow, the interface also gives no indication that older messages remain visible and saved but are absent from the next request.

## What changes

- Select a contiguous suffix of eligible recent user turns, preserving user/assistant sequence and stopping at the first turn that does not fit the configured input budget. Do not backfill shorter, older turns.
- Preserve every canonical message in storage and the existing bounded/paged UI history. Recalculate the selected boundary when the effective context or current request changes; a larger context can make older turns eligible again.
- Show one understated divider in the Chat timeline at the inclusion boundary, labelled as older messages being outside the context for the next response. When that boundary has not been loaded into the visible page, show a truthful compact indication near the older-history control instead of placing the divider at a false position.

## Scope

Context selection remains shared by normal Chat and Voice Chat. The visual indicator is for the normal Chat message timeline. This does not delete or summarize history, change media replay rules, or promise that character estimates exactly match native token use or account for every tool/reasoning token.
