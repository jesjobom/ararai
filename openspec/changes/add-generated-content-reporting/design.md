# Design: Privacy-bounded generated-content reporting

## Context

ArarAI owns conversations and their image/audio files locally. Assistant text is
generated on-device, audio playback is derived from that text through TTS, and
user audio may already have a local transcript. Reporting must therefore be an
explicit exception to the local-only data flow rather than a general chat export.

Anonymous Firebase Authentication gives an installation a pseudonymous identity,
while App Check with Play Integrity raises assurance that a request originated
from the registered ArarAI application. Firestore Security Rules can strictly
validate create-only documents but cannot provide robust per-identity or IP rate
limiting. That residual abuse risk is accepted for the expected reporting volume
so production reporting can operate within the Firebase Spark plan without a
billable Cloud Functions or Cloud Run endpoint.

The Firebase project currently provisioned for reporting is `ararai-report`.
Its default Firestore database uses `nam5`, a multi-region location in the United
States. The project remains on the no-cost Spark plan for development and
production while usage fits its Firestore quota. The report collection will
permit narrowly validated owner creation and owner-only point reads, while
denying list, update, and delete access to mobile/web clients.

## Decisions

### Attach reporting to an assistant response

Every completed, persisted assistant response SHALL expose `Report response`
through its message actions in normal Chat and Voice Chat. Responses that are
still streaming or contain no reportable assistant text are ineligible. Each
screen SHALL also expose a discoverable menu action targeting its latest eligible
response so policy reviewers and users need not discover a long-press gesture.

Opening the action creates a draft; it does not transmit data. The review UI
shows the exact response, requires a reason category, allows a bounded optional
comment, and explains that selected content will leave the device for human
review.

### Make disclosure minimal and user-controlled

The reported assistant response is the only mandatory conversation content. The
immediately preceding user message may be suggested as useful context, but the
user must be able to exclude it before submission. Additional context is an
explicit opt-in limited to two preceding user/assistant turn pairs. The review UI
lists every selected item and never uses an ambiguous `entire history` option.

Text and locally available audio transcripts may be selected as textual context.
Raw images, image bytes/thumbnails, audio recordings, TTS output, model files,
tool credentials, raw tool protocol, hidden instructions, reasoning, and the
remainder of the conversation SHALL NOT be uploaded. Reports may contain boolean
indicators that image or audio participated in the reported turn. Adding media
upload requires a later OpenSpec change covering consent, object storage, access,
retention, deletion, moderation safety, cost, and Data safety consequences.

Technical metadata is allowlisted and limited to schema version, app version,
locale, selected model/catalog identifier, runtime, report timestamp, and media/
transcript presence flags. Android ID, advertising ID, hardware serial, account
email, filenames/paths, IP address in stored report data, and other stable device
identifiers are excluded.

### Use a tightly constrained direct Firestore boundary

The Android app SHALL authenticate anonymously and create the reviewed report
directly in the default Firestore database through the Firebase Android SDK.
Firestore App Check enforcement SHALL reject requests without valid attestation.
Security Rules SHALL require authentication, bind `ownerUid` to
`request.auth.uid`, accept only an exact field allowlist with bounded types and
sizes, require the server-resolved creation timestamp to equal `request.time`,
and permit only document creation.

The collection SHALL deny list, update, and delete to every mobile/web client.
An authenticated owner may point-read only its own deterministic report document
to resolve a retried create; other reports remain unreadable. Administrative
review and deletion SHALL use Firebase Console or least-privilege administrative
credentials that are never shipped in the application. Firebase API keys and
project configuration embedded in the APK are public identifiers, not secrets.

The document ID SHALL incorporate the authenticated owner identity and the
client-generated stable random report ID. Retrying the same approved envelope
therefore cannot create a duplicate or overwrite an accepted report. A create
conflict is accepted as idempotent only after an owner-authorized point read
confirms the existing immutable document corresponds to the same report ID.

### Fix the storage location and disclose the international data flow

Accepted reports SHALL be stored in the existing default Firestore database in
`nam5`. This direct-client architecture introduces no Cloud Functions or Cloud
Run execution region.

The report review disclosure, privacy policy, and Google Play Data safety
declaration SHALL state that submitted report content and associated allowlisted
metadata are transferred to and hosted in the United States. This applies even
when the user and device are outside the United States. Administrative access,
logging, backup, retention, deletion, and incident documentation SHALL account
for that location and for any applicable international transfer obligations.

No second Firestore database or media bucket is introduced by this change. The
report collection appears when the first rules-authorized report is created; it
is not created manually as a prerequisite.

### Operate within Spark quotas without weakening rules

The Firebase project SHALL remain on Spark for production while usage fits the
documented no-cost Firestore quota. The application SHALL use the constrained
direct-write contract above; it SHALL NOT use open development rules or expose
general collection reads. If quota is exhausted, reports remain in the bounded
local queue until service resumes, without affecting local Chat.

Firestore managed TTL deletion requires billing and is therefore excluded while
the project remains on Spark. Reports SHALL store an `expiresAt` value 90 days
after creation, and the operational owner SHALL periodically delete expired or
resolved reports using an administrative process. Enabling managed TTL or adding
a server endpoint later requires an explicit architecture and billing decision.

### Use staged Play Integrity enforcement

App Check Play Integrity SHALL initially use a one-hour token lifetime, require
`PLAY_RECOGNIZED`, not require `LICENSED`, and not explicitly require a device
integrity level. Enforcement SHALL remain disabled until the Android SDK is
integrated and development builds use the App Check Debug Provider with
environment-restricted debug tokens.

After a Play internal-test build signed through Play App Signing passes genuine
attestation and end-to-end reporting, production SHALL additionally require the
`LICENSED` label. `PLAY_RECOGNIZED` remains required. No explicit Basic, Device,
or Strong Integrity verdict is required initially, because unnecessarily
excluding otherwise legitimate devices from the mandatory reporting path creates
a product and compliance risk. Tightening device integrity later requires
compatibility evidence and an OpenSpec revision.

The Firebase Android registration SHALL include the persistent debug signing
certificate for development and, before production, the upload/release and Google
Play App Signing certificate fingerprints. Firebase project identifiers and API
keys embedded in `google-services.json` are public configuration; debug tokens,
service-account credentials, and private signing material SHALL never be
committed or shipped.

### Queue failures without silently expanding retention

The app persists a bounded encrypted-or-application-private pending envelope
containing exactly the user-approved payload and its idempotency ID. WorkManager
retries only transient failures with network constraints and bounded backoff.
Authentication, attestation, validation, or permanent policy failures are shown
to the user and are not retried indefinitely.

The report UI distinguishes draft, pending, sent, and failed states. The Chat
and Voice Chat top bars expose one always-available report-center action rather
than separate new-report and pending-queue actions. The center presents the
latest reportable response and the pending queue as sibling tabs, opens the
queue when no response is reportable, and badges queued or permanently failed
items. Per-response actions remain focused directly on their selected response
while reusing the same report form. Users can cancel/delete an unsent report,
and successful submission removes the local
payload after retaining only the report identifier and completion time needed to
show a short-lived success indicator. The indicator appears after the final
pending report is delivered; remaining pending or failed items retain visual
precedence.
Queue count, payload size, attempt count, and local age are capped; expired items
are removed with an explicit status rather than uploaded unexpectedly later.

### Define operations and privacy before production

For the official Google Play distribution published by Jesjobom, Jairton Junior
is the initial operational owner and reviewer and contact.ararai@gmail.com is the
public privacy, support, deletion, abuse, and incident contact. Human review is
supported in Portuguese and English. Accepted reports SHALL have a
maximum retention of 90 days and MAY be deleted earlier after resolution or a
valid deletion request. Operations SHALL document review status, abuse response,
incident handling, data-subject contact, least-privilege access, and the manual
Spark-compatible expiry process before production release.

The public repository documents policy and non-sensitive procedures. Account
identifiers, credentials, recovery methods, incident evidence, and emergency
contacts remain private. Forks and third-party distributions SHALL use their own
backend, operator/contact, privacy policy, retention process, and store
declarations rather than treating the official Firebase project as shared
infrastructure.

The privacy policy and Google Play Data safety declaration SHALL describe the
user-initiated collection, purpose, encryption in transit, deletion/retention
path, authentication identifiers, United States hosting/international transfer,
and any service-provider processing. Debug/emulator data and production report
data SHALL not be mixed.

### Preserve offline core behavior

Reporting is an ancillary online compliance capability. Missing network,
Firebase unavailability, or report delivery failure SHALL NOT block local model
loading, prompt generation, conversation access, or media playback. No reporting
SDK may initialize in a way that sends conversation content or analytics before
the user explicitly submits a report.

## Validation

Automated tests SHALL cover response eligibility, exact context projection,
media exclusion, payload/schema/size bounds, explicit consent, local queue caps,
idempotency, retry classification, lifecycle recreation, Chat/Voice parity,
localization, accessibility, and regression of offline core Chat.

Security Rules and transport tests SHALL cover unauthenticated rejection, exact
schema/type/size bounds, owner binding, create-only access, denied list/update/
delete, owner-only point reads, idempotent retry races, and controlled failures.
Emulator-based tests do not prove Play Integrity; internal Play-distributed
builds SHALL validate genuine attestation, Firestore enforcement, offline/retry
behavior, and end-to-end document creation before production release.
