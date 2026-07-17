## ADDED Requirements

### Requirement: Bounded Chat Image Import

The app SHALL import external Chat images without buffering an unbounded source
in memory and SHALL enforce documented source and decoded-image limits before
persisting normalized media.

#### Scenario: Import an image within limits

- **GIVEN** the selected content is a decodable image within configured limits
- **WHEN** the user attaches it to a Chat prompt
- **THEN** the app processes it through bounded I/O
- **AND** stores only the normalized app-owned image used by Chat.

#### Scenario: Reject an oversized image

- **GIVEN** the selected content exceeds the configured source or decoded-image limit
- **WHEN** image import evaluates the content
- **THEN** the app rejects the attachment with a controlled error
- **AND** does not retain a partial app-owned image.

#### Scenario: Handle malformed or interrupted image input

- **GIVEN** the selected content is malformed, unavailable, or fails while being read
- **WHEN** image import runs
- **THEN** Chat remains usable and reports the import failure
- **AND** any temporary or partial output file is removed.

#### Scenario: Normalize EXIF-oriented image content

- **GIVEN** the selected image declares a rotated or mirrored EXIF orientation
- **WHEN** the app creates the normalized app-owned Chat image
- **THEN** it applies the declared orientation to the image pixels before persistence
- **AND** the Chat preview and local model receive the same visually upright image.
