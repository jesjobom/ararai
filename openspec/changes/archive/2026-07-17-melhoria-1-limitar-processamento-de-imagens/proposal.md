# Change: Limit and Stream Chat Image Imports

## Why

Chat image import currently reads the entire external source into memory before
decoding and resizing it. Large or misleading content-provider inputs can cause
excessive memory pressure and terminate the app before normalization occurs.

## What Changes

- Validate imported image metadata and enforce explicit source-size and decoded
  dimension limits.
- Process image content through bounded streams or an app-owned temporary file
  instead of retaining the complete source byte array.
- Reject unsupported, malformed, or oversized images with a controlled UI error.
- Clean up partial temporary and output files after failure or cancellation.
- Add tests around size limits, malformed input, cleanup, and successful
  normalization.

## Impact

- Touches image import and its Android content-provider boundary.
- Does not change persisted message format or model inference contracts.
- Should precede broader Chat screen refactoring so the safe import behavior is
  characterized first.
