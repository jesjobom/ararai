## ADDED Requirements

### Requirement: Source-safe fallback download resume

The app SHALL NOT combine unverified partial bytes from one model download URL
with bytes from a different URL. A resumed transfer that fails integrity SHALL
receive at most one clean retry from byte zero on the same URL before the app
advances to another configured source.

#### Scenario: Change to a fallback source

- **GIVEN** a partial model was obtained from one configured URL
- **WHEN** the downloader advances to a different fallback URL
- **THEN** it does not append the fallback response to unverified prior-source bytes.

#### Scenario: Retry incompatible resumed content cleanly

- **GIVEN** a server accepted a resume offset
- **WHEN** the completed temporary artifact fails configured integrity validation
- **THEN** the app deletes the incompatible temporary content
- **AND** retries that URL once from byte zero.

#### Scenario: Bound clean retries

- **WHEN** the clean retry also fails
- **THEN** the app advances or reports terminal failure without an unbounded loop.
