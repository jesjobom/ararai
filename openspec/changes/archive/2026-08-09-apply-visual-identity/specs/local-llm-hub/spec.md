# local-llm-hub Specification

## ADDED Requirements

### Requirement: Branded Application Entry

The application SHALL use repository-owned ArarAI visual assets for its
launcher identity and launch experience without depending on the external
artifact directory at build time or runtime.

#### Scenario: Display launcher icon

- **WHEN** a supported Android launcher displays ArarAI
- **THEN** the application uses an adaptive launcher icon where supported
- **AND** its foreground is derived from the reviewed compact color symbol
- **AND** its background is `#010923`
- **AND** the essential symbol remains inside Android adaptive-icon safe bounds
  under representative launcher masks
- **AND** supported legacy launchers receive a compatible fallback icon.

#### Scenario: Cold launch the application

- **WHEN** the user cold-launches ArarAI on a supported Android version
- **THEN** the launch window uses background `#010923`
- **AND** presents the compact ArarAI symbol without stretching or clipping its
  essential content
- **AND** transitions to the Compose application without an intermediate blank
  or light frame.

### Requirement: Branded Home Header

The Home screen SHALL present the ArarAI wordmark in a stable branded region
while preserving accessibility and existing application information.

#### Scenario: View Home in either appearance theme

- **GIVEN** the application appearance is System, Light, or Dark
- **WHEN** the user views Home
- **THEN** the transparent ArarAI wordmark is displayed without distortion on
  a `#010923` surface
- **AND** the app name remains available to accessibility services as text
- **AND** the current application version information remains available
- **AND** the header does not rely on the surrounding Material surface color
  for wordmark contrast.

#### Scenario: View Home with constrained presentation space

- **WHEN** Home is rendered on a supported portrait width or with enlarged font
  or display scale
- **THEN** the wordmark preserves its aspect ratio
- **AND** its essential content is not cropped
- **AND** it does not prevent access to Home actions below it.

### Requirement: Branded Project README

The project README SHALL identify ArarAI with a repository-owned banner that is
legible independently of the documentation viewer's color theme.

#### Scenario: Render README

- **WHEN** a documentation viewer renders `README.md` with repository images
- **THEN** an optimized opaque ArarAI banner appears near the document title
- **AND** the image uses a repository-relative reference
- **AND** meaningful alternative text identifies the ArarAI project
- **AND** the surrounding README content remains usable when images are not
  loaded.
