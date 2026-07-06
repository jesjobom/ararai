# Design Notes

## Progress Shape

The downloader should emit progress as bytes copied and optional total bytes.
The configured model already has `expectedBytes`, so the first UI can render
either:

- percent when total is known
- raw downloaded bytes when total is unknown

No ETA is needed in this change.

## State Flow

Progress should move through the existing startup state rather than adding a
separate UI channel:

1. downloader copies bytes and invokes a progress callback
2. startup controller converts that callback to `Downloading(bytes, total)`
3. chat ViewModel formats the status text
4. Compose continues rendering the existing status line

This keeps the change narrow and testable.

## Testing Strategy

Use fake byte sources and JVM unit tests. Avoid network-dependent tests.
