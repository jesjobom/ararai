# Scale Chat web-search evidence with configured context

## Why

Chat currently returns at most 1,800 characters of web evidence, including no more than 500 characters per excerpt, regardless of the model's configured context window. That fixed ceiling can discard useful evidence even when the user increases context capacity. The user prefers a predictable allowance based on configured capacity, accepting that a later context-exhaustion error may still occur.

## What changes

- Give each successful model-visible `web_search` call a complete serialized-response budget equal to 25% of the selected model's effective total context window, estimated at four characters per token.
- Keep the existing maximum of three validated sources, two focused excerpts per source, two calls per user turn, provider ordering, and safety checks. Select ranked evidence within the call's budget; do not retain Chat's fixed 1,800-character total or 500-character per-excerpt ceilings as additional quality limits.
- Keep widget `web_search_content` and `web_search_top_results` contracts and existing evidence limits unchanged, even though the content tool shares the provider chain with Chat.

## Scope

This is a Chat tool-output policy, not a promise that the remaining native context can accommodate every result. No new configuration control, exact tokenizer, automatic retry, or widget behavior change is proposed. Existing context-exhaustion messaging remains the recovery path when a model still runs out of space.
