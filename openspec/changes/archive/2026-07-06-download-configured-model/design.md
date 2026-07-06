# Design Notes

## Scope Shape

This change should close the current gap between model resolution and a usable
configured model file. It should not start native inference work.

The implementation can use WorkManager if it stays straightforward. If
WorkManager adds too much ceremony for this slice, keep the download logic
behind an interface and use a minimal Android implementation that can later move
behind WorkManager without changing the UI/ViewModel contract.

## File Safety

The final configured model path should only point to a file that passed
validation. The downloader should write to a sibling temporary file, for
example:

- final: `models/SmolLM2-135M-Instruct-Q4_K_M.gguf`
- temporary: `models/SmolLM2-135M-Instruct-Q4_K_M.gguf.part`

After download completes:

1. validate byte size when configured
2. validate SHA-256
3. delete any stale final file if the platform requires it
4. atomically rename the temporary file to the final file path
5. rerun model resolution and enable chat only when the model is available

If validation fails, the final path must remain absent or keep the previous
valid file. A failed temporary file may be deleted immediately.

## State Model

The model flow should keep explicit state names that are easy to test and render
in the debug UI:

- `missing`
- `invalid`
- `downloading`
- `available`
- `failed`

Progress can be approximate or absent in this change. A simple byte counter is
nice, but not required for completion.

## Testing Strategy

Keep most tests JVM-local by injecting a fake byte source and temporary
directory. Android-specific tests are not required for this slice unless the
chosen implementation depends heavily on WorkManager behavior.
