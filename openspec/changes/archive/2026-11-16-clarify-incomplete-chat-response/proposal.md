# Clarify incomplete Chat responses

## Why

An `Incomplete response` banner currently says only that the model produced no final answer. A user may reasonably suspect the configured context limit, but LiteRT-LM's completion callback does not expose a stop reason. The app should offer a more useful explanation without presenting a guess as a confirmed cause.

## What changes

- When an incomplete Chat turn has a cumulative native conversation token count near the configured total context capacity, show that context exhaustion is likely and suggest practical recovery.
- Otherwise, if a tool ran before the incomplete turn, explain that the tool ran but the model did not provide a final answer.
- In other cases explain that the runtime did not report a cause and suggest retrying or starting a new chat.
- Persist the inferred display reason with the assistant message so it survives reopening the conversation.
- When the native cumulative count is at or above capacity but the model emitted non-blank text, preserve that text and show a yellow possible-incompleteness warning rather than treating the output as confirmed complete or a confirmed failure.

## Scope

No change to model generation limits, projection, prompt content, Voice Chat behavior, or automatic retries.
