## ADDED Requirements

### Requirement: Application startup

The application SHALL present its first Compose frame without synchronously repeating successful integrity hashing of unchanged app-owned model artifacts or reconciling persisted Chat media on the UI thread. A cached integrity result SHALL be invalidated when the expected digest, file size, or file modification metadata changes.

#### Scenario: Relaunch with an unchanged verified model

- **WHEN** the application relaunches with a model artifact whose verification metadata still matches
- **THEN** startup SHALL reuse the successful verification
- **AND** SHALL NOT hash the complete artifact before the first frame

### Requirement: Shared native runtime ownership

The application SHALL prevent overlapping native model load transitions in its shared runtime, including when a screen-navigation cancellation occurs while a native load is not cooperatively cancellable.
Voice Chat SHALL initialize its required workload profile directly rather than constructing an intermediate text-only native engine that is immediately replaced.

#### Scenario: Voice Chat is repeatedly entered and left during loading

- **WHEN** the user repeatedly enters and leaves Voice Chat before model preparation completes
- **THEN** stale attempts SHALL NOT mark Voice Chat ready
- **AND** the shared runtime SHALL NOT create overlapping native model instances

### Requirement: Home branding

The home wordmark SHALL be rendered on a black surface in the light theme and a transparent surface in the dark theme so its light lettering remains legible without introducing a background color that conflicts with the application surface.

#### Scenario: Home is shown in either theme

- **WHEN** the user opens Home with the light theme
- **THEN** the wordmark SHALL appear on a black surface
- **WHEN** the user opens Home with the dark theme
- **THEN** the wordmark surface SHALL be transparent and blend into the application background
