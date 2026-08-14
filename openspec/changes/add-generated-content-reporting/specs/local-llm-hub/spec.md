## ADDED Requirements

### Requirement: In-App Generated-Content Reporting

The application SHALL let a user report a completed assistant response from
normal Chat or Voice Chat without leaving the application.

#### Scenario: Report a specific assistant response

- **GIVEN** a persisted assistant response contains completed reportable text
- **WHEN** the user opens that response's actions
- **THEN** the app offers `Report response`
- **AND** opening it creates a local review draft without transmitting data.

#### Scenario: Discover reporting from the chat screen

- **GIVEN** the current conversation has an eligible assistant response
- **WHEN** the user opens the normal Chat or Voice Chat screen menu
- **THEN** the app offers an action to report the latest eligible response.

#### Scenario: Do not report incomplete output

- **GIVEN** an assistant response is still streaming or has no reportable text
- **WHEN** message actions are presented
- **THEN** that response cannot be submitted as a content report.

### Requirement: Explicit Report Review And Consent

The application SHALL show and bound every item of conversation content selected
for disclosure before a report is submitted.

#### Scenario: Review mandatory and optional content

- **WHEN** the report review is opened
- **THEN** the exact assistant response is included as mandatory content
- **AND** the user selects a required reason category
- **AND** an optional bounded comment is available
- **AND** preceding textual context is individually reviewable and removable
- **AND** no transmission occurs until the user explicitly submits.

#### Scenario: Add bounded context

- **GIVEN** preceding conversation turns exist
- **WHEN** the user chooses to add context
- **THEN** at most two preceding user/assistant turn pairs can be selected
- **AND** an available local audio transcript is treated only as optional text
- **AND** the entire conversation cannot be selected through one implicit action.

### Requirement: Report Media And Sensitive-Data Exclusion

Generated-content reports SHALL exclude raw media and nonessential private or
internal application data.

#### Scenario: Report a multimodal turn

- **GIVEN** the reported turn used an image, user audio, transcript, or TTS
- **WHEN** the report payload is constructed
- **THEN** no image, thumbnail, audio recording, or TTS output is included
- **AND** no file name or path is included
- **AND** optional boolean media/transcript presence indicators may be included.

#### Scenario: Exclude internal and identifying data

- **WHEN** any report payload is constructed
- **THEN** it excludes unselected history, hidden instructions, raw tool protocol,
  reasoning, model files, credentials, Android ID, advertising ID, hardware
  serial, and account email.

### Requirement: Authenticated Direct Firestore Report Ingestion

The application SHALL submit reports directly to Firestore through a narrowly
constrained create-only client boundary protected by anonymous authentication,
App Check enforcement, and strict Security Rules.

#### Scenario: Accept an authentic valid report

- **GIVEN** the request has valid Firebase anonymous authentication and App Check
  attestation for the expected Play-distributed application
- **AND** its payload satisfies the allowlisted schema and size limits
- **WHEN** the client creates the owner-scoped deterministic document ID
- **THEN** Security Rules validate owner binding, exact fields, types, sizes, and
  server-resolved creation time
- **AND** create one immutable private Firestore report
- **AND** returns a non-sensitive receipt.

#### Scenario: Reject an untrusted client

- **GIVEN** authentication or App Check validation is absent, invalid, or for an
  unexpected application identity
- **WHEN** a report creation reaches Firestore
- **THEN** the request is rejected without storing report content.

#### Scenario: Repeat an accepted request

- **GIVEN** a report with the same authenticated owner and idempotency ID exists
- **WHEN** delivery is retried
- **THEN** the app may point-read only that owner's deterministic document to
  verify the immutable accepted report
- **AND** no duplicate report is created.

#### Scenario: Restrict direct mobile database access

- **WHEN** a mobile client attempts to list, update, or delete reports, read a
  different owner's report, or create a report outside the bounded schema
- **THEN** access is denied
- **AND** only authenticated bounded creation and owner-only point reads are
  permitted.

#### Scenario: Store an accepted report in the configured location

- **GIVEN** Firestore accepts a rules-authorized report
- **WHEN** the Android SDK persists it
- **THEN** it writes to the default Firestore database in the `nam5` United
  States multi-region
- **AND** no client-visible collection creation or media bucket is required.

### Requirement: Spark-Compatible Reporting Activation

The application SHALL provide production reporting within Firebase Spark quotas
without requiring a Cloud Functions or Cloud Run endpoint.

#### Scenario: Operate while the project remains on Spark

- **GIVEN** the reporting Firebase project remains on the Spark plan
- **WHEN** reporting is developed, tested, and released
- **THEN** the app uses constrained direct Firestore creation
- **AND** Security Rules and App Check enforcement protect the collection
- **AND** quota exhaustion retains bounded pending reports without affecting
  local Chat.

#### Scenario: Retain reports for 90 days on Spark

- **GIVEN** managed Firestore TTL deletion requires billing
- **WHEN** a report is accepted on Spark
- **THEN** it stores an `expiresAt` value 90 days after creation
- **AND** the operational owner deletes expired or resolved reports through a
  documented administrative process
- **AND** the application does not claim automatic TTL deletion.

#### Scenario: Configure initial App Check registration

- **WHEN** the Android app is registered with the Play Integrity provider
- **THEN** its token lifetime is one hour
- **AND** `PLAY_RECOGNIZED` is required
- **AND** `LICENSED` is not required before internal Play validation
- **AND** no explicit device-integrity verdict is required
- **AND** enforcement remains disabled until SDK and debug-provider validation.

#### Scenario: Harden production licensing after internal testing

- **GIVEN** a Play internal-test build signed through Play App Signing has passed
  genuine attestation and end-to-end report delivery
- **WHEN** production App Check policy is enabled
- **THEN** both `PLAY_RECOGNIZED` and `LICENSED` are required
- **AND** sideloaded copies cannot write to the protected production Firestore
  collection.

### Requirement: United States Report-Hosting Disclosure

The application SHALL disclose the configured international destination before
the user submits report content.

#### Scenario: Review a report outside or inside the United States

- **GIVEN** a user reviews content selected for a report
- **WHEN** the submission disclosure is presented
- **THEN** it states that submitted report content and allowlisted metadata are
  transferred to and hosted in the United States
- **AND** submission still requires the user's explicit action.

### Requirement: Bounded Report Delivery Recovery

The application SHALL recover from transient delivery failures without blocking
or silently retaining reports indefinitely.

#### Scenario: Queue a transient failure

- **GIVEN** the user approved a report
- **AND** delivery fails for a transient network or service reason
- **WHEN** the failure is classified
- **THEN** the exact approved bounded payload is stored in the private local queue
- **AND** retry uses network constraints, bounded backoff, and the same
  idempotency ID
- **AND** the user sees a pending state.

#### Scenario: Handle permanent failure

- **GIVEN** authentication, attestation, payload validation, or policy rejection
  is permanent
- **WHEN** delivery fails
- **THEN** the app shows a non-sensitive failed state
- **AND** does not retry indefinitely.

#### Scenario: Remove an unsent report

- **GIVEN** a report remains pending locally
- **WHEN** the user deletes it or its documented local lifetime expires
- **THEN** its pending payload is removed
- **AND** it is not transmitted later.

#### Scenario: Open the consolidated report center

- **GIVEN** the user is in Chat or Voice Chat
- **WHEN** the user selects the top-bar report action
- **THEN** one report center presents new-report and pending-report tabs
- **AND** its pending tab remains accessible when the queue is empty
- **AND** queued reports are indicated on the top-bar action
- **AND** a permanent failure is visually distinguished from an ordinary retry
- **AND** successful delivery records no report content and shows a temporary
  success indicator when no pending or failed item takes precedence
- **AND** a per-response report action still opens the focused form for that
  selected response without the report-center tabs.

### Requirement: Reporting Does Not Replace Local Core Behavior

The online reporting capability SHALL remain isolated from ArarAI's local core
chat behavior.

#### Scenario: Reporting service unavailable

- **GIVEN** Firebase Auth, App Check, or Firestore is unavailable
- **WHEN** the user uses an installed local model
- **THEN** model loading, local generation, conversation access, and local media
  playback continue independently.

#### Scenario: No report submission

- **WHEN** the user does not explicitly submit a report
- **THEN** reporting components transmit no conversation or media content.
