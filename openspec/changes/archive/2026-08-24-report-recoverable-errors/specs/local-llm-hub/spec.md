## ADDED Requirements

### Requirement: Consent-Based Recoverable Error Reporting

The application SHALL let a user optionally submit a bounded diagnostic report
after an unexpected recoverable application error.

#### Scenario: Offer a report after recoverable failure

- **GIVEN** an application-owned operation fails unexpectedly and the app restores
  a usable state
- **WHEN** the failure reaches the diagnostic error boundary
- **THEN** the app shows a brief localized description
- **AND** offers `Send report` and `Not now`
- **AND** starts no transmission before explicit consent.

#### Scenario: Runtime reports a terminal generation event

- **GIVEN** the inference engine emits an unexpected terminal failure event
- **WHEN** Chat receives the event
- **THEN** Chat shows only a short localized generic failure message
- **AND** does not expose the technical runtime message
- **AND** forwards the typed failure and available cause to the diagnostic error
  boundary.

#### Scenario: Exclude expected and fatal conditions

- **GIVEN** an operation is cancelled, validation fails normally, connectivity is
  unavailable, permission is denied, or the process has a fatal uncaught error
- **WHEN** that condition occurs
- **THEN** the diagnostic report dialog is not required
- **AND** ordinary product recovery or Android process handling remains in effect.

### Requirement: Privacy-Bounded Diagnostic Envelope

Diagnostic error reports SHALL contain only allowlisted bounded technical data.

#### Scenario: Build an error report

- **WHEN** an eligible error is prepared for user review
- **THEN** it may include bounded error category, operation stage, sanitized
  exception summary, app/runtime/model configuration, Android API level, locale,
  context size, reasoning state, enabled tool names, and timestamp
- **AND** excludes prompts, responses, history, reasoning text, raw tool protocol,
  tool results, media, transcripts, paths, credentials, account information, and
  stable device identifiers.

### Requirement: One-Shot Diagnostic Delivery

The application SHALL make at most one non-persistent delivery attempt for each
user-approved diagnostic error report.

#### Scenario: Send an approved report

- **GIVEN** the user selects `Send report`
- **WHEN** the app performs the authenticated and App-Check-protected Firestore
  REST commit
- **THEN** it makes one bounded request with a finite timeout
- **AND** does not enqueue, persist, schedule, or resubmit the envelope
- **AND** shows whether the attempt succeeded or failed.

#### Scenario: Device is offline

- **GIVEN** no network is available
- **WHEN** the user approves submission
- **THEN** the attempt fails visibly
- **AND** the envelope is cleared from memory
- **AND** reconnecting later does not transmit it.

### Requirement: Private Firestore Diagnostic Storage

Accepted diagnostic reports SHALL be validated and stored through owner-bound,
create-only Firestore access.

#### Scenario: Accept an authentic bounded report

- **GIVEN** Firebase Authentication and App Check are valid
- **AND** the payload matches the exact bounded schema
- **WHEN** Firestore Security Rules accept it
- **THEN** Firestore creates one immutable private document
- **AND** uses a server-owned creation timestamp
- **AND** validates the 90-day expiry timestamp.

#### Scenario: Restrict mobile access

- **WHEN** a mobile client attempts to create a diagnostic error report
- **THEN** Security Rules allow only an authenticated owner-bound payload that
  matches the exact bounded schema
- **AND** deny reads, lists, updates, deletes, and overwrites.
